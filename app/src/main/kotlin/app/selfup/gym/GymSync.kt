package app.selfup.gym

import app.selfup.core.GymImport
import app.selfup.data.ImportResult
import app.selfup.data.MetaDao
import app.selfup.data.MetaEntity
import app.selfup.data.RecordsRepository
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
import java.time.ZoneId

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

/**
 * Keeps Records in step with the Gym app, with nothing for the user to press.
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
) {
    private val _status = MutableStateFlow<GymStatus>(GymStatus.NotInstalled)
    val status: StateFlow<GymStatus> = _status.asStateFlow()

    private val _results = MutableSharedFlow<ImportResult>(extraBufferCapacity = 4)

    /** Runs that brought something new, for the celebration and level-up overlay. */
    val results: SharedFlow<ImportResult> = _results.asSharedFlow()

    private val mutex = Mutex()

    /** Checks the link and, when connected, pulls everything new. Safe to call at any time. */
    suspend fun sync(): Unit = mutex.withLock {
        val install = bridge.find()
        _status.value = when {
            install == null -> GymStatus.NotInstalled
            !bridge.canShare(install) -> GymStatus.NeedsUpdate
            !bridge.isGranted(install) -> GymStatus.NeedsPermission(install.permission)
            else -> pull(install)
        }
    }

    private suspend fun pull(install: GymBridge.Install): GymStatus {
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
        return GymStatus.Connected(now)
    }

    private companion object {
        const val KEY_CONNECTED_AT = "gym_connected_at"
        const val KEY_LAST_SYNC = "gym_last_sync"
    }
}
