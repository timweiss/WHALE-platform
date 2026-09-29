package de.mimuc.senseeverything.service.accessibility

/**
 * Orders tree captures and interactions on the single capture worker.
 *
 * At most one capture waits in the queue; further triggers join it, since it reads the live tree
 * when it runs. An interaction must be matched against the screen as it was before the interaction,
 * so a capture that is still waiting when an interaction arrives is moved behind it: otherwise it
 * would read the tree after the interaction (e.g. the screen a tap opened) and the interaction would
 * be matched against that screen.
 *
 * A capture is stamped with the latest triggering event. A capture that was moved behind an
 * interaction can only show the state after it, so it is stamped no earlier than the interaction.
 *
 * [requestCapture] and [postInteraction] are called on the main thread, the posted tasks run on the
 * worker.
 */
class CaptureScheduler(
    private val post: (Runnable) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val capture: (eventTime: Long) -> Unit
) {
    private val lock = Any()
    // the capture that is queued but has not started yet
    private var queued: Runnable? = null
    // wall-clock time the queued capture is stamped with
    private var triggerTime = 0L

    /** @return false if the request joined the capture that is already queued */
    fun requestCapture(eventTime: Long): Boolean = synchronized(lock) {
        triggerTime = eventTime
        if (queued != null) return false
        postCapture()
        true
    }

    /** @return true if a queued capture was moved behind [task] */
    fun postInteraction(eventTime: Long, task: Runnable): Boolean = synchronized(lock) {
        val waiting = queued
        if (waiting == null) {
            post(task)
            return false
        }
        remove(waiting)
        post(task)
        triggerTime = maxOf(triggerTime, eventTime)
        postCapture()
        true
    }

    private fun postCapture() {
        val runnable = object : Runnable {
            override fun run() {
                val eventTime = synchronized(lock) {
                    // replaced after the worker had already taken it from the queue
                    if (queued !== this) return
                    queued = null
                    triggerTime
                }
                capture(eventTime)
            }
        }
        queued = runnable
        post(runnable)
    }
}
