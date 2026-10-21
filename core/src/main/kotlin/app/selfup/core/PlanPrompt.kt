package app.selfup.core

import app.selfup.core.packs.Packs

/**
 * The instructions an AI model gets to write a plan code. The model only ever produces text
 * in the [PlanCode] format, which the app checks before anything is saved, so a bad answer
 * costs nothing.
 */
public object PlanPrompt {
    /** Every measure key a plan may use, with what it means. */
    public fun measures(): List<Pair<String, String>> = buildList {
        RecordKind.entries.forEach { kind ->
            kind.metrics.forEach { metric ->
                add(GoalMeasure.Record(kind, metric).key to "${kind.displayName}: ${metric.displayName} in ${metric.unit}")
            }
        }
        Packs.all.forEach { pack ->
            add(GoalMeasure.Skill(pack.id).key to "${pack.name}: number of concepts at Solid mastery (out of ${pack.concepts.size})")
        }
        MindGame.entries.forEach { game ->
            val direction = if (game.better == Metric.Better.LOWER) ", lower is better" else ""
            add(GoalMeasure.Mind(game).key to "${game.displayName} (${game.trains}) in ${game.unit}$direction")
        }
    }

    public fun system(): String = """
        You write training plans for SelfUp, a self-improvement app. Reply with a plan code and nothing else.

        Format, exactly:
        ${PlanCode.HEADER}
        GOAL <measure> <target number> <weeks>w
        <DAY> <task, under ${PlanCode.MAX_TASK_LENGTH} characters> | <short detail>

        DAY is MON, TUE, WED, THU, FRI, SAT, SUN, DAILY, WEEKDAYS or WEEKENDS. Day lines belong to the GOAL above them.

        Measures (use these keys exactly):
        ${measures().joinToString("\n        ") { (key, meaning) -> "$key = $meaning" }}

        Rules:
        - At most ${GoalRules.MAX_ACTIVE} goals. If asked for more, pick the ${GoalRules.MAX_ACTIVE} that matter most.
        - At most ${GoalRules.TASKS_PER_DAY} tasks per goal on any day, counting DAILY and WEEKDAYS lines. Leave rest days.
        - Weeks between ${PlanCode.MIN_WEEKS} and ${PlanCode.MAX_WEEKS}. Targets must be realistic for the weeks given and the current numbers.
        - Tasks are concrete: sets, reps, weights in kg, distances in km, minutes. Weights in steps of 2.5 kg.
        - The person works weekdays at an office, commutes by bus around 07:30 and 18:30, and trains at the gym at about 20:50. Saturday is free for longer sessions.
        - The app already adds a baseline test and a retest every 4th week; don't add your own tests.
        - Running volume should grow by no more than about 10% a week.
        - Never diagnose anything and never recommend supplements or medication.
        - No markdown, no explanation, no code fences. Only the plan code.
    """.trimIndent()

    public fun user(request: String, currentNumbers: List<String>): String = buildString {
        appendLine("What I want: ${request.trim()}")
        if (currentNumbers.isNotEmpty()) {
            appendLine()
            appendLine("My current numbers:")
            currentNumbers.forEach { appendLine("- $it") }
        }
    }.trim()

    /** Asks the model to fix its last answer, quoting what was wrong. */
    public fun repair(error: PlanCodeResult.Error): String {
        val where = if (error.line > 0) "Line ${error.line}: " else ""
        return "That plan code didn't parse. $where${error.message}. Send the corrected plan code only."
    }

    /** The plan code inside a model's reply, dropping any chatter before or after it. */
    public fun extract(reply: String): String {
        val lines = reply.lines()
        val start = lines.indexOfFirst { it.trim().equals(PlanCode.HEADER, ignoreCase = true) }
        if (start < 0) return reply.trim()
        val words = setOf("GOAL", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", "DAILY", "WEEKDAYS", "WEEKENDS")
        val body = lines.drop(start + 1).takeWhile { line ->
            val t = line.trim()
            t.isEmpty() || t.startsWith("#") || t.substringBefore(' ').uppercase() in words
        }
        return (listOf(PlanCode.HEADER) + body).joinToString("\n").trim()
    }
}
