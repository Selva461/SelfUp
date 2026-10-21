package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.DayBoundary
import app.selfup.core.MindGame
import app.selfup.core.MindRules
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.XpRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** One game as the Mind screen shows it. */
data class MindSummary(
    val game: MindGame,
    /** Every score, oldest first. */
    val scores: List<Double>,
    val playedToday: Boolean,
) {
    val best: Double? get() = scores.reduceOrNull { a, b -> if (game.isBetter(b, a)) b else a }
    val latest: Double? get() = scores.lastOrNull()
}

data class MindResult(
    val game: MindGame,
    val score: Double,
    /** The best before this game; null on the first play. */
    val previousBest: Double?,
    val xp: Int,
    val changes: List<StatChange>,
) {
    val isNewBest: Boolean get() = previousBest != null && game.isBetter(score, previousBest)
}

/** Mind game scores. XP goes to Technical through the same ledger as everything else. */
class MindRepository(
    private val db: SelfUpDatabase,
    private val clock: Clock,
    private val dayBoundary: DayBoundary,
) {
    private val mind = db.mindDao()
    private val xp = db.xpDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    fun observeSummaries(): Flow<List<MindSummary>> = mind.observeAll().map { rows ->
        val today = today().toEpochDay()
        val byGame = rows.groupBy { it.game }
        MindGame.entries.map { game ->
            val played = byGame[game.key].orEmpty()
            MindSummary(game, played.map { it.score }, played.any { it.epochDay == today })
        }
    }

    suspend fun record(game: MindGame, score: Double): MindResult = db.withTransaction {
        val now = clock.instant()
        val day = dayBoundary.questDay(now).toEpochDay()
        val history = mind.forGame(game.key)
        val scores = history.map { it.score }
        val earned = MindRules.xpFor(game, score, scores, playedToday = history.any { it.epochDay == day })
        mind.insert(MindScoreEntity(game = game.key, score = score, epochDay = day, atMillis = now.toEpochMilli(), xpAwarded = earned))
        val changes = if (earned > 0) grant(earned, day, now.toEpochMilli()) else emptyList()
        val previousBest = scores.reduceOrNull { a, b -> if (game.isBetter(b, a)) b else a }
        MindResult(game, score, previousBest, earned, changes)
    }

    private suspend fun grant(delta: Int, epochDay: Long, atMillis: Long): List<StatChange> {
        val totals = xp.totals().associate { Stat.fromKey(it.stat) to it.total }
        val changes = XpRules.apply(totals, mapOf(STAT to delta))
        xp.insertAll(
            listOf(
                XpEventEntity(
                    stat = STAT.key,
                    delta = delta,
                    reason = XpReason.MIND,
                    questId = null,
                    completionId = null,
                    epochDay = epochDay,
                    createdAtMillis = atMillis,
                ),
            ),
        )
        return changes
    }

    companion object {
        val STAT: Stat = Stat.TECHNICAL
    }
}
