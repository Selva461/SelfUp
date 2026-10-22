package app.selfup.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A multiple-choice question. [code] is an optional snippet shown in monospace above the options. */
public data class Question(
    val prompt: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val code: String? = null,
) {
    init {
        require(prompt.isNotBlank()) { "A question needs a prompt" }
        require(options.size in 2..5) { "A question needs 2 to 5 options: $prompt" }
        require(options.toSet().size == options.size) { "Options must be distinct: $prompt" }
        require(answer in options.indices) { "Answer index out of range: $prompt" }
    }
}

/**
 * One idea worth knowing, such as "HashMap internals". [card] is what you read to reach Met;
 * [task] is the small build that proves Applied; [keyPoints] are what an explanation must
 * cover to reach Teachable.
 */
public data class Concept(
    val id: String,
    val section: String,
    val title: String,
    val card: List<String>,
    val questions: List<Question>,
    val task: String,
    val keyPoints: List<String>,
) {
    init {
        require(id.matches(Regex("[a-z0-9_.]+"))) { "Concept ids are lowercase keys: $id" }
        require(card.isNotEmpty()) { "Concept $id needs a card" }
        require(questions.size >= SkillRules.QUIZ_LENGTH) { "Concept $id needs ${SkillRules.QUIZ_LENGTH}+ questions" }
        require(keyPoints.size >= 2) { "Concept $id needs key points" }
    }
}

public data class SkillPack(
    val id: String,
    val name: String,
    val stat: Stat,
    val concepts: List<Concept>,
) {
    init {
        require(concepts.map { it.id }.toSet().size == concepts.size) { "Duplicate concept ids in $id" }
    }

    val sections: List<String> get() = concepts.map { it.section }.distinct()
}

/** Mastery levels, earned in order. */
public enum class Mastery(public val level: Int, public val displayName: String, public val howToEarn: String) {
    UNSEEN(0, "Unseen", "Not started"),
    MET(1, "Met", "Read the concept card"),
    RECALLED(2, "Recalled", "Score 60% or more on its quiz"),
    SOLID(3, "Solid", "Score 80% or more twice, at least 3 days apart"),
    APPLIED(4, "Applied", "Finish the coding task"),
    TEACHABLE(5, "Teachable", "Explain it aloud, covering every key point"),
    ;

    public companion object {
        public fun of(level: Int): Mastery = entries.first { it.level == level }
    }
}

/** Something that happened to a concept. The full history is kept; mastery is replayed from it. */
public sealed interface SkillEvent {
    public val day: LocalDate

    public data class Read(override val day: LocalDate) : SkillEvent
    public data class Quiz(override val day: LocalDate, val correct: Int, val total: Int) : SkillEvent {
        init {
            require(total > 0 && correct in 0..total) { "Score $correct/$total is invalid" }
        }

        val fraction: Double get() = correct.toDouble() / total
    }
    public data class Applied(override val day: LocalDate) : SkillEvent
    public data class Taught(override val day: LocalDate) : SkillEvent
}

/** Where a concept stands after replaying its events. */
public data class ConceptState(
    val mastery: Mastery = Mastery.UNSEEN,
    /** Day of the last quiz, for spacing and fading. */
    val lastQuiz: LocalDate? = null,
    /** First strong (80%+) score of the current run, waiting for a spaced second one. */
    val pendingStrong: LocalDate? = null,
    /** Number of passed quizzes, which sets the next review interval. */
    val passes: Int = 0,
) {
    /** Solid or better, but untested for [SkillRules.FADE_DAYS]: due for review before it slips. */
    public fun isFading(today: LocalDate): Boolean =
        mastery.level >= Mastery.SOLID.level && lastQuiz != null && daysSince(lastQuiz, today) >= SkillRules.FADE_DAYS

    /** The day the next spaced review falls on, or null when not yet quizzed. */
    public fun nextReview(): LocalDate? {
        val last = lastQuiz ?: return null
        val interval = SkillRules.REVIEW_INTERVALS.getOrElse(passes) { SkillRules.REVIEW_INTERVALS.last() }
        return last.plusDays(interval)
    }

    public fun isDue(today: LocalDate): Boolean = nextReview()?.let { !today.isBefore(it) } ?: false
}

public object SkillRules {
    public const val QUIZ_LENGTH: Int = 5
    public const val PASS: Double = 0.6
    public const val STRONG: Double = 0.8
    public const val SOLID_SPACING_DAYS: Long = 3
    public const val FADE_DAYS: Long = 30

    /** Days until the next review after 0, 1, 2… passes: expanding gaps, the core of spaced retrieval. */
    public val REVIEW_INTERVALS: List<Long> = listOf(1, 3, 7, 14, 30)

    /**
     * XP for a perfect first quiz of a concept on a day. Fewer right earns less (see
     * [Performance]): 4/5 earns 5, 3/5 earns 2, and 1/5 or 0/5 costs 1. Retakes the same day
     * earn nothing, so the first attempt is the one that counts.
     */
    public const val QUIZ_XP: Int = 10

    /** Bonus for reaching Solid, Applied or Teachable. */
    public const val MASTERY_BONUS_XP: Int = 15

    /** Replays [events] (any order) into the concept's state. */
    public fun replay(events: List<SkillEvent>): ConceptState =
        events.sortedBy { it.day }.fold(ConceptState(), ::apply)

    public fun apply(state: ConceptState, event: SkillEvent): ConceptState = when (event) {
        is SkillEvent.Read -> state.copy(mastery = maxOf(state.mastery, Mastery.MET))
        is SkillEvent.Quiz -> quiz(state, event)
        is SkillEvent.Applied ->
            if (state.mastery.level >= Mastery.SOLID.level) state.copy(mastery = maxOf(state.mastery, Mastery.APPLIED)) else state
        is SkillEvent.Taught ->
            if (state.mastery.level >= Mastery.APPLIED.level) state.copy(mastery = Mastery.TEACHABLE) else state
    }

    private fun quiz(state: ConceptState, quiz: SkillEvent.Quiz): ConceptState {
        val tested = state.copy(lastQuiz = quiz.day)
        return when {
            quiz.fraction < PASS -> tested.copy(
                // A failed review knocks a solid concept back one step; earlier levels hold.
                mastery = if (state.mastery.level >= Mastery.SOLID.level) Mastery.of(state.mastery.level - 1) else state.mastery,
                pendingStrong = null,
                passes = 0,
            )
            quiz.fraction < STRONG -> tested.copy(
                mastery = maxOf(state.mastery, Mastery.RECALLED),
                passes = state.passes + 1,
            )
            else -> {
                val first = state.pendingStrong
                val spaced = first != null && daysSince(first, quiz.day) >= SOLID_SPACING_DAYS
                tested.copy(
                    mastery = maxOf(state.mastery, if (spaced) Mastery.SOLID else Mastery.RECALLED),
                    pendingStrong = if (spaced) null else first ?: quiz.day,
                    passes = state.passes + 1,
                )
            }
        }
    }

    /**
     * XP earned by [event], given the state before it and the events already logged today
     * for the same concept. The day's first quiz is scored by its share right (see
     * [QUIZ_XP]); reaching Solid, Applied or Teachable adds [MASTERY_BONUS_XP].
     */
    public fun xpFor(before: ConceptState, event: SkillEvent, earlierToday: List<SkillEvent>): Int {
        val after = apply(before, event)
        val quizzedAlready = earlierToday.any { it is SkillEvent.Quiz }
        val quizXp = if (event is SkillEvent.Quiz && !quizzedAlready) Performance.xp(QUIZ_XP, event.fraction) else 0
        val bonus = if (after.mastery > before.mastery && after.mastery.level >= Mastery.SOLID.level) MASTERY_BONUS_XP else 0
        return quizXp + bonus
    }
}

/** Today's study session: reviews that are due first, then new concepts in pack order. */
public data class StudySession(val reviews: List<Concept>, val fresh: List<Concept>) {
    val all: List<Concept> get() = reviews + fresh
    val isEmpty: Boolean get() = reviews.isEmpty() && fresh.isEmpty()
}

public object StudyPlanner {
    public const val MAX_REVIEWS: Int = 3
    public const val MAX_NEW: Int = 1

    public fun session(
        pack: SkillPack,
        states: Map<String, ConceptState>,
        today: LocalDate,
        maxReviews: Int = MAX_REVIEWS,
        maxNew: Int = MAX_NEW,
    ): StudySession {
        fun state(c: Concept) = states[c.id] ?: ConceptState()
        val reviews = pack.concepts
            .filter { state(it).isDue(today) || state(it).isFading(today) }
            // Most overdue first; fading solid concepts count as overdue too.
            .sortedBy { state(it).nextReview() ?: today }
            .take(maxReviews)
        val fresh = pack.concepts
            .filter { state(it).lastQuiz == null && it !in reviews }
            .take(maxNew)
        return StudySession(reviews, fresh)
    }

    /** Count of concepts at each mastery level, for the pack's progress ring. */
    public fun breakdown(pack: SkillPack, states: Map<String, ConceptState>): Map<Mastery, Int> =
        Mastery.entries.associateWith { level -> pack.concepts.count { (states[it.id]?.mastery ?: Mastery.UNSEEN) == level } }

    /** Overall progress through a pack from 0 to 1: each concept counts its mastery out of 5. */
    public fun progress(pack: SkillPack, states: Map<String, ConceptState>): Double =
        pack.concepts.sumOf { states[it.id]?.mastery?.level ?: 0 }.toDouble() / (pack.concepts.size * Mastery.TEACHABLE.level)
}

private fun daysSince(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
