package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.CompletionInput
import app.selfup.core.DayBoundary
import app.selfup.core.DefaultQuests
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.StatProgress
import app.selfup.core.XpRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A quest as it appears on a given quest day. */
data class DayQuest(
    val quest: Quest,
    val completion: CompletionEntity?,
) {
    val isDone: Boolean get() = completion != null
}

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
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dayBoundary: DayBoundary = DayBoundary(zone = clock.zone),
) {
    private val quests = db.questDao()
    private val completions = db.completionDao()
    private val xp = db.xpDao()
    private val meta = db.metaDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

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
    }
}
