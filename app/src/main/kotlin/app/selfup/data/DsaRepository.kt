package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.Dsa
import app.selfup.core.DsaAttempt
import app.selfup.core.DsaDifficulty
import app.selfup.core.DsaOutcome
import app.selfup.core.DsaState
import app.selfup.core.DsaTopic
import app.selfup.core.Stat
import app.selfup.core.StatChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

/** A problem with where it stands on the re-solve ladder. */
data class DsaProblemView(
    val problem: DsaProblemEntity,
    val state: DsaState,
    val attempts: List<DsaAttemptEntity>,
) {
    val topic: DsaTopic get() = DsaTopic.fromKey(problem.topic)
    val difficulty: DsaDifficulty get() = DsaDifficulty.fromKey(problem.difficulty)
}

data class DsaOverview(
    val today: LocalDate,
    val problems: List<DsaProblemView> = emptyList(),
) {
    /** Re-solves due today or overdue, most overdue first. */
    val due: List<DsaProblemView> get() = problems.filter { it.state.isDue(today) }.sortedBy { it.state.due }
    val solvedAlone: Int get() = problems.count { it.state.solvedAlone }
    val mastered: Int get() = problems.count { it.state.mastered }
    val attemptedToday: Boolean get() = problems.any { p -> p.attempts.any { it.epochDay == today.toEpochDay() } }

    /** Problems solved alone per topic, for the roadmap. */
    fun solvedIn(topic: DsaTopic): Int = problems.count { it.topic == topic && it.state.solvedAlone }

    /** The first roadmap topic not yet filled: where to work next. */
    val nextTopic: DsaTopic? get() = DsaTopic.entries.firstOrNull { solvedIn(it) < it.roadmap }
}

/** What logging an attempt did, for the snackbar and undo. */
data class DsaResult(
    val attemptId: Long,
    val title: String,
    val outcome: DsaOutcome,
    val performance: Double,
    val xp: Int,
    val state: DsaState,
    val changes: List<StatChange>,
)

/**
 * The DSA tracker: problems and every from-scratch attempt at them. XP goes to Technical
 * through the ledger (reason "dsa"), and each attempt keeps what it paid so it can be undone.
 */
class DsaRepository(private val db: SelfUpDatabase, private val life: LifeRepository) {
    private val dao = db.dsaDao()
    private val clock = life.clock

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<DsaOverview> = life.observeToday().flatMapLatest { today ->
        combine(dao.observeProblems(), dao.observeAttempts()) { problems, attempts ->
            val byProblem = attempts.groupBy { it.problemId }
            DsaOverview(today, problems.map { p -> view(p, byProblem[p.id].orEmpty()) })
        }
    }

    /** Adds a problem with its first attempt. */
    suspend fun add(title: String, topic: DsaTopic, difficulty: DsaDifficulty, url: String?, outcome: DsaOutcome, minutes: Int?): DsaResult = db.withTransaction {
        require(title.isNotBlank()) { "A problem needs a name" }
        val id = dao.insertProblem(
            DsaProblemEntity(title = title.trim(), topic = topic.key, difficulty = difficulty.key, url = url?.trim()?.ifBlank { null }, createdAtMillis = clock.millis()),
        )
        attempt(id, outcome, minutes)
    }

    /** Logs a from-scratch attempt at [problemId] today. */
    suspend fun attempt(problemId: Long, outcome: DsaOutcome, minutes: Int?): DsaResult = db.withTransaction {
        val problem = requireNotNull(dao.problem(problemId)) { "No problem with id $problemId" }
        val before = dao.attempts(problemId)
        val difficulty = DsaDifficulty.fromKey(problem.difficulty)
        val today = life.today()
        val firstSolve = before.none { DsaOutcome.fromKey(it.outcome) == DsaOutcome.ALONE }
        val performance = Dsa.performance(difficulty, outcome, minutes)
        val wanted = Dsa.xp(difficulty, performance, firstSolve)
        val state = Dsa.replay(before.map(::toAttempt) + DsaAttempt(today, outcome, minutes))
        val changes = life.grantBonus(Stat.TECHNICAL, wanted, today, XpReason.DSA)
        val paid = changes.sumOf { it.delta }
        val attemptId = dao.insertAttempt(
            DsaAttemptEntity(
                problemId = problemId,
                outcome = outcome.key,
                minutes = minutes,
                performance = performance,
                xp = paid,
                stageAfter = state.stage,
                epochDay = today.toEpochDay(),
                atMillis = clock.millis(),
            ),
        )
        DsaResult(attemptId, problem.title, outcome, performance, paid, state, changes)
    }

    /** Takes back an attempt and exactly the XP it paid (offered right after logging); a problem left with no attempts goes too. */
    suspend fun undo(attemptId: Long) = db.withTransaction {
        val attempt = dao.attempt(attemptId) ?: return@withTransaction
        // Straight into the ledger, unclamped like a completion undo: it only takes back what was paid.
        if (attempt.xp != 0) {
            db.xpDao().insertAll(
                listOf(XpEventEntity(stat = Stat.TECHNICAL.key, delta = -attempt.xp, reason = XpReason.UNDO, questId = null, completionId = null, epochDay = attempt.epochDay, createdAtMillis = clock.millis())),
            )
        }
        dao.deleteAttempt(attemptId)
        if (dao.attempts(attempt.problemId).isEmpty()) dao.deleteProblem(attempt.problemId)
    }

    /** Every problem's ladder state, for achievements. */
    suspend fun states(): List<DsaState> =
        dao.allAttempts().groupBy { it.problemId }.values.map { Dsa.replay(it.map(::toAttempt)) }

    private fun view(problem: DsaProblemEntity, attempts: List<DsaAttemptEntity>) =
        DsaProblemView(problem, Dsa.replay(attempts.map(::toAttempt)), attempts)

    private fun toAttempt(e: DsaAttemptEntity) = DsaAttempt(LocalDate.ofEpochDay(e.epochDay), DsaOutcome.fromKey(e.outcome), e.minutes)
}
