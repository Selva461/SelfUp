package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.AutoCheck
import app.selfup.core.CompletionInput
import app.selfup.core.DailyReset
import app.selfup.core.Drill
import app.selfup.core.DayOutcome
import app.selfup.core.DayBoundary
import app.selfup.core.DayOutcomeRow
import app.selfup.core.DefaultQuests
import app.selfup.core.Enforcer
import app.selfup.core.Performance
import app.selfup.core.Quest
import app.selfup.core.QuestChecks
import app.selfup.core.Schedule
import app.selfup.core.Spending
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.StatProgress
import app.selfup.core.StreakState
import app.selfup.core.Streaks
import app.selfup.core.TestDay
import app.selfup.core.XpRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** A quest as it appears on a given quest day. */
data class DayQuest(
    val quest: Quest,
    val completion: CompletionEntity?,
    /** How the System checks it, or null when a tap completes it. */
    val check: AutoCheck? = null,
) {
    val isDone: Boolean get() = completion != null

    /** XP the completion earned, scored or full. */
    val earnedXp: Int?
        get() = completion?.let { c ->
            c.performance?.let { XpRules.scoredDeltas(quest, it).values.sum() } ?: XpRules.completionXp(quest.difficulty)
        }
}

/** What closing out finished quest days did, for telling the user. */
data class DayCloseReport(
    val missed: List<Quest> = emptyList(),
    val xpLost: Int = 0,
    /** Days whose misses a streak shield absorbed, so they cost nothing. */
    val shieldsUsed: Int = 0,
    /** The streak that broke, when one did. */
    val streakLost: Int = 0,
    /** Rest days whose misses were excused. */
    val restedDays: Int = 0,
) {
    val isEmpty: Boolean get() = missed.isEmpty() && shieldsUsed == 0 && streakLost == 0 && restedDays == 0
}

/** What the reminder scheduler needs to know about one quest. */
data class ReminderInput(
    val quest: Quest,
    val doneToday: Boolean,
    /** A one-time quest that is complete for good. */
    val finished: Boolean,
)

sealed interface CompletionResult {
    /** [performance] is set when the System scored it rather than granting the full reward. */
    data class Completed(val changes: List<StatChange>, val performance: Double? = null) : CompletionResult

    /** A checked quest's verdict moved (a gym session that grew); [changes] is the difference. */
    data class Rescored(val changes: List<StatChange>, val performance: Double) : CompletionResult

    /** The data showed it wasn't done well enough, so it stays open (and is penalized if the day ends so). */
    data class Failed(val performance: Double) : CompletionResult

    /** The System checks this quest itself; a tap can't complete it. */
    data class Automatic(val check: AutoCheck) : CompletionResult

    /** The day was already settled before the data arrived. */
    data object Closed : CompletionResult

    data object AlreadyDone : CompletionResult
    data object NotDue : CompletionResult
    data object NotFound : CompletionResult
}

/**
 * The single entry point for reading and changing game state. Every write that touches
 * XP runs in one transaction, so a crash can never leave a completion without its XP.
 */
class LifeRepository(
    private val db: SelfUpDatabase,
    val clock: Clock = Clock.systemDefaultZone(),
    val dayBoundary: DayBoundary = DayBoundary(zone = clock.zone),
) {
    private val quests = db.questDao()
    private val completions = db.completionDao()
    private val xp = db.xpDao()
    private val meta = db.metaDao()
    private val tracking = db.trackingDao()
    private val hunter = db.hunterDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    /** The current quest day, re-emitted when the day rolls over while the app is open. */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant()
            emit(dayBoundary.questDay(now))
            delay(Duration.between(now, dayBoundary.nextReset(now)).toMillis() + 1)
        }
    }.distinctUntilChanged()

    /** The daily streak, as of the last closed day. */
    fun observeStreak(): Flow<StreakState> = meta.observe(STREAK_KEY).map(StreakState::decode).distinctUntilChanged()

    fun observeStats(stats: List<Stat> = Stat.launchSet): Flow<List<StatProgress>> =
        xp.observeTotals().map { rows -> StatProgress.of(rows.toTotals(), stats) }

    /** Quests due on [day], each with its completion if there is one. */
    fun observeDay(day: LocalDate): Flow<List<DayQuest>> = combine(
        quests.observeAll(),
        completions.observeOn(day.toEpochDay()),
        completions.observeFinishedOneTimeIds(),
        meta.observe(GYM_LINKED_KEY),
    ) { all, doneToday, finishedOneTime, gymLinked ->
        val byQuest = doneToday.associateBy { it.questId }
        val finished = finishedOneTime.toSet()
        all.map(QuestMapper::toDomain)
            .filter { quest ->
                val completion = byQuest[quest.id]
                when {
                    completion != null -> true
                    !quest.schedule.isDueOn(day) -> false
                    // A finished one-time quest stays visible only on the day it was done.
                    quest.schedule is Schedule.OneTime -> quest.id !in finished
                    else -> true
                }
            }
            .map { DayQuest(it, byQuest[it.id], QuestChecks.of(it, gymConnected = gymLinked != null)) }
    }

    /**
     * Today's quests still open that can be done today, the ones the System nags about.
     * Quests settled from tomorrow's data (phone off at night, the day's meals) are left out.
     */
    suspend fun openToday(): List<DayQuest> = observeDay(today()).first().filter { !it.isDone && Enforcer.pushable(it.check) }

    /** Closed days failed in a row up to yesterday: misses that no shield or rest day excused. */
    suspend fun failRun(): Int = Drill.failRun(
        tracking.recentDays(FAIL_RUN_LOOKBACK).map {
            DayOutcomeRow(it.missed, excused = it.outcome == DayOutcome.SHIELDED.name || it.outcome == DayOutcome.RESTED.name)
        },
    )

    /** How [quest] is checked, given whether Gym is linked right now. */
    suspend fun checkOf(quest: Quest): AutoCheck? = QuestChecks.of(quest, gymConnected = meta.get(GYM_LINKED_KEY) != null)

    /** Records whether the Gym app is linked, which makes the gym quest checked rather than tapped. */
    suspend fun setGymLinked(linked: Boolean) {
        val current = meta.get(GYM_LINKED_KEY) != null
        if (linked == current) return
        if (linked) meta.put(MetaEntity(GYM_LINKED_KEY, "1")) else meta.delete(GYM_LINKED_KEY)
    }

    /** Every quest with a reminder, with today's state, re-emitted on any change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReminderInputs(): Flow<List<ReminderInput>> = combine(
        observeAllQuests(),
        observeToday().flatMapLatest { completions.observeOn(it.toEpochDay()) },
        completions.observeFinishedOneTimeIds(),
    ) { all, doneToday, finishedOneTime ->
        val done = doneToday.map { it.questId }.toSet()
        val finished = finishedOneTime.toSet()
        all.filter { it.reminder != null }.map { ReminderInput(it, it.id in done, it.id in finished) }
    }

    suspend fun isDoneToday(questId: Long): Boolean {
        val quest = getQuest(questId) ?: return false
        return when (quest.schedule) {
            is Schedule.OneTime -> completions.latest(questId) != null
            is Schedule.Recurring -> completions.get(questId, today().toEpochDay()) != null
        }
    }

    fun observeAllQuests(): Flow<List<Quest>> =
        quests.observeAll().map { rows -> rows.map(QuestMapper::toDomain) }

    fun observeQuestsFeeding(stat: Stat): Flow<List<Quest>> =
        quests.observeFeeding(stat.key).map { rows -> rows.map(QuestMapper::toDomain) }

    fun observeRecentXp(stat: Stat, limit: Int = 30): Flow<List<XpEventEntity>> =
        xp.observeRecent(stat.key, limit)

    suspend fun getQuest(id: Long): Quest? = quests.get(id)?.let(QuestMapper::toDomain)

    /** Last logged number for a logging quest, used to prefill the input. */
    suspend fun lastLoggedValue(questId: Long): Double? = completions.lastValue(questId)

    /** Inserts a new quest (id 0) or replaces an existing one. Returns its id. */
    suspend fun saveQuest(quest: Quest): Long = db.withTransaction {
        val now = clock.millis()
        if (quest.id == 0L) {
            val id = quests.insert(QuestMapper.toEntity(quest, now))
            quests.insertStats(QuestMapper.statRows(id, quest.stats))
            id
        } else {
            val existing = requireNotNull(quests.get(quest.id)) { "No quest with id ${quest.id}" }
            quests.update(QuestMapper.toEntity(quest, existing.quest.createdAtMillis))
            quests.clearStats(quest.id)
            quests.insertStats(QuestMapper.statRows(quest.id, quest.stats))
            quest.id
        }
    }

    /** Deletes the quest and its completions. XP already earned stays in the ledger. */
    suspend fun deleteQuest(id: Long) = quests.delete(id)

    /**
     * Completes [questId] for the current quest day and grants its XP.
     * Logging quests with a number input must pass [value].
     */
    suspend fun complete(questId: Long, value: Double? = null): CompletionResult = db.withTransaction {
        val quest = quests.get(questId)?.let(QuestMapper::toDomain)
            ?: return@withTransaction CompletionResult.NotFound
        if (quest.input is CompletionInput.Number) {
            requireNotNull(value) { "\"${quest.title}\" needs a number to complete" }
        }
        checkOf(quest)?.let { return@withTransaction CompletionResult.Automatic(it) }

        val day = dayBoundary.questDay(clock.instant())
        val performance = if (value != null && Stat.MONEY in quest.stats) {
            Spending.performance(value, completions.valuesBefore(questId, day.toEpochDay()))
        } else {
            null
        }
        complete(quest, day, value, performance)
    }

    /**
     * Sets [questId]'s completion on [day] to what the System measured: completed with XP for
     * [performance], re-scored when an earlier verdict moved (the difference is granted or
     * taken back), or left open when the performance fails. Each new or changed verdict is
     * logged to auto_check with [detail]. A hand-tapped completion is left alone, and a day
     * [closeFinishedDays] already settled can't change, so call this before it.
     */
    suspend fun verify(questId: Long, day: LocalDate, check: AutoCheck, performance: Double, detail: String): CompletionResult = db.withTransaction {
        val quest = quests.get(questId)?.let(QuestMapper::toDomain)
            ?: return@withTransaction CompletionResult.NotFound
        val lastClosed = meta.get(LAST_CLOSED_DAY_KEY)?.toLong()
        if (lastClosed != null && day.toEpochDay() <= lastClosed) return@withTransaction CompletionResult.Closed
        if (!quest.schedule.isDueOn(day)) return@withTransaction CompletionResult.NotDue
        val existing = completions.get(questId, day.toEpochDay())
        if (existing != null && existing.performance == null) return@withTransaction CompletionResult.AlreadyDone
        if (existing != null && existing.performance == performance) return@withTransaction CompletionResult.AlreadyDone

        val now = clock.millis()
        val failed = Performance.isFail(performance)
        // A fail that matches the last verdict (a gym session still under the bar) isn't news.
        val lastCheck = tracking.lastAutoCheck(questId, day.toEpochDay())
        if (existing == null && failed && lastCheck?.performance == performance) return@withTransaction CompletionResult.Failed(performance)
        val result = when {
            existing == null && failed -> CompletionResult.Failed(performance)
            existing == null -> complete(quest, day, null, performance)
            else -> {
                // Move the XP already granted to what the new verdict is worth.
                val granted = xp.forCompletion(existing.id).groupBy { Stat.fromKey(it.stat) }.mapValues { (_, e) -> e.sumOf { it.delta } }
                val wanted = if (failed) emptyMap() else XpRules.scoredDeltas(quest, performance)
                val diff = (granted.keys + wanted.keys).associateWith { (wanted[it] ?: 0) - (granted[it] ?: 0) }.filterValues { it != 0 }
                val changes = XpRules.apply(xp.totals().toTotals(), diff)
                xp.insertAll(
                    diff.map { (stat, delta) ->
                        XpEventEntity(
                            stat = stat.key,
                            delta = delta,
                            reason = XpReason.RESCORE,
                            questId = questId,
                            // A dropped completion's events are detached like an undo's.
                            completionId = if (failed) null else existing.id,
                            epochDay = day.toEpochDay(),
                            createdAtMillis = now,
                        )
                    },
                )
                if (failed) {
                    completions.delete(existing.id)
                    CompletionResult.Failed(performance)
                } else {
                    completions.setPerformance(existing.id, performance)
                    CompletionResult.Rescored(changes, performance)
                }
            }
        }
        tracking.insertAutoCheck(
            AutoCheckEntity(
                epochDay = day.toEpochDay(),
                questId = questId,
                kind = check.name,
                performance = performance,
                detail = detail,
                xp = if (failed) 0 else XpRules.scoredDeltas(quest, performance).values.sum(),
                atMillis = now,
            ),
        )
        result
    }

    /** Due daily quests on [day] that existed by then, done or not. */
    suspend fun dailiesDueOn(day: LocalDate): List<Quest> {
        val rows = quests.all()
        val createdOn = rows.associate { it.quest.id to dayBoundary.questDay(Instant.ofEpochMilli(it.quest.createdAtMillis)) }
        return DailyReset.missed(rows.map(QuestMapper::toDomain), day, emptySet()) { !createdOn.getValue(it.id).isAfter(day) }
    }

    /** XP that isn't tied to a quest, such as staying within the screen-time budget. */
    suspend fun grantBonus(stat: Stat, xpAmount: Int, day: LocalDate, reason: String): List<StatChange> = db.withTransaction {
        val totals = xp.totals().toTotals()
        // A loss (a day far over the screen-time budget) never takes a level.
        val delta = XpRules.settle(totals[stat] ?: 0, xpAmount)
        if (delta == 0) return@withTransaction emptyList()
        val changes = XpRules.apply(totals, mapOf(stat to delta))
        xp.insertAll(
            listOf(
                XpEventEntity(
                    stat = stat.key,
                    delta = delta,
                    reason = reason,
                    questId = null,
                    completionId = null,
                    epochDay = day.toEpochDay(),
                    createdAtMillis = clock.millis(),
                ),
            ),
        )
        changes
    }

    private suspend fun complete(quest: Quest, day: LocalDate, value: Double?, performance: Double?): CompletionResult {
        val questId = quest.id
        val now = clock.instant()
        if (!quest.schedule.isDueOn(day)) return CompletionResult.NotDue
        val alreadyDone = when (quest.schedule) {
            is Schedule.OneTime -> completions.latest(questId) != null
            is Schedule.Recurring -> completions.get(questId, day.toEpochDay()) != null
        }
        if (alreadyDone) return CompletionResult.AlreadyDone

        val completionId = completions.insert(
            CompletionEntity(
                questId = questId,
                epochDay = day.toEpochDay(),
                completedAtMillis = now.toEpochMilli(),
                value = value,
                performance = performance,
            ),
        )
        val totals = xp.totals().toTotals()
        val deltas = (performance?.let { XpRules.scoredDeltas(quest, it) } ?: XpRules.completionDeltas(quest))
            .mapValues { (stat, delta) -> XpRules.settle(totals[stat] ?: 0, delta) }
            .filterValues { it != 0 }
        val changes = XpRules.apply(totals, deltas)
        xp.insertAll(
            deltas.map { (stat, delta) ->
                XpEventEntity(
                    stat = stat.key,
                    delta = delta,
                    reason = XpReason.COMPLETION,
                    questId = questId,
                    completionId = completionId,
                    epochDay = day.toEpochDay(),
                    createdAtMillis = now.toEpochMilli(),
                )
            },
        )
        return CompletionResult.Completed(changes, performance)
    }

    /**
     * Reverses today's completion of [questId], for an accidental tap. Returns the XP
     * changes, or null when there was nothing to undo. Unlike a penalty, an undo can lower
     * a level, because it only takes back XP that should never have been granted.
     */
    suspend fun undoCompletion(questId: Long): List<StatChange>? = db.withTransaction {
        val quest = quests.get(questId)?.let(QuestMapper::toDomain) ?: return@withTransaction null
        // The System's own verdicts aren't taps to take back.
        if (checkOf(quest) != null) return@withTransaction null
        val day = today()
        val completion = when (quest.schedule) {
            is Schedule.OneTime -> completions.latest(questId)?.takeIf { it.epochDay == day.toEpochDay() }
            is Schedule.Recurring -> completions.get(questId, day.toEpochDay())
        } ?: return@withTransaction null

        val granted = xp.forCompletion(completion.id)
        val reversal = granted.groupBy { Stat.fromKey(it.stat) }.mapValues { (_, events) -> -events.sumOf { it.delta } }
        val changes = XpRules.apply(xp.totals().toTotals(), reversal)
        val now = clock.millis()
        xp.insertAll(
            reversal.map { (stat, delta) ->
                XpEventEntity(
                    stat = stat.key,
                    delta = delta,
                    reason = XpReason.UNDO,
                    questId = questId,
                    completionId = null,
                    epochDay = day.toEpochDay(),
                    createdAtMillis = now,
                )
            },
        )
        completions.delete(completion.id)
        changes
    }

    /**
     * Closes every quest day that ended since the last call: missed daily quests cost XP
     * (never a level), yesterday's misses get a make-up quest for today, and expired
     * make-up quests are removed. Idempotent, so it is safe to call on every launch and
     * at every rollover. The first call after install only sets the starting point.
     */
    suspend fun closeFinishedDays(): DayCloseReport = db.withTransaction {
        val today = today()
        refreshTestDayQuest(today)
        val yesterday = today.minusDays(1)
        val lastClosed = meta.get(LAST_CLOSED_DAY_KEY)?.toLong()?.let(LocalDate::ofEpochDay)
        // Never move the marker backwards (e.g. after a clock change), or days would be penalized twice.
        if (lastClosed == null || lastClosed.isBefore(yesterday)) {
            meta.put(MetaEntity(LAST_CLOSED_DAY_KEY, yesterday.toEpochDay().toString()))
        }
        if (lastClosed == null || !lastClosed.isBefore(yesterday)) {
            quests.deletePenaltyQuestsExpiredBefore(today.toEpochDay())
            return@withTransaction DayCloseReport()
        }

        val rows = quests.all()
        val all = rows.map(QuestMapper::toDomain)
        val createdOn = rows.associate {
            it.quest.id to dayBoundary.questDay(Instant.ofEpochMilli(it.quest.createdAtMillis))
        }
        val now = clock.millis()
        val missedYesterday = mutableListOf<Quest>()
        var xpLost = 0
        var streak = StreakState.decode(meta.get(STREAK_KEY))
        var shieldsUsed = 0
        var streakLost = 0
        var restedDays = 0
        val rest = hunter.restDays().toSet()

        var day = lastClosed.plusDays(1)
        while (day.isBefore(today)) {
            val done = completions.questIdsOn(day.toEpochDay()).toSet()
            val existed = { quest: Quest -> !createdOn.getValue(quest.id).isAfter(day) }
            val missed = DailyReset.missed(all, day, done, existed)
            val due = DailyReset.missed(all, day, emptySet(), existed).size
            val before = streak.current
            val (next, outcome) = Streaks.close(streak, due, missed.size, rested = day.toEpochDay() in rest)
            streak = next
            val record = DayRecordEntity(
                epochDay = day.toEpochDay(),
                due = due,
                missed = missed.size,
                outcome = outcome.name,
                streakAfter = streak.current,
                shieldsAfter = streak.shields,
                xpLost = 0,
                closedAtMillis = now,
            )
            when (outcome) {
                DayOutcome.SHIELDED, DayOutcome.RESTED -> {
                    tracking.putDay(record)
                    if (outcome == DayOutcome.SHIELDED) shieldsUsed++ else restedDays++
                    day = day.plusDays(1)
                    continue
                }
                DayOutcome.BROKEN -> streakLost = maxOf(streakLost, before)
                DayOutcome.EMPTY, DayOutcome.CLEAR -> Unit
            }
            val penalties = DailyReset.penalties(xp.totals().toTotals(), missed)
            xp.insertAll(
                penalties.map {
                    XpEventEntity(
                        stat = it.stat.key,
                        delta = it.delta,
                        reason = XpReason.PENALTY,
                        questId = it.questId,
                        completionId = null,
                        epochDay = day.toEpochDay(),
                        createdAtMillis = now,
                    )
                },
            )
            xpLost -= penalties.sumOf { it.delta }
            tracking.putDay(record.copy(xpLost = -penalties.sumOf { it.delta }))
            if (day == yesterday) missedYesterday += missed
            day = day.plusDays(1)
        }

        meta.put(MetaEntity(STREAK_KEY, streak.encode()))
        missedYesterday.forEach { saveQuest(DailyReset.penaltyQuest(it, today)) }
        quests.deletePenaltyQuestsExpiredBefore(today.toEpochDay())
        DayCloseReport(missedYesterday, xpLost, shieldsUsed, streakLost, restedDays)
    }

    /**
     * On the first Saturday of the month, adds that day's Test day quest; afterwards removes
     * it so old Test days don't pile up in the quest list. Earned XP stays in the ledger.
     */
    private suspend fun refreshTestDayQuest(today: LocalDate) {
        val stored = meta.get(TEST_DAY_KEY)?.split(':')?.let { (day, id) -> LocalDate.ofEpochDay(day.toLong()) to id.toLong() }
        if (stored != null && stored.first.isBefore(today)) {
            quests.delete(stored.second)
            meta.delete(TEST_DAY_KEY)
        }
        if (TestDay.isTestDay(today) && stored?.first != today) {
            val id = saveQuest(TestDay.quest(today))
            meta.put(MetaEntity(TEST_DAY_KEY, "${today.toEpochDay()}:$id"))
        }
    }

    /** Seeds the default quests once per install, so deleting them all doesn't bring them back. */
    suspend fun seedDefaultsOnce() = db.withTransaction {
        if (meta.get(SEEDED_KEY) != null) return@withTransaction
        if (quests.count() == 0) {
            DefaultQuests.all.forEach { saveQuest(it) }
        }
        meta.put(MetaEntity(SEEDED_KEY, "1"))
    }

    private fun List<StatTotalRow>.toTotals(): Map<Stat, Long> =
        associate { Stat.fromKey(it.stat) to it.total }

    private companion object {
        const val SEEDED_KEY = "seeded_default_quests"
        const val LAST_CLOSED_DAY_KEY = "last_closed_quest_day"
        const val TEST_DAY_KEY = "test_day_quest"
        const val STREAK_KEY = "streak"
        const val GYM_LINKED_KEY = "gym_linked"
        const val FAIL_RUN_LOOKBACK = 60
    }
}
