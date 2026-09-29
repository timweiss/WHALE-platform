package de.mimuc.senseeverything.service.accessibility

/**
 * Collapses a continuous scroll gesture into a single SCROLL interaction.
 *
 * A scroll event is suppressed if the previous scroll event came from the same window, package and
 * class less than [windowMs] ago. The window slides: every scroll event (recorded or suppressed)
 * refreshes the timestamp, so a gesture only produces a new record after a pause of at least
 * [windowMs]. A tap in between ends the gesture, see [reset].
 *
 * Scrollable views of the same class in the same window (e.g. a horizontal carousel inside a feed)
 * cannot be told apart without a binder call, so switching between them quickly counts as one gesture.
 *
 * Only reads fields that are cheap to access on the main thread (no binder calls).
 */
class ScrollCoalescer(
    private val windowMs: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastPackage: CharSequence? = null
    private var lastClass: CharSequence? = null
    private var lastWindowId = -1
    private var lastTime = Long.MIN_VALUE

    /** @return true if the scroll event should be recorded, false if it belongs to the ongoing gesture. */
    fun shouldRecord(packageName: CharSequence?, className: CharSequence?, windowId: Int): Boolean {
        val now = clock()
        val sameTarget = windowId == lastWindowId &&
                packageName?.toString() == lastPackage?.toString() &&
                className?.toString() == lastClass?.toString()
        val withinWindow = lastTime != Long.MIN_VALUE && now - lastTime < windowMs

        lastPackage = packageName
        lastClass = className
        lastWindowId = windowId
        lastTime = now

        return !(sameTarget && withinWindow)
    }

    /** Ends the ongoing gesture, e.g. after a tap; the next scroll event is recorded. */
    fun reset() {
        lastTime = Long.MIN_VALUE
    }
}
