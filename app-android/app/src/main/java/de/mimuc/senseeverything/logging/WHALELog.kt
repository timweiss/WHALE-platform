package de.mimuc.senseeverything.logging

import android.util.Log
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.service.SEApplicationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * WHALELog is a drop-in replacement for Android's Log class that logs to both Logcat
 * and the app's database for synchronization with the server.
 *
 * Usage:
 *   WHALELog.d("MyTag", "Debug message")
 *   WHALELog.i("MyTag", "Info message")
 *   WHALELog.w("MyTag", "Warning message")
 *   WHALELog.e("MyTag", "Error message")
 *   WHALELog.v("MyTag", "Verbose message")
 */
object WHALELog {
    private const val SENSOR_NAME = "Logging"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val DB_LOG_LEVEL = Log.INFO // Log level to store in DB

    /**
     * Send a DEBUG log message.
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        if (DB_LOG_LEVEL > Log.DEBUG) return
        saveToDatabase("DEBUG", tag, message, null)
    }

    /**
     * Send a DEBUG log message with a throwable.
     */
    fun d(tag: String, message: String, throwable: Throwable?) {
        Log.d(tag, message, throwable)
        if (DB_LOG_LEVEL > Log.DEBUG) return
        saveToDatabase("DEBUG", tag, message, throwable)
    }

    /**
     * Send an INFO log message.
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        if (DB_LOG_LEVEL > Log.INFO) return
        saveToDatabase("INFO", tag, message, null)
    }

    /**
     * Send an INFO log message with a throwable.
     */
    fun i(tag: String, message: String, throwable: Throwable?) {
        Log.i(tag, message, throwable)
        if (DB_LOG_LEVEL > Log.INFO) return
        saveToDatabase("INFO", tag, message, throwable)
    }

    /**
     * Send a WARN log message.
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        if (DB_LOG_LEVEL > Log.WARN) return
        saveToDatabase("WARN", tag, message, null)
    }

    /**
     * Send a WARN log message with a throwable.
     */
    fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
        if (DB_LOG_LEVEL > Log.WARN) return
        saveToDatabase("WARN", tag, message, throwable)
    }

    /**
     * Send an ERROR log message.
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
        if (DB_LOG_LEVEL > Log.ERROR) return
        saveToDatabase("ERROR", tag, message, null)
    }

    /**
     * Send an ERROR log message with a throwable.
     */
    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        if (DB_LOG_LEVEL > Log.ERROR) return
        saveToDatabase("ERROR", tag, message, throwable)
    }

    /**
     * Send a VERBOSE log message.
     */
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        if (DB_LOG_LEVEL > Log.VERBOSE) return
        saveToDatabase("VERBOSE", tag, message, null)
    }

    /**
     * Send a VERBOSE log message with a throwable.
     */
    fun v(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
        if (DB_LOG_LEVEL > Log.VERBOSE) return
        saveToDatabase("VERBOSE", tag, message, throwable)
    }

    /**
     * Save log entry to database asynchronously.
     */
    private fun saveToDatabase(level: String, tag: String, message: String, throwable: Throwable?) {
        scope.launch {
            try {
                val appController = SEApplicationController.getInstance()
                if (appController == null) {
                    Log.w("WHALELog", "SEApplicationController not initialized, skipping database log")
                    return@launch
                }

                val db = appController.getAppDatabase()
                val logDataDao = db.logDataDao()

                val jsonData = JSONObject().apply {
                    put("level", level)
                    put("tag", tag)
                    put("message", message)
                    if (throwable != null) {
                        put("exception", throwable.toString())
                        put("stackTrace", Log.getStackTraceString(throwable))
                    }
                }

                val logData = LogData(
                    System.currentTimeMillis(),
                    SENSOR_NAME,
                    jsonData.toString()
                )

                logDataDao.insertAll(logData)
            } catch (e: Exception) {
                // Fallback to Android Log if database operation fails
                Log.e("WHALELog", "Failed to save log to database", e)
            }
        }
    }
}