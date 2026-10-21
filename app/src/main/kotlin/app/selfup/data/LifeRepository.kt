package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.CompletionInput
import app.selfup.core.DailyReset
import app.selfup.core.DayBoundary
import app.selfup.core.DefaultQuests
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.StatProgress
import app.selfup.core.XpRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
) {
    val isDone: Boolean get() = completion != null
}

/** What closing out finished quest days did, for telling the user. */
data class DayCloseReport(
    val missed: List<Quest> = emptyList(),
    val xpLost: Int = 0,
) {
    val isEmpty: Boolean get() = missed.isEmpty()
}

/** What the reminder scheduler needs to know about one quest. */
data class ReminderInput(
    val quest: Quest,
    val doneToday: Boolean,
    /** A one-time quest that is complete for good. */
    val finished: Boolean,
)

sealed interface CompletionResult {
    data class Completed(val changes: List<StatChange>) : CompletionResult
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

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    /** The current quest day, re-emitted when the day rolls over while the app is open. */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant()
            emit(dayBoundary.questDay(now))
            delay(Duration.between(now, dayBoundary.nextReset(now)).toMillis() + 1)
        }
    }.distinctUntilChanged()

    fun observeStats(stats: List<Stat> = Stat.launchSet): Flow<List<StatProgress>> =
        xp.observeTotals().map { rows -> StatProgress.of(rows.toTotals(), stats) }

    /** Quests due on [day], each with its completion if there is one. */
    fun observeDay(day: LocalDate): Flow<List<DayQuest>> = combine(
        quests.observeAll(),
        completions.observeOn(day.toEpochDay()),
        completions.observeFinishedOneTimeIds(),
    ) { all, doneToday, finishedOneTime ->
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
            .map { DayQuest(it, byQuest[it.id]) }
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

        val now = clock.instant()
        val day = dayBoundary.questDay(now)
        if (!quest.schedule.isDueOn(day)) return@withTransaction CompletionResult.NotDue
        val alreadyDone = when (quest.schedule) {
            is Schedule.OneTime -> completions.latest(questId) != null
            is Schedule.Recurring -> completions.get(questId, day.toEpochDay()) != null
        }
        if (alreadyDone) return@withTransaction CompletionResult.AlreadyDone

        val completionId = completions.insert(
            CompletionEntity(
                questId = questId,
                epochDay = day.toEpochDay(),
                completedAtMillis = now.toEpochMilli(),
                value = value,
            ),
        )
        val deltas = XpRules.completionDeltas(quest)
        val changes = XpRules.apply(xp.totals().toTotals(), deltas)
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
        CompletionResult.Completed(changes)
    }

    /**
     * Reverses today's completion of [questId], for an accidental tap. Returns the XP
     * changes, or null when there was nothing to undo. Unlike a penalty, an undo can lower
     * a level, because it only takes back XP that should never have been granted.
     */
    suspend fun undoCompletion(questId: Long): List<StatChange>? = db.withTransaction {
        val quest = quests.get(questId)?.let(QuestMapper::toDomain) ?: return@withTransaction null
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

        var day = lastClosed.plusDays(1)
        while (day.isBefore(today)) {
            val done = completions.questIdsOn(day.toEpochDay()).toSet()
            val missed = DailyReset.missed(all, day, done) { quest -> !createdOn.getValue(quest.id).isAfter(day) }
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
            if (day == yesterday) missedYesterday += missed
            day = day.plusDays(1)
        }

        missedYesterday.forEach { saveQuest(DailyReset.penaltyQuest(it, today)) }
        quests.deletePenaltyQuestsExpiredBefore(today.toEpochDay())
        DayCloseReport(missedYesterday, xpLost)
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
    }
}
