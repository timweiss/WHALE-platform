package de.mimuc.senseeverything.sensor.implementation

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.sensor.AbstractSensor

class ActivityRecognitionSensor(val context: Context, database: AppDatabase) :
    AbstractSensor(context, database) {
    var receiverIntent: PendingIntent? = null
    val TAG = "ActivityRecognitionSensor"

    override fun isAvailable(context: Context?): Boolean {
        return true
    }

    override fun availableForPeriodicSampling(): Boolean {
        return false
    }

    override fun availableForContinuousSampling(): Boolean {
        return true
    }

    override fun getSensorName(): String? {
        return "Activity Recognition"
    }

    private val myBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ActivityTransitionResult.hasResult(intent)) {
                val result = ActivityTransitionResult.extractResult(intent)!!
                for (event in result.transitionEvents) {
                    onLogDataItem(
                        event.elapsedRealTimeNanos,
                        "Activity: ${getActivityTypeName(event.activityType)}, Transition: ${getActivityTransitionName(event.transitionType)}"
                    )
                }
            }
        }
    }

    override fun start(context: Context) {
        val request = ActivityTransitionRequest(getTransitions())

        receiverIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, myBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val task = ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, receiverIntent!!)

        task.addOnSuccessListener {
            m_IsRunning = true
            Log.d(TAG, "Activity transition updates requested successfully")
        }

        task.addOnFailureListener { e: Exception ->
            m_IsRunning = false
            Log.e(TAG, "Failed to request activity transition updates", e)
        }
    }

    override fun stop() {
        if (receiverIntent != null && m_IsRunning) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val task = ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(receiverIntent!!)

            task.addOnSuccessListener {
                m_IsRunning = false
                receiverIntent = null
                Log.d(TAG, "Activity transition updates removed successfully")
            }

            task.addOnFailureListener { e: Exception ->
                // Handle error
                Log.e(TAG, "Failed to remove activity transition updates", e)
            }
        }
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
}