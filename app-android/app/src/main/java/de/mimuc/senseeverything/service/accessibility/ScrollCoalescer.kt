package de.mimuc.senseeverything.service.accessibility

/**
 * Collapses a continuous scroll gesture into a single SCROLL interaction.
 *
 * A scroll event is suppressed if the previous scroll event came from the same package and class
 * less than [windowMs] ago. The window slides: every scroll event (recorded or suppressed) refreshes
 * the timestamp, so a gesture only produces a new record after a pause of at least [windowMs].
 *
 * Only reads fields that are cheap to access on the main thread (no binder calls).
 */
class ScrollCoalescer(
    private val windowMs: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastPackage: CharSequence? = null
    private var lastClass: CharSequence? = null
    private var lastTime = Long.MIN_VALUE

    /** @return true if the scroll event should be recorded, false if it belongs to the ongoing gesture. */
    fun shouldRecord(packageName: CharSequence?, className: CharSequence?): Boolean {
        val now = clock()
        val sameTarget = packageName?.toString() == lastPackage?.toString() &&
                className?.toString() == lastClass?.toString()
        val withinWindow = lastTime != Long.MIN_VALUE && now - lastTime < windowMs

        lastPackage = packageName
        lastClass = className
        lastTime = now

        return !(sameTarget && withinWindow)
    }
}
