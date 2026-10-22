package app.selfup.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import app.selfup.core.ScreenTime
import app.selfup.core.UsageEvent
import app.selfup.core.UsageKind
import java.time.Instant

/** Where screen-time events come from, so tests can supply their own. */
interface UsageSource {
    /** Whether the user granted usage access. Nothing is read without it. */
    fun hasAccess(): Boolean

    fun events(start: Instant, end: Instant): List<UsageEvent>

    /** Packages that count toward the entertainment budget and get locked past it. */
    fun entertainment(): Set<String> = ScreenTime.scrollApps.keys
}

/**
 * Reads Android's own usage log. This needs the special "usage access" grant, which the
 * user gives in system settings; it is read-only and nothing leaves the phone.
 */
class UsageReader(private val context: Context) : UsageSource {
    private val usage get() = context.getSystemService(UsageStatsManager::class.java)

    override fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun events(start: Instant, end: Instant): List<UsageEvent> {
        if (!hasAccess()) return emptyList()
        val stats = usage ?: return emptyList()
        val log = stats.queryEvents(start.toEpochMilli(), end.toEpochMilli()) ?: return emptyList()
        val event = UsageEvents.Event()
        val out = mutableListOf<UsageEvent>()
        while (log.getNextEvent(event)) {
            val kind = when (event.eventType) {
                // ACTIVITY_RESUMED and ACTIVITY_PAUSED (API 29) share these values with the older names.
                @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageKind.RESUMED
                @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageKind.PAUSED
                else -> null
            } ?: continue
            out += UsageEvent(event.packageName, Instant.ofEpochMilli(event.timeStamp), kind)
        }
        return out
    }

    /** The known feed and video apps, plus every installed app that declares itself a game. */
    override fun entertainment(): Set<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        val games = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }
                .filter { it.category == ApplicationInfo.CATEGORY_GAME }
                .map { it.packageName }
        }.getOrDefault(emptyList())
        return ScreenTime.scrollApps.keys + games
    }

    companion object {
        fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
