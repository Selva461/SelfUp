package app.selfup.core

/**
 * Which app is really in front, followed through the usage log screen by screen.
 *
 * Reading only the newest event is not enough. A half swipe up to the recent apps starts
 * the launcher (logged as resumed) while the app underneath never pauses, and letting go
 * cancels the gesture: the launcher pauses and nothing new resumes. Following only the last
 * event leaves "nothing in front" while the app is still on screen, which is exactly how the
 * lock got dodged. Here every screen that resumed and has not paused yet is still showing,
 * and the newest of them is in front.
 */
public class ForegroundTracker(private val ignore: Set<String> = emptySet()) {
    /** Screens resumed and not paused yet, with the order they resumed in. */
    private val showing = LinkedHashMap<Pair<String, String?>, Long>()
    private var order = 0L

    /** The app in front, or null when nothing is (the screen is off or locked). */
    public val front: String?
        get() = showing.maxByOrNull { it.value }?.key?.first

    /** Applies [events] in log order and returns the app in front after them. */
    public fun apply(events: List<UsageEvent>): String? {
        for (event in events) {
            if (event.pkg in ignore) continue
            val screen = event.pkg to event.activity
            when (event.kind) {
                UsageKind.RESUMED -> showing[screen] = ++order
                UsageKind.PAUSED -> {
                    // Without a screen name, a pause ends every screen of that app.
                    if (event.activity == null) showing.keys.removeAll { it.first == event.pkg } else showing.remove(screen)
                }
            }
        }
        return front
    }

    /** Forgets everything, for when the screen goes off and every app pauses anyway. */
    public fun clear() {
        showing.clear()
    }
}
