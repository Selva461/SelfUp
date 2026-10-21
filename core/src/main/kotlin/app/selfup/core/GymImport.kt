package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A working set as the Gym app shares it. Warm-ups never arrive. */
public data class GymSet(
    val setId: Long,
    val sessionId: Long,
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
    /** Gym's encoding: empty, a legacy bare word, or "Axis=Value|Axis=Value". */
    val variation: String,
    val assisted: Boolean,
    val loggedAt: Instant,
)

public data class GymCardio(
    val id: Long,
    val activity: String,
    val minutes: Int,
    val distanceKm: Double,
    val loggedAt: Instant,
)

public data class GymWeighIn(val day: LocalDate, val weightKg: Double)

/** A record entry that came from Gym. [source] identifies it, so a re-sync updates rather than duplicates. */
public data class ImportedRecord(val source: String, val entry: RecordEntry)

/**
 * Turns a Gym training log into record entries.
 *
 * Records track a handful of benchmark lifts, so only the exact movements count: a
 * close-grip or incline bench is a different lift, and letting it into the bench history
 * would make the estimate jump around for reasons that aren't strength. Variations that
 * make a lift harder (paused, slow eccentric) are kept, because they can only understate.
 *
 * One entry per lift per workout, from its best set, so the history reads as sessions
 * rather than every set of every session.
 */
public object GymImport {
    public const val SOURCE_PREFIX: String = "gym:"

    /** Gym's built-in names (lowercased), plus the obvious names of a lift someone added themselves. */
    private val exercises: Map<String, RecordKind> = buildMap {
        fun map(kind: RecordKind, vararg names: String) = names.forEach { put(it, kind) }
        map(RecordKind.BENCH, "barbell bench press", "paused barbell bench press", "bench press", "bench")
        map(RecordKind.SQUAT, "back squat", "high-bar back squat", "low-bar back squat", "paused squat", "squat", "barbell squat", "barbell back squat")
        map(RecordKind.DEADLIFT, "deadlift", "sumo deadlift", "paused deadlift", "deficit deadlift", "conventional deadlift", "barbell deadlift")
        map(RecordKind.OVERHEAD_PRESS, "overhead press", "ohp", "military press", "barbell overhead press", "standing overhead press")
        map(RecordKind.PULL_UPS, "pull-up", "neutral-grip pull-up", "wide-grip pull-up", "weighted pull-up", "pull up", "pullup", "pull-ups", "pull ups", "pullups")
        map(RecordKind.PUSH_UPS, "push-up", "push up", "pushup", "push-ups", "push ups", "pushups")
        map(RecordKind.PLANK, "plank")
    }

    /** Variation values that leave a lift comparable with its plain version. */
    private val comparable = setOf(
        "standard", "normal", "flat", "both", "overhand", "mixed", "neutral", "shoulder width", "mid", "sumo",
        "paused", "slow eccentric",
    )

    private val running = setOf("running", "run", "treadmill run")

    /** Gym stores body weight per day; noon keeps it inside that quest day whatever the day boundary. */
    private val weighInTime: LocalTime = LocalTime.NOON

    public fun kindFor(exercise: String): RecordKind? =
        exercises[exercise.trim().lowercase().replace(Regex("\\s+"), " ")]

    public fun isComparable(variation: String): Boolean {
        val text = variation.trim()
        if (text.isEmpty()) return true
        val values = if ('=' in text) text.split('|').map { it.substringAfter('=') } else listOf(text)
        return values.all { it.trim().lowercase() in comparable }
    }

    public fun records(
        sets: List<GymSet>,
        cardio: List<GymCardio>,
        weighIns: List<GymWeighIn>,
        zone: ZoneId,
    ): List<ImportedRecord> = buildList {
        sets.mapNotNull { set -> entryFor(set)?.let { set to it } }
            .groupBy { (set, entry) -> set.sessionId to entry.kind }
            .forEach { (key, candidates) ->
                val (sessionId, kind) = key
                val best = candidates
                    .sortedBy { (set, _) -> set.loggedAt }
                    .maxWith(compareBy { (_, entry) -> RecordMath.metrics(entry).getValue(kind.metrics.first()) })
                add(ImportedRecord("${SOURCE_PREFIX}session:$sessionId:${kind.key}", best.second))
            }
        cardio.filter { it.activity.trim().lowercase() in running && it.distanceKm > 0 && it.minutes > 0 }.forEach {
            add(ImportedRecord("${SOURCE_PREFIX}cardio:${it.id}", RecordEntry(RecordKind.RUN, it.loggedAt, distanceKm = it.distanceKm, seconds = it.minutes * 60)))
        }
        weighIns.filter { it.weightKg > 0 }.forEach {
            val at = it.day.atTime(weighInTime).atZone(zone).toInstant()
            add(ImportedRecord("${SOURCE_PREFIX}weight:${it.day.toEpochDay()}", RecordEntry(RecordKind.BODY_WEIGHT, at, weightKg = it.weightKg)))
        }
    }.sortedBy { it.entry.at }

    private fun entryFor(set: GymSet): RecordEntry? {
        val kind = kindFor(set.exercise) ?: return null
        if (set.assisted || !isComparable(set.variation) || set.reps <= 0) return null
        return when (kind.input) {
            RecordInput.WEIGHT_REPS ->
                if (set.weightKg > 0 && set.reps <= RecordMath.MAX_REPS) RecordEntry(kind, set.loggedAt, weightKg = set.weightKg, reps = set.reps) else null
            // Added weight makes reps harder, so a weighted set's reps still count; help doesn't.
            RecordInput.REPS -> if (set.weightKg >= 0) RecordEntry(kind, set.loggedAt, reps = set.reps) else null
            // Gym logs a plank's hold in the reps column, as seconds.
            RecordInput.SECONDS -> if (set.weightKg <= 0) RecordEntry(kind, set.loggedAt, seconds = set.reps) else null
            RecordInput.DISTANCE_TIME, RecordInput.WEIGHT -> null
        }
    }
}
