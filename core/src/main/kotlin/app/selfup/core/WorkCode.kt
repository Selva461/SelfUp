package app.selfup.core

/** Who scored a piece of work. Claude saw the work itself; Groq only reads your description of it. */
public enum class WorkJudge(public val key: String, public val displayName: String, public val factor: Double) {
    CLAUDE("claude", "Claude", 1.0),

    /** Scored from a description alone, so it can never count as perfect. */
    GROQ("groq", "Groq", 0.8),
    ;

    public companion object {
        public fun fromKey(key: String): WorkJudge? = entries.firstOrNull { it.key == key.lowercase() }
    }
}

/**
 * Something you made, scored on a 0–5 rubric by a judge who looked at it.
 *
 * - [difficulty] (1–5) sets the stakes: the full reward is 10 XP per point.
 * - [quality]: does it work, is it well built, would it pass a strict review.
 * - [completeness]: shipped and usable, or half done.
 * - [ownership]: how much was your own thinking and doing, rather than Claude's. Building
 *   with Claude counts, but the reward follows what you did, not what was produced.
 */
public data class WorkReport(
    val title: String,
    val stat: Stat,
    val difficulty: Int,
    val quality: Int,
    val completeness: Int,
    val ownership: Int,
    val hours: Double?,
    val judge: WorkJudge,
    val note: String,
) {
    init {
        require(title.isNotBlank()) { "Work needs a title" }
        require(difficulty in 1..WorkCode.MAX) { "Difficulty must be 1 to ${WorkCode.MAX}" }
        listOf(quality, completeness, ownership).forEach { require(it in 0..WorkCode.MAX) { "Scores must be 0 to ${WorkCode.MAX}" } }
        require(hours == null || hours > 0) { "Hours must be positive" }
    }

    public val fullXp: Int get() = difficulty * WorkCode.XP_PER_DIFFICULTY

    /** The rubric as 0 to 1, weighted towards ownership, and capped for a judge who only read a description. */
    public val performance: Double
        get() {
            val rubric = WorkCode.QUALITY_WEIGHT * quality + WorkCode.COMPLETE_WEIGHT * completeness + WorkCode.OWNERSHIP_WEIGHT * ownership
            return rubric / WorkCode.MAX * judge.factor
        }

    public val xp: Int get() = Performance.xp(fullXp, performance)
}

public sealed interface WorkCodeResult {
    public data class Ok(val report: WorkReport) : WorkCodeResult
    public data class Error(val line: Int, val message: String) : WorkCodeResult
}

/**
 * A plain-text scorecard Claude writes at the end of a session where you built something,
 * for pasting into the app:
 *
 * ```
 * SELFUP WORK 1
 * TITLE Performance-based XP for the System app
 * STAT technical
 * DIFFICULTY 4
 * QUALITY 4
 * COMPLETE 5
 * OWNERSHIP 1
 * HOURS 2
 * JUDGE claude
 * NOTE Shipped and merged; Claude wrote the code, you set the rules.
 * ```
 *
 * STAT, HOURS, JUDGE (claude by default) and NOTE are optional. Each code is accepted once.
 */
public object WorkCode {
    public const val HEADER: String = "SELFUP WORK 1"
    public const val MAX: Int = 5
    public const val XP_PER_DIFFICULTY: Int = 10
    public const val QUALITY_WEIGHT: Double = 0.3
    public const val COMPLETE_WEIGHT: Double = 0.3
    public const val OWNERSHIP_WEIGHT: Double = 0.4
    public const val MAX_TITLE: Int = 100

    private val required = listOf("TITLE", "DIFFICULTY", "QUALITY", "COMPLETE", "OWNERSHIP")

    public fun parse(text: String): WorkCodeResult {
        val lines = text.lines().map { it.trim() }.withIndex()
            .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("```") }
            .toList()
        val header = lines.firstOrNull() ?: return WorkCodeResult.Error(1, "The code is empty")
        if (!header.value.equals(HEADER, ignoreCase = true)) {
            return WorkCodeResult.Error(header.index + 1, "A work code starts with \"$HEADER\"")
        }
        val fields = mutableMapOf<String, Pair<Int, String>>()
        for ((index, line) in lines.drop(1)) {
            val key = line.substringBefore(' ').uppercase()
            val value = line.substringAfter(' ', "").trim()
            if (key !in KNOWN) return WorkCodeResult.Error(index + 1, "Unknown line \"$key\"")
            if (key in fields) return WorkCodeResult.Error(index + 1, "$key appears twice")
            fields[key] = index + 1 to value
        }
        required.firstOrNull { it !in fields }?.let { return WorkCodeResult.Error(lines.last().index + 1, "Missing $it") }

        fun number(key: String, range: IntRange): Int? = fields[key]?.second?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in range }
        fun error(key: String, message: String) = WorkCodeResult.Error(fields[key]?.first ?: 1, message)

        val title = fields.getValue("TITLE").second
        if (title.isBlank() || title.length > MAX_TITLE) return error("TITLE", "TITLE must be 1 to $MAX_TITLE characters")
        val difficulty = number("DIFFICULTY", 1..MAX) ?: return error("DIFFICULTY", "DIFFICULTY must be 1 to $MAX")
        val quality = number("QUALITY", 0..MAX) ?: return error("QUALITY", "QUALITY must be 0 to $MAX")
        val complete = number("COMPLETE", 0..MAX) ?: return error("COMPLETE", "COMPLETE must be 0 to $MAX")
        val ownership = number("OWNERSHIP", 0..MAX) ?: return error("OWNERSHIP", "OWNERSHIP must be 0 to $MAX")
        val stat = fields["STAT"]?.second?.let { key ->
            Stat.entries.firstOrNull { it.key == key.lowercase() } ?: return error("STAT", "Unknown stat \"$key\"")
        } ?: Stat.TECHNICAL
        val hours = fields["HOURS"]?.second?.let { value ->
            value.removeSuffix("h").trim().toDoubleOrNull()?.takeIf { it > 0 && it <= 24 } ?: return error("HOURS", "HOURS must be a number from 0 to 24")
        }
        val judge = fields["JUDGE"]?.second?.let { WorkJudge.fromKey(it) ?: return error("JUDGE", "JUDGE must be claude or groq") } ?: WorkJudge.CLAUDE
        return WorkCodeResult.Ok(
            WorkReport(title, stat, difficulty, quality, complete, ownership, hours, judge, fields["NOTE"]?.second.orEmpty()),
        )
    }

    /** The code for [report], in the form [parse] reads. */
    public fun write(report: WorkReport): String = buildString {
        appendLine(HEADER)
        appendLine("TITLE ${report.title}")
        appendLine("STAT ${report.stat.key}")
        appendLine("DIFFICULTY ${report.difficulty}")
        appendLine("QUALITY ${report.quality}")
        appendLine("COMPLETE ${report.completeness}")
        appendLine("OWNERSHIP ${report.ownership}")
        report.hours?.let { appendLine("HOURS ${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()}") }
        appendLine("JUDGE ${report.judge.key}")
        if (report.note.isNotBlank()) appendLine("NOTE ${report.note}")
    }.trimEnd()

    /**
     * What makes two codes the same piece of work, so pasting one twice pays once: the
     * title and rubric, ignoring case and spacing.
     */
    public fun fingerprint(report: WorkReport): String =
        listOf(report.title.lowercase().replace(Regex("\\s+"), " "), report.stat.key, report.difficulty, report.quality, report.completeness, report.ownership, report.judge.key)
            .joinToString("|")

    private val KNOWN = setOf("TITLE", "STAT", "DIFFICULTY", "QUALITY", "COMPLETE", "OWNERSHIP", "HOURS", "JUDGE", "NOTE")
}

/** The instructions that make a Groq model a strict judge of work it can only read about. */
public object WorkPrompt {
    public fun system(): String = """
        You are a strict reviewer scoring something a person built, for a self-improvement game where XP must be hard to earn.
        You only have their description, so judge only what it shows; anything vague or unproven scores low.
        Score each from 0 to 5, where 5 is rare and means exceptional, and 3 is solid work:
        - DIFFICULTY (1-5): how hard the problem is. A tutorial-level script is 1; a shipped app feature with data and tests is 3-4.
        - QUALITY: is it correct, well built and tested, as described.
        - COMPLETE: shipped and usable (5) versus started or partly working (1-2).
        - OWNERSHIP: how much of the thinking and doing was the person's own rather than an AI's. If an AI wrote the code and the person only asked for it, 1. If they designed it, wrote or reviewed much of it and can explain it, 3-4.
        Reply with only this block, no other text:
        ${WorkCode.HEADER}
        TITLE <short name of the work>
        STAT technical
        DIFFICULTY <1-5>
        QUALITY <0-5>
        COMPLETE <0-5>
        OWNERSHIP <0-5>
        JUDGE groq
        NOTE <one sentence on the biggest reason for the scores>
    """.trimIndent()

    public fun user(description: String): String = "What I built:\n${description.trim()}"
}
