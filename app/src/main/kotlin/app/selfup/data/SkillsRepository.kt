package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.ConceptState
import app.selfup.core.DayBoundary
import app.selfup.core.Mastery
import app.selfup.core.SkillEvent
import app.selfup.core.SkillRules
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.XpRules
import app.selfup.core.packs.Packs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** What one concept event changed. */
data class SkillResult(
    val conceptId: String,
    val before: Mastery,
    val after: Mastery,
    val xp: Int,
    /** Stat XP changes, for level-up detection; empty when no XP was earned. */
    val changes: List<StatChange>,
) {
    val masteryUp: Boolean get() = after > before
}

/** Skill packs' progress, replayed from the skill_event history. */
class SkillsRepository(
    private val db: SelfUpDatabase,
    private val clock: Clock,
    private val dayBoundary: DayBoundary,
) {
    private val skills = db.skillDao()
    private val xp = db.xpDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    /** Every concept's state, keyed by concept id. Concepts never touched are absent. */
    fun observeStates(): Flow<Map<String, ConceptState>> = skills.observeAll().map { rows ->
        rows.groupBy { it.conceptId }.mapValues { (_, events) -> SkillRules.replay(events.map { it.toDomain() }) }
    }

    suspend fun read(conceptId: String): SkillResult = record(conceptId) { SkillEvent.Read(it) }

    suspend fun quiz(conceptId: String, correct: Int, total: Int): SkillResult =
        record(conceptId) { SkillEvent.Quiz(it, correct, total) }

    suspend fun applied(conceptId: String): SkillResult = record(conceptId) { SkillEvent.Applied(it) }

    suspend fun taught(conceptId: String): SkillResult = record(conceptId) { SkillEvent.Taught(it) }

    private suspend fun record(conceptId: String, make: (LocalDate) -> SkillEvent): SkillResult = db.withTransaction {
        val pack = requireNotNull(Packs.packOf(conceptId)) { "Unknown concept $conceptId" }
        val now = clock.instant()
        val day = dayBoundary.questDay(now)
        val history = skills.forConcept(conceptId).map { it.toDomain() }
        val before = SkillRules.replay(history)
        val event = make(day)
        val after = SkillRules.apply(before, event)
        val earned = SkillRules.xpFor(before, event, history.filter { it.day == day })

        skills.insert(event.toEntity(conceptId, now.toEpochMilli()))
        val changes = if (earned != 0) grant(pack.stat, earned, day, now.toEpochMilli()) else emptyList()
        SkillResult(conceptId, before.mastery, after.mastery, changes.sumOf { it.delta }, changes)
    }

    /** Grants [delta]; a loss from a bad quiz is clamped so it never costs a level. */
    private suspend fun grant(stat: Stat, delta: Int, day: LocalDate, atMillis: Long): List<StatChange> {
        val totals = xp.totals().associate { Stat.fromKey(it.stat) to it.total }
        val settled = XpRules.settle(totals[stat] ?: 0, delta)
        if (settled == 0) return emptyList()
        val changes = XpRules.apply(totals, mapOf(stat to settled))
        xp.insertAll(
            listOf(
                XpEventEntity(
                    stat = stat.key,
                    delta = settled,
                    reason = XpReason.SKILL,
                    questId = null,
                    completionId = null,
                    epochDay = day.toEpochDay(),
                    createdAtMillis = atMillis,
                ),
            ),
        )
        return changes
    }
}

internal fun SkillEventEntity.toDomain(): SkillEvent {
    val day = LocalDate.ofEpochDay(epochDay)
    return when (kind) {
        SkillEventKind.READ -> SkillEvent.Read(day)
        SkillEventKind.QUIZ -> SkillEvent.Quiz(day, requireNotNull(correct), requireNotNull(total))
        SkillEventKind.APPLIED -> SkillEvent.Applied(day)
        SkillEventKind.TAUGHT -> SkillEvent.Taught(day)
        else -> error("Unknown skill event kind $kind")
    }
}

private fun SkillEvent.toEntity(conceptId: String, atMillis: Long): SkillEventEntity {
    val (kind, correct, total) = when (this) {
        is SkillEvent.Read -> Triple(SkillEventKind.READ, null, null)
        is SkillEvent.Quiz -> Triple(SkillEventKind.QUIZ, correct, total)
        is SkillEvent.Applied -> Triple(SkillEventKind.APPLIED, null, null)
        is SkillEvent.Taught -> Triple(SkillEventKind.TAUGHT, null, null)
    }
    return SkillEventEntity(
        conceptId = conceptId,
        kind = kind,
        epochDay = day.toEpochDay(),
        atMillis = atMillis,
        correct = correct,
        total = total,
    )
}
