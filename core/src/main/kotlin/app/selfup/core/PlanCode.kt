package app.selfup.core

import java.time.DayOfWeek

/** One task line in a written plan: shown on its weekday, ticked by the goal's own measure. */
public data class CustomTask(val title: String, val detail: String = "")

/** A week of tasks by weekday. Days without tasks are rest days for the goal. */
public data class WeeklyPlan(val days: Map<DayOfWeek, List<CustomTask>>) {
    public fun on(day: DayOfWeek): List<CustomTask> = days[day].orEmpty()
}

/** A goal read from a plan code, before it's saved. */
public data class ImportedGoal(
    val measure: GoalMeasure,
    val target: Double,
    val weeks: Int,
    val plan: WeeklyPlan,
)

public sealed interface PlanCodeResult {
    public data class Ok(val goals: List<ImportedGoal>) : PlanCodeResult
    public data class Error(val line: Int, val message: String) : PlanCodeResult
}

/**
 * A plain-text plan format Claude can write in chat and you can paste into the app. It is
 * readable on purpose, so you can check or edit a plan before importing it:
 *
 * ```
 * SELFUP PLAN 1
 * GOAL record:bench:EST_1RM 80 12w
 * MON Bench 5×5 at 60 kg | Log the top set
 * THU Bench 3×3 at 67.5 kg
 * GOAL mind:n_back 3 8w
 * DAILY Mind session | N-back counts most
 * ```
 *
 * Day lines belong to the GOAL above them. `DAILY` means every day and `WEEKDAYS` Monday
 * to Friday. Text after `|` is the task's detail. Lines starting with `#` are ignored.
 */
public object PlanCode {
    public const val HEADER: String = "SELFUP PLAN 1"
    public const val MAX_TASK_LENGTH: Int = 80
    public const val MIN_WEEKS: Int = 1
    public const val MAX_WEEKS: Int = 52

    private val dayNames = mapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY,
    )
    private val groups = mapOf(
        "DAILY" to DayOfWeek.entries.toSet(),
        "WEEKDAYS" to DayOfWeek.entries.filter { it.value <= DayOfWeek.FRIDAY.value }.toSet(),
        "WEEKENDS" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    )

    public fun parse(text: String): PlanCodeResult {
        // Chat apps wrap code in fences and add stray spacing; tolerate both.
        val lines = text.lines().map { it.trim() }.withIndex()
            .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("```") }
        val first = lines.firstOrNull() ?: return PlanCodeResult.Error(1, "The plan is empty")
        if (!first.value.equals(HEADER, ignoreCase = true)) {
            return PlanCodeResult.Error(first.index + 1, "A plan starts with \"$HEADER\"")
        }

        val goals = mutableListOf<ImportedGoal>()
        var current: Triple<GoalMeasure, Double, Int>? = null
        var days = mutableMapOf<DayOfWeek, MutableList<CustomTask>>()

        fun close() {
            current?.let { (measure, target, weeks) -> goals += ImportedGoal(measure, target, weeks, WeeklyPlan(days.toMap())) }
            days = mutableMapOf()
        }

        for ((index, line) in lines.drop(1)) {
            val number = index + 1
            val word = line.substringBefore(' ').uppercase()
            val rest = line.substringAfter(' ', "").trim()
            if (word == "GOAL") {
                close()
                current = parseGoal(rest) ?: return PlanCodeResult.Error(number, "Can't read this goal: \"$rest\"")
                continue
            }
            val on = dayNames[word]?.let(::setOf) ?: groups[word]
                ?: return PlanCodeResult.Error(number, "Expected GOAL or a day such as MON, not \"$word\"")
            if (current == null) return PlanCodeResult.Error(number, "A day line needs a GOAL line above it")
            val title = rest.substringBefore('|').trim()
            val detail = rest.substringAfter('|', "").trim()
            if (title.isEmpty()) return PlanCodeResult.Error(number, "This task has no text")
            if (title.length > MAX_TASK_LENGTH) return PlanCodeResult.Error(number, "Keep tasks under $MAX_TASK_LENGTH characters")
            on.forEach { days.getOrPut(it) { mutableListOf() } += CustomTask(title, detail) }
        }
        close()
        if (goals.isEmpty()) return PlanCodeResult.Error(lines.last().index + 1, "The plan has no GOAL lines")
        goals.forEachIndexed { i, goal ->
            if (goal.plan.days.values.any { it.size > GoalRules.TASKS_PER_DAY }) {
                return PlanCodeResult.Error(0, "Goal ${i + 1} has more than ${GoalRules.TASKS_PER_DAY} tasks on one day")
            }
        }
        return PlanCodeResult.Ok(goals)
    }

    /** "record:bench:EST_1RM 80 12w" → measure, target, weeks. */
    private fun parseGoal(text: String): Triple<GoalMeasure, Double, Int>? {
        val parts = text.split(Regex("\\s+"))
        if (parts.size != 3) return null
        val measure = runCatching { GoalMeasure.fromKey(normalizeKey(parts[0])) }.getOrNull() ?: return null
        if (measure is GoalMeasure.Skill && runCatching { measure.pack }.isFailure) return null
        val target = parts[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val weeks = parts[2].lowercase().removeSuffix("w").toIntOrNull()?.takeIf { it in MIN_WEEKS..MAX_WEEKS } ?: return null
        return Triple(measure, target, weeks)
    }

    /** Lets a plan write the metric in lower case: record:bench:est_1rm. */
    private fun normalizeKey(key: String): String {
        val parts = key.split(':')
        return if (parts.firstOrNull() == "record" && parts.size == 3) "${parts[0]}:${parts[1]}:${parts[2].uppercase()}" else key
    }

    /** The day lines of one plan, for storing with its goal. Round-trips through [parseWeek]. */
    public fun encodeWeek(plan: WeeklyPlan): String = buildString {
        DayOfWeek.entries.forEach { day ->
            plan.on(day).forEach { task ->
                val name = dayNames.entries.first { it.value == day }.key
                append(name).append(' ').append(task.title)
                if (task.detail.isNotEmpty()) append(" | ").append(task.detail)
                append('\n')
            }
        }
    }

    public fun parseWeek(text: String): WeeklyPlan {
        val days = mutableMapOf<DayOfWeek, MutableList<CustomTask>>()
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            val day = dayNames[line.substringBefore(' ')] ?: return@forEach
            val rest = line.substringAfter(' ')
            days.getOrPut(day) { mutableListOf() } += CustomTask(rest.substringBefore(" | ").trim(), rest.substringAfter(" | ", "").trim())
        }
        return WeeklyPlan(days)
    }
}
