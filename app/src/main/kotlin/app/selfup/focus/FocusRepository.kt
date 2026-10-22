package app.selfup.focus

import app.selfup.core.DayBoundary
import app.selfup.core.Quest
import app.selfup.core.ScreenTime
import app.selfup.core.Stat
import app.selfup.data.CompletionResult
import app.selfup.data.LifeRepository
import app.selfup.core.ReminderPlanner
import app.selfup.data.AppUsageDayEntity
import app.selfup.data.FocusEventEntity
import app.selfup.data.FocusEventKind
import app.selfup.data.MetaDao
import app.selfup.data.PhoneDayEntity
import app.selfup.data.TrackingDao
import app.selfup.data.MetaEntity
import app.selfup.data.XpReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Today's entertainment use, for the Home window. */
data class ScreenToday(
    val hasAccess: Boolean,
    val blockerOn: Boolean = false,
    val minutes: Int = 0,
    val budget: Int = ScreenTime.DAILY_BUDGET_MINUTES,
    /** Minutes per app, biggest first. */
    val apps: List<Pair<String, Int>> = emptyList(),
) {
    val spent: Boolean get() = minutes >= budget
}

/** What settling yesterday's screen time did. */
data class FocusReport(
    val verified: List<Quest> = emptyList(),
    val bonusXp: Int = 0,
) {
    val isEmpty: Boolean get() = verified.isEmpty() && bonusXp == 0
}

/**
 * Screen time as part of the game: yesterday's "Phone off by …" quests are checked against
 * the usage log, a day within the entertainment budget earns Life XP, and past the budget
 * the blocker locks entertainment apps for the rest of the quest day.
 */
class FocusRepository(
    private val source: UsageSource,
    private val life: LifeRepository,
    private val meta: MetaDao,
    private val tracking: TrackingDao,
    private val clock: Clock = life.clock,
    private val boundary: DayBoundary = life.dayBoundary,
    /** Whether the blocker's accessibility service is switched on. */
    private val blockerEnabled: () -> Boolean = { false },
) {
    @Volatile private var cachedApps: Set<String>? = null

    private fun apps(): Set<String> = cachedApps ?: source.entertainment().also { cachedApps = it }

    /**
     * Settles yesterday once. Must run before [LifeRepository.closeFinishedDays], so a
     * verified quest counts as done rather than missed. Without usage access, or with no
     * usage logged at all (access only just granted, or the log expired), it does nothing.
     */
    suspend fun settle(): FocusReport = withContext(Dispatchers.IO) {
        if (!source.hasAccess()) return@withContext FocusReport()
        val today = life.today()
        val yesterday = today.minusDays(1)
        val last = meta.get(SETTLED_KEY)?.toLongOrNull()
        if (last != null && last >= yesterday.toEpochDay()) return@withContext FocusReport()
        meta.put(MetaEntity(SETTLED_KEY, yesterday.toEpochDay().toString()))

        val start = boundary.startOf(yesterday)
        val end = boundary.startOf(today)
        val events = source.events(start, end)
        if (events.isEmpty()) return@withContext FocusReport()

        val day = yesterday.toEpochDay()
        val summary = ScreenTime.summarize(
            events,
            start,
            end,
            lateFrom = ReminderPlanner.instantOn(yesterday, ScreenTime.LATE_FROM, boundary),
            entertainment = apps(),
        )
        tracking.putAppUsage(summary.apps.map { (pkg, use) -> AppUsageDayEntity(day, pkg, use.foregroundMillis, use.opens, use.entertainment) })
        tracking.putPhoneDay(
            PhoneDayEntity(
                epochDay = day,
                firstUseMillis = summary.firstUse?.toEpochMilli(),
                lastUseMillis = summary.lastUse?.toEpochMilli(),
                totalMillis = summary.totalMillis,
                entertainmentMillis = summary.entertainmentMillis,
                opens = summary.opens,
                lateOpens = summary.lateOpens,
            ),
        )
        val used = (summary.entertainmentMillis / 60_000).toInt()

        val verified = life.dailiesDueOn(yesterday).filter { quest ->
            val cutoff = ScreenTime.cutoff(quest.title) ?: return@filter false
            ScreenTime.stayedOff(events, yesterday, cutoff, boundary) &&
                life.completeVerified(quest.id, yesterday) is CompletionResult.Completed
        }
        verified.forEach { log(FocusEventKind.VERIFIED, day, questId = it.id, minutesUsed = used) }
        val bonus = if (used < ScreenTime.DAILY_BUDGET_MINUTES) ScreenTime.UNDER_BUDGET_XP else 0
        if (bonus > 0) {
            life.grantBonus(Stat.LIFE, bonus, yesterday, XpReason.FOCUS)
            log(FocusEventKind.BONUS, day, minutesUsed = used)
        }
        FocusReport(verified, bonus)
    }

    /** Entertainment minutes so far today, per app. */
    fun usedToday(): Map<String, Long> {
        val now = clock.instant()
        val start = boundary.startOf(boundary.questDay(now))
        return ScreenTime.foregroundMillis(source.events(start, now), start, now, apps())
    }

    /** Whether opening [pkg] right now should hit the lock screen. Each lock is logged. */
    suspend fun shouldBlock(pkg: String): Boolean {
        if (pkg !in apps() || !source.hasAccess()) return false
        val used = withContext(Dispatchers.IO) { ScreenTime.minutes(usedToday()) }
        val block = ScreenTime.shouldBlock(used, clock.instant(), graceUntil())
        if (block) log(FocusEventKind.LOCKED, today().toEpochDay(), pkg = pkg, minutesUsed = used)
        return block
    }

    /** True when today's one grace unlock is still available. */
    suspend fun graceAvailable(): Boolean = graceDay() != today().toEpochDay()

    /** Starts today's grace unlock. Returns false when it was already used. */
    suspend fun useGrace(): Boolean {
        if (!graceAvailable()) return false
        val until = clock.instant().plus(Duration.ofMinutes(ScreenTime.GRACE_MINUTES.toLong()))
        meta.put(MetaEntity(GRACE_KEY, "${today().toEpochDay()}:${until.toEpochMilli()}"))
        log(FocusEventKind.GRACE, today().toEpochDay())
        return true
    }

    /** Re-reads today's use every minute while collected. */
    fun observeToday(): Flow<ScreenToday> = flow {
        while (true) {
            emit(readToday())
            delay(REFRESH)
        }
    }.flowOn(Dispatchers.IO)

    fun readToday(): ScreenToday {
        if (!source.hasAccess()) return ScreenToday(hasAccess = false, blockerOn = blockerEnabled())
        val ms = usedToday()
        val names = ScreenTime.scrollApps
        return ScreenToday(
            hasAccess = true,
            blockerOn = blockerEnabled(),
            minutes = ScreenTime.minutes(ms),
            apps = ms.entries.sortedByDescending { it.value }
                .map { (pkg, millis) -> (names[pkg] ?: pkg.substringAfterLast('.')) to (millis / 60_000).toInt() }
                .filter { it.second > 0 },
        )
    }

    private suspend fun log(kind: String, epochDay: Long, pkg: String? = null, questId: Long? = null, minutesUsed: Int? = null) {
        tracking.insertFocusEvent(FocusEventEntity(atMillis = clock.millis(), epochDay = epochDay, kind = kind, pkg = pkg, questId = questId, minutesUsed = minutesUsed))
    }

    private fun today(): LocalDate = boundary.questDay(clock.instant())

    private suspend fun graceDay(): Long? = meta.get(GRACE_KEY)?.substringBefore(':')?.toLongOrNull()

    private suspend fun graceUntil(): Instant? {
        val value = meta.get(GRACE_KEY) ?: return null
        if (graceDay() != today().toEpochDay()) return null
        return value.substringAfter(':').toLongOrNull()?.let(Instant::ofEpochMilli)
    }

    private companion object {
        const val SETTLED_KEY = "focus_settled_day"
        const val GRACE_KEY = "focus_grace"
        const val REFRESH = 60_000L
    }
}
