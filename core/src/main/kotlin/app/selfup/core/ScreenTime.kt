package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

public enum class UsageKind { RESUMED, PAUSED }

/** One event from Android's usage log: an app came to or left the foreground. */
public data class UsageEvent(val pkg: String, val at: Instant, val kind: UsageKind)

/**
 * Screen-time rules. The System reads the phone's own usage log, so scrolling and
 * "phone off" quests check themselves instead of relying on an honest tap.
 */
public object ScreenTime {
    /** Daily budget for entertainment apps together. Past it, the blocker locks them. */
    public const val DAILY_BUDGET_MINUTES: Int = 30

    /** Life XP for a day that stayed within the budget. A reward, never a penalty. */
    public const val UNDER_BUDGET_XP: Int = 10

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
        packages: Set<String> = scrollApps.keys,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Instant>()
        val seen = mutableSetOf<String>()
        fun add(pkg: String, from: Instant, to: Instant) {
            val ms = to.toEpochMilli() - from.toEpochMilli()
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0) + ms
        }
        events.asSequence()
            .filter { it.pkg in packages }
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

    public fun minutes(millis: Map<String, Long>): Int = (millis.values.sum() / 60_000).toInt()

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

    private val CUTOFF = Regex("""(?i)\b(phone off|phone down|in bed|no phone|screen off|screens off)\b.*?\bby\s+(\d{1,2})[:.](\d{2})""")
}
