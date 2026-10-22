package app.selfup.gym

import app.selfup.core.AutoCheck
import app.selfup.core.GymImport
import app.selfup.core.GymScore
import app.selfup.core.GymSet
import app.selfup.core.GymVerdict
import app.selfup.data.CompletionResult
import app.selfup.data.GymSessionScoreEntity
import app.selfup.data.ImportResult
import app.selfup.data.LifeRepository
import app.selfup.data.MetaDao
import app.selfup.data.MetaEntity
import app.selfup.data.RecordsRepository
import app.selfup.data.TrackingDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Where the link with Gym stands, which decides what the Records screen shows. */
sealed interface GymStatus {
    /** Gym isn't on this phone. */
    data object NotInstalled : GymStatus

    /** Gym is installed but too old to share; it has to be updated. */
    data object NeedsUpdate : GymStatus

    /** Gym can share, and is waiting for the user to allow it once. */
    data class NeedsPermission(val permission: String) : GymStatus

    /** Connected. [lastSync] is null until the first pull lands; [failed] when the last pull couldn't read Gym. */
    data class Connected(val lastSync: Instant?, val failed: Boolean = false) : GymStatus
}

/** A gym quest the day's best session completed, re-scored, or left open. */
data class GymCheck(val title: String, val verdict: GymVerdict, val result: CompletionResult)

/**
 * Keeps Records in step with the Gym app, with nothing for the user to press. While linked,
 * it also scores each workout (see [GymScore]) and completes the day's gym quest with XP
 * for how the best session that day measured up.
 *
 * [sync] runs when SelfUp comes to the foreground and every 15 minutes in the background.
 * Each run reads Gym's whole shared log and reconciles it, so an edit or deletion in Gym
 * flows through as surely as a new workout, and running it twice changes nothing.
 */
class GymSync(
    private val bridge: GymBridge,
    private val records: RecordsRepository,
    private val meta: MetaDao,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val life: LifeRepository,
    private val tracking: TrackingDao,
) {
    private val _status = MutableStateFlow<GymStatus>(GymStatus.NotInstalled)
    val status: StateFlow<GymStatus> = _status.asStateFlow()

    private val _results = MutableSharedFlow<ImportResult>(extraBufferCapacity = 4)

    /** Runs that brought something new, for the celebration and level-up overlay. */
    val results: SharedFlow<ImportResult> = _results.asSharedFlow()

    private val _checks = MutableSharedFlow<GymCheck>(extraBufferCapacity = 4)

    /** Gym quests the sync completed or re-scored, for the snackbar. */
    val checks: SharedFlow<GymCheck> = _checks.asSharedFlow()

    private val mutex = Mutex()

    /** Checks the link and, when connected, pulls everything new. Safe to call at any time. */
    suspend fun sync(): Unit = mutex.withLock {
        val install = bridge.find()
        val status = when {
            install == null -> GymStatus.NotInstalled
            !bridge.canShare(install) -> GymStatus.NeedsUpdate
            !bridge.isGranted(install) -> GymStatus.NeedsPermission(install.permission)
            else -> pull(install)
        }
        _status.value = status
        // Without the link a gym quest goes back to a tap; with it, only a logged workout completes it.
        life.setGymLinked(status is GymStatus.Connected)
    }

    private suspend fun pull(install: GymBridge.Install): GymStatus {
        // Linked before scoring, so the very first pull already checks the gym quest.
        life.setGymLinked(true)
        val lastSync = meta.get(KEY_LAST_SYNC)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        val earnsFrom = meta.get(KEY_CONNECTED_AT)?.toLongOrNull()?.let(Instant::ofEpochMilli)
            ?: clock.instant().also { meta.put(MetaEntity(KEY_CONNECTED_AT, it.toEpochMilli().toString())) }
        val snapshot = try {
            bridge.read(install)
        } catch (e: Exception) {
            // Revoked between the check and the read, or Gym crashed. Try again next time.
            return GymStatus.Connected(lastSync, failed = true)
        }
        val imported = GymImport.records(snapshot.sets, snapshot.cardio, snapshot.weighIns, zone())
        val result = records.syncImported(GymImport.SOURCE_PREFIX, imported, earnsFrom)
        val now = clock.instant()
        meta.put(MetaEntity(KEY_LAST_SYNC, now.toEpochMilli().toString()))
        if (result.added + result.updated + result.removed > 0) _results.tryEmit(result)
        score(snapshot.sets, snapshot.weighIns.maxByOrNull { it.day }?.weightKg, earnsFrom)
        return GymStatus.Connected(now)
    }

    /**
     * Scores every session into gym_session_score, then hands today's and yesterday's best
     * session to the gym quest. Sessions from before linking are history only.
     */
    private suspend fun score(sets: List<GymSet>, bodyWeightKg: Double?, earnsFrom: Instant) {
        val sessions = GymScore.sessions(sets, bodyWeightKg ?: GymScore.DEFAULT_BODY_WEIGHT_KG)
        if (sessions.isEmpty()) return
        val boundary = life.dayBoundary
        val now = clock.millis()
        val verdicts = sessions.map { GymScore.verdict(it, sessions) }
        tracking.putGymScores(
            verdicts.map { v ->
                GymSessionScoreEntity(
                    sessionId = v.session.sessionId,
                    epochDay = boundary.questDay(v.session.start).toEpochDay(),
                    startMillis = v.session.start.toEpochMilli(),
                    endMillis = v.session.end.toEpochMilli(),
                    sets = v.session.sets,
                    volumeKg = v.session.volumeKg,
                    comparedTo = v.comparedTo,
                    setsPart = v.setsPart,
                    volumePart = v.volumePart,
                    strengthPart = v.strengthPart,
                    performance = v.performance,
                    scoredAtMillis = now,
                )
            },
        )
        val today = life.today()
        listOf(today.minusDays(1), today).forEach { day -> checkDay(day, verdicts, earnsFrom) }
    }

    private suspend fun checkDay(day: LocalDate, verdicts: List<GymVerdict>, earnsFrom: Instant) {
        val best = verdicts
            .filter { !it.session.start.isBefore(earnsFrom) && life.dayBoundary.questDay(it.session.start) == day }
            .maxByOrNull { it.performance } ?: return
        val detail = String.format(
            Locale.ROOT,
            "session=%d sets=%d volume=%.0f vs=%s setsPart=%.2f volumePart=%.2f strengthPart=%.2f",
            best.session.sessionId, best.session.sets, best.session.volumeKg, best.comparedTo?.toString() ?: "-",
            best.setsPart, best.volumePart, best.strengthPart,
        )
        life.dailiesDueOn(day).filter { life.checkOf(it) == AutoCheck.GYM }.forEach { quest ->
            val result = life.verify(quest.id, day, AutoCheck.GYM, best.performance, detail)
            if (result is CompletionResult.Completed || result is CompletionResult.Rescored) {
                _checks.tryEmit(GymCheck(quest.title, best, result))
            }
        }
    }

    private companion object {
        const val KEY_CONNECTED_AT = "gym_connected_at"
        const val KEY_LAST_SYNC = "gym_last_sync"
    }
}
