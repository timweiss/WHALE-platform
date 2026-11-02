package de.mimuc.senseeverything.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Display
import de.mimuc.senseeverything.logging.WHALELog
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Takes a screenshot of a single accessibility window and writes it as a downscaled JPEG.
 *
 * On Android 14+ only the given window is captured, not the display: no status bar, no notifications,
 * no keyboard (the IME is its own window). Pass the window the UI tree was read from
 * ([android.view.accessibility.AccessibilityNodeInfo.getWindowId]), so screenshot and tree show the
 * same window. Older versions can't capture a single window, there the whole default display is
 * captured, including status bar, notifications and keyboard.
 *
 * Decoding and encoding run on a dedicated background thread, so they never delay tree captures; the
 * full-size pixels only exist while one image is being written. One capture runs at a time, the
 * framework rate-limits screenshots anyway.
 *
 * @param resultHandler the thread [capture] reports on, so callers can keep their state on one thread
 */
class ScreenshotCapturer(
    private val service: AccessibilityService,
    private val resultHandler: Handler
) {
    companion object {
        const val TAG = "ScreenshotCapturer"

        /**
         * Enough to recognise the screen when rating it, a quarter of the pixels of a 1440p screen.
         * Limits the short side, so portrait and landscape captures keep the same resolution.
         */
        private const val MAX_SHORT_SIDE = 720
        private const val JPEG_QUALITY = 80
    }

    sealed interface Outcome {
        /** the image was written to the file passed to [capture] */
        object Saved : Outcome

        /** nothing was written (a partial file is deleted) */
        data class Failed(val reason: String) : Outcome
    }

    private val thread = HandlerThread("UITreeScreenshot", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val inFlight = AtomicBoolean(false)
    private val stats = ScreenshotStats()

    /** Captures [windowId] into [file]; [onResult] is always called, on [resultHandler]. */
    fun capture(windowId: Int, file: File, onResult: (Outcome) -> Unit) {
        stats.requested.incrementAndGet()
        val requestedAt = SystemClock.elapsedRealtime()
        if (!inFlight.compareAndSet(false, true)) {
            finish(Outcome.Failed("busy"), onResult)
            return
        }

        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val cpuStart = SystemClock.currentThreadTimeMillis()
                val outcome = try {
                    write(screenshot, file)
                    stats.cpuMsTotal.addAndGet(SystemClock.currentThreadTimeMillis() - cpuStart)
                    stats.recordTime(SystemClock.elapsedRealtime() - requestedAt)
                    Outcome.Saved
                } catch (e: Exception) {
                    file.delete()
                    Outcome.Failed("write: ${e.message}")
                } finally {
                    screenshot.hardwareBuffer.close()
                }
                inFlight.set(false)
                finish(outcome, onResult)
            }

            override fun onFailure(errorCode: Int) {
                inFlight.set(false)
                finish(Outcome.Failed(errorName(errorCode)), onResult)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.takeScreenshotOfWindow(windowId, executor, callback)
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }
        } catch (e: Exception) {
            inFlight.set(false)
            finish(Outcome.Failed("request: ${e.message}"), onResult)
        }
    }

    private fun write(screenshot: AccessibilityService.ScreenshotResult, file: File) {
        // a hardware bitmap lives in graphics memory and can't be scaled or encoded, copy it out once
        val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            ?: throw IOException("could not wrap hardware buffer")
        val full = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        full ?: throw IOException("could not copy hardware bitmap")
        stats.bitmapKbMax.accumulateAndGet(full.allocationByteCount / 1024L) { a, b -> maxOf(a, b) }

        val shortSide = minOf(full.width, full.height)
        val scaled = if (shortSide > MAX_SHORT_SIDE) {
            val scale = MAX_SHORT_SIDE.toFloat() / shortSide
            val width = (full.width * scale).roundToInt()
            val height = (full.height * scale).roundToInt()
            Bitmap.createScaledBitmap(full, width, height, true).also { full.recycle() }
        } else {
            full
        }

        try {
            val written = file.outputStream().buffered().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!written) throw IOException("could not encode")
            stats.kbWritten.addAndGet(file.length() / 1024)
        } finally {
            scaled.recycle()
        }
    }

    private fun finish(outcome: Outcome, onResult: (Outcome) -> Unit) {
        when (outcome) {
            is Outcome.Saved -> stats.saved.incrementAndGet()
            is Outcome.Failed -> {
                stats.failed.incrementAndGet()
                WHALELog.w(TAG, "Screenshot failed: ${outcome.reason}")
            }
        }
        resultHandler.post { onResult(outcome) }
    }

    private fun errorName(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "internal error"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "no accessibility access"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "interval too short"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid display"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> "invalid window"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "secure window"
        else -> "error $errorCode"
    }

    /** Counters since the last call, in the format of the UITreeConsumer stats log. */
    fun drainStats(): String = stats.drain()

    /** A screenshot still being taken is dropped; its file is removed when the store next loads. */
    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    /**
     * Counters for the periodic stats log, reset on every report.
     *
     * msTotal/msMax are wall time from the request to the written file (including the wait for the
     * framework), cpuMsTotal the screenshot thread's CPU time for copying, scaling and encoding.
     * bitmapKbMax is the largest full-size copy, the transient native memory one capture needs.
     */
    private class ScreenshotStats {
        val requested = AtomicLong()
        val saved = AtomicLong()
        val failed = AtomicLong()
        val msTotal = AtomicLong()
        val msMax = AtomicLong()
        val cpuMsTotal = AtomicLong()
        val bitmapKbMax = AtomicLong()
        val kbWritten = AtomicLong()

        fun recordTime(ms: Long) {
            msTotal.addAndGet(ms)
            msMax.accumulateAndGet(ms) { a, b -> maxOf(a, b) }
        }

        fun drain(): String =
            "screenshotsRequested=${requested.getAndSet(0)}" +
                " screenshotsSaved=${saved.getAndSet(0)}" +
                " screenshotsFailed=${failed.getAndSet(0)}" +
                " screenshotMsTotal=${msTotal.getAndSet(0)}" +
                " screenshotMsMax=${msMax.getAndSet(0)}" +
                " screenshotCpuMsTotal=${cpuMsTotal.getAndSet(0)}" +
                " screenshotBitmapKbMax=${bitmapKbMax.getAndSet(0)}" +
                " screenshotKbWritten=${kbWritten.getAndSet(0)}"
    }
}
