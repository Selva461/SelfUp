package app.selfup.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForegroundTest {
    private val novel = "com.qidian.Int.reader"
    private val home = "com.google.android.apps.nexuslauncher"
    private val shade = "com.android.systemui"
    private var t = Instant.parse("2026-10-24T08:00:00Z")

    private fun on(pkg: String, activity: String? = null) = UsageEvent(pkg, t.also { t = t.plusMillis(100) }, UsageKind.RESUMED, activity)
    private fun off(pkg: String, activity: String? = null) = UsageEvent(pkg, t.also { t = t.plusMillis(100) }, UsageKind.PAUSED, activity)

    @Test
    fun `a cancelled half swipe to recents leaves the app in front`() {
        val tracker = ForegroundTracker()
        assertEquals(novel, tracker.apply(listOf(on(novel, "Reader"))))
        // The gesture starts the launcher; the app underneath never pauses.
        assertEquals(home, tracker.apply(listOf(on(home, "Launcher"))))
        // Letting go cancels it: the launcher pauses and nothing new resumes.
        assertEquals(novel, tracker.apply(listOf(off(home, "Launcher"))))
    }

    @Test
    fun `a real switch moves the front and the screen going off clears it`() {
        val tracker = ForegroundTracker()
        tracker.apply(listOf(on(novel, "Reader")))
        assertEquals(home, tracker.apply(listOf(off(novel, "Reader"), on(home, "Launcher"))))
        assertNull(tracker.apply(listOf(off(home, "Launcher"))))
    }

    @Test
    fun `moving between screens of one app keeps it in front in either order`() {
        val tracker = ForegroundTracker()
        tracker.apply(listOf(on(novel, "Library")))
        assertEquals(novel, tracker.apply(listOf(on(novel, "Reader"), off(novel, "Library"))))
        assertEquals(novel, tracker.apply(listOf(off(novel, "Reader"), on(novel, "Library"))))
    }

    @Test
    fun `ignored packages pass over and an unnamed pause ends every screen of the app`() {
        val tracker = ForegroundTracker(ignore = setOf(shade))
        tracker.apply(listOf(on(novel, "Reader"), on(novel, "Comments")))
        assertEquals(novel, tracker.apply(listOf(on(shade), off(shade))))
        assertNull(tracker.apply(listOf(off(novel))))
        tracker.apply(listOf(on(novel)))
        tracker.clear()
        assertNull(tracker.front)
    }
}
