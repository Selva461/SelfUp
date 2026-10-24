package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

public enum class UsageKind { RESUMED, PAUSED }

/**
 * One event from Android's usage log: an app came to or left the foreground. [activity] is
 * the screen inside the app, when the log names it; an app moving between its own screens
 * logs one screen's pause and the next one's resume, in either order.
 */
public data class UsageEvent(val pkg: String, val at: Instant, val kind: UsageKind, val activity: String? = null)

/**
 * Screen-time rules. The System reads the phone's own usage log, so scrolling and
 * "phone off" quests check themselves instead of relying on an honest tap.
 */
public object ScreenTime {
    /** Daily budget for entertainment apps together. Past it, the blocker locks them. */
    public const val DAILY_BUDGET_MINUTES: Int = 30

    /**
     * Life XP for a day's entertainment use, scored by [budgetPerformance]: full only at
     * [FREE_MINUTES] or less, falling away towards the budget, and a loss past about
     * two-thirds of it.
     */
    public const val UNDER_BUDGET_XP: Int = 10

    /** Entertainment minutes that still count as a clean day: a reply in a chat that opened a feed. */
    public const val FREE_MINUTES: Int = 5

    /** A day's entertainment use against the budget, 0 to 1. */
    public fun budgetPerformance(usedMinutes: Int, budget: Int = DAILY_BUDGET_MINUTES): Double {
        if (usedMinutes <= FREE_MINUTES) return 1.0
        return ((budget - usedMinutes).toDouble() / (budget - FREE_MINUTES)).coerceIn(0.0, 1.0)
    }

    /** Each app opened after a cutoff takes this much off a "Phone off" quest. */
    public const val OPEN_COST: Double = 0.15

    /** Minutes of use after a cutoff that wipe out a "Phone off" quest entirely. */
    public const val LATE_MINUTES_TO_FAIL: Double = 30.0

    /** Feed, video and reading apps, by package. Games are added on the phone by app category. */
    public val scrollApps: Map<String, String> = linkedMapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "X",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit",
        "in.mohalla.sharechat" to "ShareChat",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.netflix.mediaclient" to "Netflix",
        "in.startv.hotstar" to "Hotstar",
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
        "com.mxtech.videoplayer.ad" to "MX Player",
        "com.qidian.Int.reader" to "Webnovel",
    )

    /**
     * Foreground time per package within [start, end), from [events] in that window. An app
     * already open at [start] (its first event is a pause) counts from [start]; one still
     * open at [end] counts until [end].
     */
    public fun foregroundMillis(
        events: List<UsageEvent>,
        start: Instant,
        end: Instant,
        packages: Set<String>? = scrollApps.keys,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Instant>()
        val seen = mutableSetOf<String>()
        fun add(pkg: String, from: Instant, to: Instant) {
            val ms = to.toEpochMilli() - from.toEpochMilli()
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0) + ms
        }
        events.asSequence()
            .filter { packages == null || it.pkg in packages }
            .filter { !it.at.isBefore(start) && it.at.isBefore(end) }
            .sortedBy { it.at }
            .forEach { event ->
                when (event.kind) {
                    UsageKind.RESUMED -> openSince.putIfAbsent(event.pkg, event.at)
                    UsageKind.PAUSED -> {
                        val from = openSince.remove(event.pkg) ?: if (event.pkg !in seen) start else null
                        if (from != null) add(event.pkg, from, event.at)
                    }
                }
                seen += event.pkg
            }
        openSince.forEach { (pkg, from) -> add(pkg, from, end) }
        return totals
    }

    /**
     * The full picture of one quest day for the detailed log: time and opens for every app
     * (not only entertainment), first and last use, and how many opens came after [lateFrom].
     */
    public fun summarize(events: List<UsageEvent>, start: Instant, end: Instant, lateFrom: Instant, entertainment: Set<String>): UsageDay {
        val inWindow = events.filter { !it.at.isBefore(start) && it.at.isBefore(end) }.sortedBy { it.at }
        val millis = foregroundMillis(inWindow, start, end, packages = null)
        val opens = mutableMapOf<String, Int>()
        val open = mutableSetOf<String>()
        var late = 0
        inWindow.forEach { event ->
            when (event.kind) {
                UsageKind.RESUMED -> if (open.add(event.pkg)) {
                    opens[event.pkg] = (opens[event.pkg] ?: 0) + 1
                    if (!event.at.isBefore(lateFrom)) late++
                }
                UsageKind.PAUSED -> open.remove(event.pkg)
            }
        }
        val apps = (millis.keys + opens.keys).associateWith { pkg ->
            AppUse(millis[pkg] ?: 0, opens[pkg] ?: 0, pkg in entertainment)
        }
        val resumes = inWindow.filter { it.kind == UsageKind.RESUMED }
        return UsageDay(
            apps = apps,
            firstUse = resumes.firstOrNull()?.at,
            lastUse = inWindow.lastOrNull()?.at,
            lateOpens = late,
        )
    }

    /** Opens after this time count as late-night use. */
    public val LATE_FROM: LocalTime = LocalTime.of(23, 0)

    /**
     * The app in front after [events], read in log order from [current]. A pause of the app
     * in front (screen off, or the lock screen) leaves nothing in front, so this returns null.
     * Packages in [ignore], such as the notification shade or keyboard, pass over an app
     * without leaving it.
     */
    public fun frontApp(events: List<UsageEvent>, current: String?, ignore: Set<String> = emptySet()): String? {
        var front = current
        for (event in events) {
            if (event.pkg in ignore) continue
            when (event.kind) {
                UsageKind.RESUMED -> front = event.pkg
                UsageKind.PAUSED -> if (event.pkg == front) front = null
            }
        }
        return front
    }

    public fun minutes(millis: Map<String, Long>): Int = (millis.values.sum() / 60_000).toInt()

    /** The popup warns once a day when this many minutes or fewer are left. */
    public const val WARN_AT_MINUTES_LEFT: Int = 5

    /** How long a one-off grace unlock lasts once the budget is spent. One per day. */
    public const val GRACE_MINUTES: Int = 5

    /**
     * Whether opening an entertainment app should be blocked now: the budget is spent and
     * no grace unlock is running.
     */
    public fun shouldBlock(usedMinutes: Int, now: Instant, graceUntil: Instant?, budget: Int = DAILY_BUDGET_MINUTES): Boolean =
        usedMinutes >= budget && (graceUntil == null || !now.isBefore(graceUntil))

    /**
     * The cutoff named by a quest such as "Phone off by 23:00" or "In bed by 00:00",
     * or null when the title isn't one the usage log can check.
     */
    public fun cutoff(title: String): LocalTime? {
        val match = CUTOFF.find(title) ?: return null
        val hour = match.groupValues[2].toInt()
        val minute = match.groupValues[3].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Whether the phone stayed unused from [cutoff] on [day] until that quest day ended.
     * Opening any app in that window fails it, unlocking included (it brings up the
     * launcher). A screen that only lights up for a notification doesn't count.
     */
    public fun stayedOff(events: List<UsageEvent>, day: LocalDate, cutoff: LocalTime, boundary: DayBoundary): Boolean {
        val from = ReminderPlanner.instantOn(day, cutoff, boundary)
        val to = boundary.startOf(day.plusDays(1))
        return events.none { it.kind == UsageKind.RESUMED && !it.at.isBefore(from) && it.at.isBefore(to) }
    }

    /**
     * How well the phone stayed down from [cutoff] on [day] until that quest day ended, 0
     * to 1: untouched is perfect; each app opened costs [OPEN_COST] and every minute of use
     * a share of [LATE_MINUTES_TO_FAIL].
     */
    public fun offPerformance(events: List<UsageEvent>, day: LocalDate, cutoff: LocalTime, boundary: DayBoundary): OffResult {
        val from = ReminderPlanner.instantOn(day, cutoff, boundary)
        val to = boundary.startOf(day.plusDays(1))
        val inWindow = events.filter { !it.at.isBefore(from) && it.at.isBefore(to) }
        val opens = inWindow.count { it.kind == UsageKind.RESUMED }
        val minutes = foregroundMillis(inWindow, from, to, packages = null).values.sum() / 60_000.0
        val performance = if (opens == 0) 1.0 else (1 - OPEN_COST * opens - minutes / LATE_MINUTES_TO_FAIL).coerceIn(0.0, 1.0)
        return OffResult(opens, minutes, performance)
    }

    private val CUTOFF = Regex("""(?i)\b(phone off|phone down|in bed|no phone|screen off|screens off)\b.*?\bby\s+(\d{1,2})[:.](\d{2})""")
}

/** What happened after a "Phone off by" cutoff. */
public data class OffResult(val opens: Int, val minutes: Double, val performance: Double)

public data class AppUse(val foregroundMillis: Long, val opens: Int, val entertainment: Boolean)

public data class UsageDay(
    val apps: Map<String, AppUse>,
    val firstUse: Instant?,
    val lastUse: Instant?,
    val lateOpens: Int,
) {
    val totalMillis: Long get() = apps.values.sumOf { it.foregroundMillis }
    val entertainmentMillis: Long get() = apps.values.filter { it.entertainment }.sumOf { it.foregroundMillis }
    val opens: Int get() = apps.values.sumOf { it.opens }
}
