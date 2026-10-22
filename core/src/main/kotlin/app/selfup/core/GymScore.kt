package app.selfup.core

import java.time.Instant

/** One Gym workout, boiled down to what decides whether it was up to the mark. */
public data class GymSession(
    val sessionId: Long,
    val start: Instant,
    val end: Instant,
    /** Working sets (warm-ups never arrive from Gym). */
    val sets: Int,
    /** Sum of load × reps over every set, in kg. Unweighted sets carry body weight. */
    val volumeKg: Double,
    /** Best estimated one-rep max per exercise (normalized name), from its heaviest set. */
    val topSets: Map<String, Double>,
)

/** How one session measured up, with every part kept for the detailed log. */
public data class GymVerdict(
    val session: GymSession,
    /** The earlier session it was held against, or null when nothing similar came before. */
    val comparedTo: Long?,
    val setsPart: Double,
    val volumePart: Double,
    /** Share of repeated exercises whose top set matched or beat last time. */
    val strengthPart: Double,
    val performance: Double,
)

/**
 * Whether a gym session was up to the mark. A session gets full marks only when it has at
 * least [MIN_SETS] working sets, moves at least as much total weight as the last session of
 * the same kind, and matches or beats every repeated lift's top set from that session. Each
 * shortfall scales the result down.
 *
 * "The same kind" is the most similar earlier session by exercises (push day against push
 * day), so a light pull day isn't judged against a heavy leg day.
 */
public object GymScore {
    /** Working sets in a full session. */
    public const val MIN_SETS: Int = 12

    /** Sessions sharing less than this share of exercises aren't comparable. */
    public const val MIN_OVERLAP: Double = 0.5

    /** Load counted for an unweighted set when no weigh-in is known. */
    public const val DEFAULT_BODY_WEIGHT_KG: Double = 70.0

    public fun sessions(sets: List<GymSet>, bodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG): List<GymSession> =
        sets.filter { it.reps > 0 }
            .groupBy { it.sessionId }
            .map { (id, rows) ->
                val sorted = rows.sortedBy { it.loggedAt }
                GymSession(
                    sessionId = id,
                    start = sorted.first().loggedAt,
                    end = sorted.last().loggedAt,
                    sets = rows.size,
                    volumeKg = rows.sumOf { load(it, bodyWeightKg) * it.reps },
                    topSets = rows.groupBy { name(it.exercise) }
                        .mapValues { (_, group) -> group.maxOf { RecordMath.estimatedOneRepMax(load(it, bodyWeightKg), it.reps.coerceAtMost(RecordMath.MAX_REPS)) } },
                )
            }
            .sortedBy { it.start }

    /** Scores [session] against the sessions before it in [history] (any order; later ones are ignored). */
    public fun verdict(session: GymSession, history: List<GymSession>): GymVerdict {
        val previous = comparable(session, history)
        val setsPart = Performance.atLeast(session.sets.toDouble(), MIN_SETS.toDouble())
        val volumePart = previous?.let { Performance.atLeast(session.volumeKg, it.volumeKg) } ?: 1.0
        val shared = previous?.topSets?.keys?.intersect(session.topSets.keys).orEmpty()
        val strengthPart = if (shared.isEmpty()) {
            1.0
        } else {
            shared.count { session.topSets.getValue(it) >= previous!!.topSets.getValue(it) - EPSILON }.toDouble() / shared.size
        }
        val performance = setsPart * volumePart * (0.5 + 0.5 * strengthPart)
        return GymVerdict(session, previous?.sessionId, setsPart, volumePart, strengthPart, performance)
    }

    /** The most similar session before [session], latest first among equals, or null. */
    public fun comparable(session: GymSession, history: List<GymSession>): GymSession? =
        history.asSequence()
            .filter { it.sessionId != session.sessionId && it.start < session.start }
            .map { it to overlap(session.topSets.keys, it.topSets.keys) }
            .filter { (_, overlap) -> overlap >= MIN_OVERLAP }
            .maxWithOrNull(compareBy<Pair<GymSession, Double>> { it.second }.thenBy { it.first.start })
            ?.first

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        val union = (a + b).size
        return if (union == 0) 0.0 else a.intersect(b).size.toDouble() / union
    }

    private fun load(set: GymSet, bodyWeightKg: Double): Double = when {
        set.assisted -> (bodyWeightKg - set.weightKg).coerceAtLeast(1.0)
        set.weightKg > 0 -> set.weightKg
        else -> bodyWeightKg
    }

    private fun name(exercise: String): String = exercise.trim().lowercase().replace(Regex("\\s+"), " ")

    private const val EPSILON = 1e-6
}
