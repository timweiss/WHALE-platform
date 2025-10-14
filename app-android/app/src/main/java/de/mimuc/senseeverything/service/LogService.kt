package de.mimuc.senseeverything.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.permissions.PermissionNotificationHelper
import de.mimuc.senseeverything.sensor.AbstractSensor
import de.mimuc.senseeverything.sensor.SingletonSensorList
import de.mimuc.senseeverything.service.floatingWidget.NotificationTriggerFloatingWidgetService
import de.mimuc.senseeverything.service.healthcheck.HealthcheckResult
import de.mimuc.senseeverything.service.healthcheck.ServiceHealthcheck.checkServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

internal enum class LogServiceState {
    IDLE,
    SAMPLING_AFTER_UNLOCK,
    SAMPLING_PERIODIC
}

@AndroidEntryPoint
class LogService : AbstractService() {
    private var sensorList: MutableList<AbstractSensor>? = null
    private var mMessenger: Messenger? = null
    private var lockUnlockReceiver: BroadcastReceiver? = null

    private var state = LogServiceState.IDLE
    private var isPeriodicSamplingEnabled = false

    @Inject
    lateinit var singletonSensorList: SingletonSensorList

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var permissionNotificationHelper: PermissionNotificationHelper

    override fun onCreate() {
        TAG = javaClass.name
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ret = super.onStartCommand(intent, flags, startId)
        WHALELog.i(TAG, "onStartCommand called")

        // fixme: the service might be restarted by the system, so we need to automatically start sensing
        if (runHealthcheck(this).allCriticalPermissionsGranted) {
            listenForLockUnlock()
            setupPeriodicSampling()
            setupContiunousSampling()
        } else {
            WHALELog.e(TAG, "Not all critical permissions granted, not starting sampling/stopping service")
            stopSelf()
        }

        return ret
    }

    override fun onDestroy() {
        // clear handlers
        periodicHandler.removeCallbacks(periodicStartRunnable)
        periodicHandler.removeCallbacks(periodicStopRunnable)
        lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable)

        stopSensors(true)

        if (lockUnlockReceiver != null) {
            try {
                unregisterReceiver(lockUnlockReceiver)
                WHALELog.i(TAG, "lockUnlockReceiver unregistered successfully")
            } catch (e: IllegalArgumentException) {
                WHALELog.w(TAG, "lockUnlockReceiver was not registered")
            }
        }

        super.onDestroy()
    }

    internal class IncomingHandler(context: Context, service: LogService?) : Handler() {
        private val serviceReference = WeakReference<LogService?>(service)
        private val applicationContext = context.applicationContext

        private val TAG = "LogServiceMessageHandler"

        override fun handleMessage(msg: Message) {
            val service = serviceReference.get()
            if (service != null) {
                WHALELog.i(TAG, "message: " + msg.what)
                when (msg.what) {
                    START_SENSORS -> {
                        service.startSensors(onlyPeriodic = false, includeContinous = true)
                    }

                    STOP_SENSORS -> {
                        service.stopSampling()
                    }

                    LISTEN_LOCK_UNLOCK -> {
                        service.listenForLockUnlock()
                    }

                    LISTEN_LOCK_UNLOCK_AND_PERIODIC -> {
                        service.listenForLockUnlock()
                        service.setupPeriodicSampling()
                        service.setupContiunousSampling()
                    }

                    else -> super.handleMessage(msg)
                }
            } else {
                WHALELog.e(TAG, "Service has unexpectedly died")
            }
        }
    }

    /* Section: Sampling */
    var lockUnlockStopHandler: Handler = Handler()
    var lockUnlockStopRunnable: Runnable = Runnable {
        WHALELog.i(TAG, "lockUnlockStopRunnable: stop sampling")
        setState(LogServiceState.IDLE)
    }

    private fun listenForLockUnlock() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_USER_PRESENT)
        filter.addAction(Intent.ACTION_SCREEN_OFF)

        lockUnlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    WHALELog.i(TAG, "lockUnlockReceiver: device locked")
                    hideInteractionWidget()
                } else {
                    WHALELog.i(TAG,
                        "lockUnlockReceiver: device unlocked, sensorlist: $singletonSensorList"
                    )
                    showInteractionWidget()
                    setState(LogServiceState.SAMPLING_AFTER_UNLOCK)

                    // Run healthcheck on unlock
                    runHealthcheck(context)
                }
            }
        }
        initializeSensors()
        registerReceiver(lockUnlockReceiver, filter)
        WHALELog.i(TAG, "registered lock/unlock receiver")
    }

    private fun stopListeningForLockUnlock() {
        unregisterReceiver(lockUnlockReceiver)
        WHALELog.i(TAG, "unregistered lock/unlock receiver")
    }

    var periodicHandler: Handler = Handler()
    var periodicStartRunnable: Runnable = Runnable {
        WHALELog.i(TAG, "periodicStartRunnable: start sampling")
        setState(LogServiceState.SAMPLING_PERIODIC)
    }

    var periodicStopRunnable: Runnable = Runnable {
        WHALELog.i(TAG, "periodicStopRunnable: stop sampling")
        setState(LogServiceState.IDLE)
    }

    private fun setupPeriodicSampling() {
        isPeriodicSamplingEnabled = true
        periodicStartRunnable.run()
    }

    private fun stopPeriodicSampling() {
        isPeriodicSamplingEnabled = false
        periodicHandler.removeCallbacks(periodicStartRunnable)
        periodicHandler.removeCallbacks(periodicStopRunnable)
    }

    private fun setupContiunousSampling() {
        startSensors(onlyPeriodic = false, includeContinous = true)
    }

    private fun stopSampling() {
        stopSensors(true)
        stopPeriodicSampling()
        stopListeningForLockUnlock()
    }

    /* Section: State Handling */
    private fun setState(newState: LogServiceState?) {
        processNewState(state, newState)
    }

    private fun processNewState(previousState: LogServiceState?, newState: LogServiceState?) {
        WHALELog.i(
            TAG,
            "state transition: $previousState -> $newState (periodicEnabled=$isPeriodicSamplingEnabled)"
        )
        if (previousState == LogServiceState.IDLE) {
            if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                WHALELog.i(TAG, "device unlocked, starting full sampling, sensorlist: $singletonSensorList")

                // Cancel any pending periodic sampling starts since we're doing unlock sampling now
                periodicHandler.removeCallbacks(periodicStartRunnable)

                startSensors(onlyPeriodic = false, includeContinous = false)

                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable)
                lockUnlockStopHandler.postDelayed(
                    lockUnlockStopRunnable,
                    LOCK_UNLOCK_SAMPLE_DURATION.toLong()
                )
                state = LogServiceState.SAMPLING_AFTER_UNLOCK
            } else if (newState == LogServiceState.SAMPLING_PERIODIC) {
                WHALELog.i(TAG, "starting periodic sampling for " + PERIODIC_SAMPLING_SAMPLE_DURATION + "ms")

                startSensors(onlyPeriodic = true, includeContinous = false)

                periodicHandler.removeCallbacks(periodicStopRunnable)
                periodicHandler.postDelayed(
                    periodicStopRunnable,
                    PERIODIC_SAMPLING_SAMPLE_DURATION.toLong()
                ) // 1 minute

                state = LogServiceState.SAMPLING_PERIODIC
            }
        } else if (previousState == LogServiceState.SAMPLING_PERIODIC) {
            if (newState == LogServiceState.IDLE) {
                WHALELog.i(
                    TAG,
                    "stopping periodic sampling, next cycle in " + PERIODIC_SAMPLING_CYCLE_DURATION + "ms"
                )
                stopSensors(false)

                periodicHandler.removeCallbacks(periodicStopRunnable)
                if (isPeriodicSamplingEnabled) {
                    periodicHandler.postDelayed(
                        periodicStartRunnable,
                        PERIODIC_SAMPLING_CYCLE_DURATION.toLong()
                    ) // 5 minutes
                }

                state = LogServiceState.IDLE
            } else if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                // if running periodic sampling and device gets unlocked, start full sampling instead
                WHALELog.i(
                    TAG,
                    "device unlocked during periodic sampling, switching to full sampling (periodic cycle continues)"
                )
                stopSensors(false)
                startSensors(onlyPeriodic = false, includeContinous = false)

                // Don't remove periodic callbacks - let them continue in the background
                // The periodic cycle will continue, but unlock sampling takes priority
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable)
                lockUnlockStopHandler.postDelayed(
                    lockUnlockStopRunnable,
                    LOCK_UNLOCK_SAMPLE_DURATION.toLong()
                )

                state = LogServiceState.SAMPLING_AFTER_UNLOCK
            }
        } else if (previousState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
            if (newState == LogServiceState.IDLE) {
                stopSensors(false)

                state = LogServiceState.IDLE

                // If periodic sampling is enabled, check if we should start it now or wait
                if (isPeriodicSamplingEnabled) {
                    WHALELog.i(TAG, "unlock sampling done, periodic cycle continues")
                    // The periodic cycle should already be scheduled from before the unlock
                    // If not, restart it
                    if (!periodicHandler.hasCallbacks(periodicStartRunnable) && !periodicHandler.hasCallbacks(
                            periodicStopRunnable
                        )
                    ) {
                        WHALELog.i(TAG, "no periodic callbacks found, restarting periodic cycle")
                        periodicHandler.postDelayed(
                            periodicStartRunnable,
                            PERIODIC_SAMPLING_CYCLE_DURATION.toLong()
                        )
                    }
                }
            } else if (newState == LogServiceState.SAMPLING_PERIODIC) {
                // This can happen if periodic sampling fires while unlock sampling is still running
                WHALELog.i(TAG, "periodic sampling triggered during unlock sampling, deferring")
                // Just ignore and let unlock sampling finish, periodic will retry
            } else {
                WHALELog.e(TAG, "invalid state transition")
            }
        }
    }

    /* Section: Healthcheck */
    private fun runHealthcheck(context: Context): HealthcheckResult {
        val result = checkServices(context)
        if (!result.allHealthy) {
            WHALELog.w(TAG, "Healthcheck failed on unlock - services may need attention")

            // Check for revoked permissions and show notification if needed
            val revokedPerms = result.permissionsGranted.filterValues { !it }
            if (revokedPerms.isNotEmpty()) {
                WHALELog.w(TAG, "Found ${revokedPerms.size} revoked permissions, triggering notification")
                CoroutineScope(Dispatchers.Main).launch {
                    permissionNotificationHelper.showPermissionRevokedNotification(
                        context,
                        result.permissionsGranted
                    )
                }
            }
        }
        return result
    }

    /* Section: Interaction Widget */
    private fun showInteractionWidget() {
        val intent = Intent(this, NotificationTriggerFloatingWidgetService::class.java)
        startService(intent)
    }

    private fun hideInteractionWidget() {
        val intent = Intent(this, NotificationTriggerFloatingWidgetService::class.java)
        stopService(intent)
    }

    /* Section: Sensor Handling */
    private fun initializeSensors() {
        dataStoreManager.getSensitiveDataSaltSync { salt: String? ->
            sensorList = singletonSensorList.getList(this, database, salt)
            Unit
        }
    }

    private fun startSensors(onlyPeriodic: Boolean, includeContinous: Boolean) {
        dataStoreManager.getSensitiveDataSaltSync { salt: String? ->
            // use the singleton list because we want to keep our sensor's state inbetween activations
            sensorList = singletonSensorList.getList(this, database, salt)

            WHALELog.i(TAG, "size: " + sensorList!!.size)
            for (sensor in sensorList!!) {
                if (sensor.isEnabled && sensor.isAvailable(this) && (sensor.availableForPeriodicSampling() || !onlyPeriodic || (sensor.availableForContinuousSampling() && includeContinous))) {
                    sensor.start(this)

                    WHALELog.i(TAG, sensor.sensorName + " turned on")
                } else {
                    WHALELog.i(TAG, sensor.sensorName + " turned off")
                }
            }
        }
    }

    private fun stopSensors(includeContinuous: Boolean) {
        if (sensorList == null) {
            WHALELog.w(TAG, "stopSensors called but sensorList is null")
            return
        }
        for (sensor in sensorList) {
            if (sensor.isRunning && ((sensor.availableForContinuousSampling() && includeContinuous) || !sensor.availableForContinuousSampling())) {
                sensor.stop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        mMessenger = Messenger(IncomingHandler(this, this))
        return mMessenger!!.binder
    }

    inner class LogBinder : Binder() {
        val service: LogService
            get() = this@LogService
    }

    companion object {
        const val START_SENSORS: Int = 0
        const val STOP_SENSORS: Int = 1
        const val LISTEN_LOCK_UNLOCK: Int = 2
        const val LISTEN_LOCK_UNLOCK_AND_PERIODIC: Int = 3
        const val SLEEP_MODE: Int = 5

        private const val PERIODIC_SAMPLING_SAMPLE_DURATION = 60 * 1000 // 1 minute
        private const val PERIODIC_SAMPLING_CYCLE_DURATION = 5 * 60 * 1000 // 5 minutes
        private const val LOCK_UNLOCK_SAMPLE_DURATION = 60 * 1000 // 1 minute
    }
}
