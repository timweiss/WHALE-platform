package de.mimuc.senseeverything.sensor.implementation

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.sensor.AbstractSensor

class ActivityRecognitionSensor(val context: Context, database: AppDatabase) :
    AbstractSensor(context, database) {
    private var receiverIntent: PendingIntent? = null
    private var receiverRegistered = false

    init {
        TAG = "ActivityRecognitionSensor"
        SENSOR_NAME = "Activity Recognition"
    }

    override fun isAvailable(context: Context?): Boolean {
        return true
    }

    override fun availableForPeriodicSampling(): Boolean {
        return false
    }

    override fun availableForContinuousSampling(): Boolean {
        return true
    }

    private val transitionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityTransitionResult.hasResult(intent)) return

            val result = ActivityTransitionResult.extractResult(intent) ?: return
            for (event in result.transitionEvents) {
                onLogDataItem(
                    wallClockMillis(event.elapsedRealTimeNanos),
                    "Activity: ${getActivityTypeName(event.activityType)}, Transition: ${getActivityTransitionName(event.transitionType)}"
                )
            }
        }
    }

    override fun start(context: Context) {
        super.start(context)

        // LogService starts continuous sensors again on every sampling cycle
        if (receiverRegistered) return

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            WHALELog.w(TAG, "ACTIVITY_RECOGNITION not granted")
            return
        }

        ContextCompat.registerReceiver(
            context,
            transitionReceiver,
            IntentFilter(ACTION_ACTIVITY_TRANSITION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        // Play services adds the transition result to the intent, so it has to be mutable, and
        // a mutable PendingIntent needs an explicit package
        val intent = Intent(ACTION_ACTIVITY_TRANSITION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        receiverIntent = pendingIntent

        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(getTransitions()), pendingIntent)
            .addOnSuccessListener {
                m_IsRunning = true
                WHALELog.i(TAG, "Activity transition updates requested successfully")
            }
            .addOnFailureListener { e: Exception ->
                m_IsRunning = false
                WHALELog.e(TAG, "Failed to request activity transition updates", e)
            }
    }

    override fun stop() {
        receiverIntent?.let { pendingIntent ->
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                ActivityRecognition.getClient(context)
                    .removeActivityTransitionUpdates(pendingIntent)
                    .addOnSuccessListener {
                        WHALELog.i(TAG, "Activity transition updates removed successfully")
                    }
                    .addOnFailureListener { e: Exception ->
                        WHALELog.e(TAG, "Failed to remove activity transition updates", e)
                    }
            }
            pendingIntent.cancel()
            receiverIntent = null
        }

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(transitionReceiver)
            } catch (e: IllegalArgumentException) {
                WHALELog.w(TAG, "transition receiver was not registered")
            }
            receiverRegistered = false
        }

        m_IsRunning = false
    }

    /** Transition events carry elapsed realtime, the other sensors log wall clock time. */
    private fun wallClockMillis(elapsedRealtimeNanos: Long): Long {
        val ageMillis = SystemClock.elapsedRealtime() - elapsedRealtimeNanos / 1_000_000
        return System.currentTimeMillis() - ageMillis
    }

    private fun getActivityTypeName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            else -> "Unknown Activity"
        }
    }

    private fun getActivityTransitionName(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "Enter"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "Exit"
            else -> "Unknown Transition"
        }
    }

    private fun getTransitions(): List<ActivityTransition> {
        val transitions = mutableListOf<ActivityTransition>()
        val activityTypes = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.WALKING,
            DetectedActivity.STILL,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE
        )

        activityTypes.forEach { activityType ->
            transitions += ActivityTransition.Builder()
                .setActivityType(activityType)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
            transitions += ActivityTransition.Builder()
                .setActivityType(activityType)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        }
        return transitions
    }

    companion object {
        private const val ACTION_ACTIVITY_TRANSITION = "de.mimuc.senseeverything.action.ACTIVITY_TRANSITION"
        private const val REQUEST_CODE = 7001
    }
}
