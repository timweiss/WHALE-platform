package de.mimuc.senseeverything.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import java.lang.ref.WeakReference;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.logging.WHALELog;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.sensor.SingletonSensorList;
import de.mimuc.senseeverything.service.floatingWidget.NotificationTriggerFloatingWidgetService;
import de.mimuc.senseeverything.service.healthcheck.HealthcheckResult;
import de.mimuc.senseeverything.service.healthcheck.ServiceHealthcheck;
import kotlin.Unit;

enum LogServiceState {
    IDLE,
    SLEEP,
    SAMPLING_AFTER_UNLOCK,
    SAMPLING_PERIODIC
}

@AndroidEntryPoint
public class LogService extends AbstractService {
    public static final int START_SENSORS = 0;
    public static final int STOP_SENSORS = 1;
    public static final int LISTEN_LOCK_UNLOCK = 2;
    public static final int LISTEN_LOCK_UNLOCK_AND_PERIODIC = 3;
    public static final int SLEEP_MODE = 5;

    private final int PERIODIC_SAMPLING_SAMPLE_DURATION = 60 * 1000; // 1 minute
    private final int PERIODIC_SAMPLING_CYCLE_DURATION = 5 * 60 * 1000; // 5 minutes
    private final int LOCK_UNLOCK_SAMPLE_DURATION = 60 * 1000; // 1 minute

    private List<AbstractSensor> sensorList = null;
    private Messenger mMessenger;
    private BroadcastReceiver lockUnlockReceiver;
    private boolean isInSleepMode = false;

    private LogServiceState state = LogServiceState.IDLE;
    private boolean isPeriodicSamplingEnabled = false;

    @Inject
    public SingletonSensorList singletonSensorList;

    @Inject
    public DataStoreManager dataStoreManager;

    @Inject
    public AppDatabase database;

    @Override
    public void onCreate() {
        TAG = getClass().getName();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int ret = super.onStartCommand(intent, flags, startId);
        // starting sensors should only be done, once communicated by the SamplingManager
        return ret;
    }

    @Override
    public void onDestroy() {
        // clear handlers
        periodicHandler.removeCallbacks(periodicStartRunnable);
        periodicHandler.removeCallbacks(periodicStopRunnable);
        lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);

        stopSensors(true);

        if (lockUnlockReceiver != null) {
            try {
                unregisterReceiver(lockUnlockReceiver);
                WHALELog.INSTANCE.i(TAG, "lockUnlockReceiver unregistered successfully");
            } catch (IllegalArgumentException e) {
                WHALELog.INSTANCE.w(TAG, "lockUnlockReceiver was not registered");
            }
        }

        super.onDestroy();
    }

    static class IncomingHandler extends Handler {
        private final WeakReference<LogService> serviceReference;
        private Context applicationContext;

        private final String TAG = "LogServiceMessageHandler";

        IncomingHandler(Context context, LogService service) {
            applicationContext = context.getApplicationContext();
            serviceReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            LogService service = serviceReference.get();
            if (service != null) {
                WHALELog.INSTANCE.i(TAG, "message: " + msg.what);
                switch (msg.what) {
                    case START_SENSORS: {
                        service.startSensors(false, true);
                        break;
                    }
                    case STOP_SENSORS: {
                        service.stopSampling();
                        break;
                    }
                    case LISTEN_LOCK_UNLOCK: {
                        service.listenForLockUnlock();
                        break;
                    }
                    case LISTEN_LOCK_UNLOCK_AND_PERIODIC: {
                        service.listenForLockUnlock();
                        service.setupPeriodicSampling();
                        service.setupContiunousSampling();
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                }
            } else {
                WHALELog.INSTANCE.e(TAG, "Service has unexpectedly died");
            }
        }
    }

    /* Section: Sampling */

    Handler lockUnlockStopHandler = new Handler();
    Runnable lockUnlockStopRunnable = () -> {
        WHALELog.INSTANCE.i(TAG, "lockUnlockStopRunnable: stop sampling");
        setState(LogServiceState.IDLE);
    };

    private void listenForLockUnlock() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        lockUnlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    WHALELog.INSTANCE.i(TAG, "lockUnlockReceiver: device locked");
                    hideInteractionWidget();
                } else {
                    WHALELog.INSTANCE.i(TAG, "lockUnlockReceiver: device unlocked, sensorlist" + singletonSensorList);
                    showInteractionWidget();
                    setState(LogServiceState.SAMPLING_AFTER_UNLOCK);

                    // Run healthcheck on unlock
                    runHealthcheckOnUnlock(context);
                }
            }
        };
        initializeSensors();
        registerReceiver(lockUnlockReceiver, filter);
        WHALELog.INSTANCE.i(TAG, "registered lock/unlock receiver");
    }

    private void stopListeningForLockUnlock() {
        unregisterReceiver(lockUnlockReceiver);
        WHALELog.INSTANCE.i(TAG, "unregistered lock/unlock receiver");
    }

    Handler periodicHandler = new Handler();
    Runnable periodicStartRunnable = () -> {
        WHALELog.INSTANCE.i(TAG, "periodicStartRunnable: start sampling");
        setState(LogServiceState.SAMPLING_PERIODIC);
    };

    Runnable periodicStopRunnable = () -> {
        WHALELog.INSTANCE.i(TAG, "periodicStopRunnable: stop sampling");
        setState(LogServiceState.IDLE);
    };

    private void setupPeriodicSampling() {
        isPeriodicSamplingEnabled = true;
        periodicStartRunnable.run();
    }

    private void stopPeriodicSampling() {
        isPeriodicSamplingEnabled = false;
        periodicHandler.removeCallbacks(periodicStartRunnable);
        periodicHandler.removeCallbacks(periodicStopRunnable);
    }

    private void setupContiunousSampling() {
        startSensors(false, true);
    }

    private void stopSampling() {
        stopSensors(true);
        stopPeriodicSampling();
        stopListeningForLockUnlock();
    }

    /* Section: State Handling */
    private void setState(LogServiceState newState) {
        processNewState(state, newState);
    }

    private void processNewState(LogServiceState previousState, LogServiceState newState) {
        WHALELog.INSTANCE.i(TAG, "state transition: " + previousState + " -> " + newState + " (periodicEnabled=" + isPeriodicSamplingEnabled + ")");
        if (previousState == LogServiceState.IDLE) {
            if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                WHALELog.INSTANCE.i(TAG, "device unlocked, starting full sampling, sensorlist" + singletonSensorList);
                // Cancel any pending periodic sampling starts since we're doing unlock sampling now
                periodicHandler.removeCallbacks(periodicStartRunnable);
                startSensors(false, false);
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                lockUnlockStopHandler.postDelayed(lockUnlockStopRunnable, LOCK_UNLOCK_SAMPLE_DURATION);
                state = LogServiceState.SAMPLING_AFTER_UNLOCK;
            } else if (newState == LogServiceState.SAMPLING_PERIODIC) {
                WHALELog.INSTANCE.i(TAG, "starting periodic sampling for " + PERIODIC_SAMPLING_SAMPLE_DURATION + "ms");
                startSensors(true, false);
                periodicHandler.removeCallbacks(periodicStopRunnable);
                periodicHandler.postDelayed(periodicStopRunnable, PERIODIC_SAMPLING_SAMPLE_DURATION); // 1 minute
                state = LogServiceState.SAMPLING_PERIODIC;
            }
        } else if (previousState == LogServiceState.SAMPLING_PERIODIC) {
            if (newState == LogServiceState.IDLE) {
                WHALELog.INSTANCE.i(TAG, "stopping periodic sampling, next cycle in " + PERIODIC_SAMPLING_CYCLE_DURATION + "ms");
                stopSensors(false);
                periodicHandler.removeCallbacks(periodicStopRunnable);
                if (isPeriodicSamplingEnabled) {
                    periodicHandler.postDelayed(periodicStartRunnable, PERIODIC_SAMPLING_CYCLE_DURATION); // 5 minutes
                }
                state = LogServiceState.IDLE;
            } else if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                // if running periodic sampling and device gets unlocked, start full sampling instead
                WHALELog.INSTANCE.i(TAG, "device unlocked during periodic sampling, switching to full sampling (periodic cycle continues)");
                stopSensors(false);
                startSensors(false, false);
                // Don't remove periodic callbacks - let them continue in the background
                // The periodic cycle will continue, but unlock sampling takes priority
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                lockUnlockStopHandler.postDelayed(lockUnlockStopRunnable, LOCK_UNLOCK_SAMPLE_DURATION);
                state = LogServiceState.SAMPLING_AFTER_UNLOCK;
            }
        } else if (previousState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
            if (newState == LogServiceState.IDLE) {
                stopSensors(false);
                state = LogServiceState.IDLE;
                // If periodic sampling is enabled, check if we should start it now or wait
                if (isPeriodicSamplingEnabled) {
                    WHALELog.INSTANCE.i(TAG, "unlock sampling done, periodic cycle continues");
                    // The periodic cycle should already be scheduled from before the unlock
                    // If not, restart it
                    if (!periodicHandler.hasCallbacks(periodicStartRunnable) && !periodicHandler.hasCallbacks(periodicStopRunnable)) {
                        WHALELog.INSTANCE.i(TAG, "no periodic callbacks found, restarting periodic cycle");
                        periodicHandler.postDelayed(periodicStartRunnable, PERIODIC_SAMPLING_CYCLE_DURATION);
                    }
                }
            } else if (newState == LogServiceState.SAMPLING_PERIODIC) {
                // This can happen if periodic sampling fires while unlock sampling is still running
                WHALELog.INSTANCE.i(TAG, "periodic sampling triggered during unlock sampling, deferring");
                // Just ignore and let unlock sampling finish, periodic will retry
            } else {
                WHALELog.INSTANCE.e(TAG, "invalid state transition");
            }
        }
    }

    /* Section: Healthcheck */

    private void runHealthcheckOnUnlock(Context context) {
        HealthcheckResult result = ServiceHealthcheck.INSTANCE.checkServices(context);
        if (!result.getAllHealthy()) {
            WHALELog.INSTANCE.w(TAG, "Healthcheck failed on unlock - services may need attention");
        }
    }

    /* Section: Interaction Widget */

    private void showInteractionWidget() {
        Intent intent = new Intent(this, NotificationTriggerFloatingWidgetService.class);
        startService(intent);
    }

    private void hideInteractionWidget() {
        Intent intent = new Intent(this, NotificationTriggerFloatingWidgetService.class);
        stopService(intent);
    }

    /* Section: Sensor Handling */

    private void initializeSensors() {
        dataStoreManager.getSensitiveDataSaltSync(salt -> {
            sensorList = singletonSensorList.getList(this, database, salt);
            return Unit.INSTANCE;
        });
    }

    private void startSensors(boolean onlyPeriodic, boolean includeContinous) {
        dataStoreManager.getSensitiveDataSaltSync(salt -> {
            // use the singleton list because we want to keep our sensor's state inbetween activations
            sensorList = singletonSensorList.getList(this, database, salt);

            WHALELog.INSTANCE.i(TAG, "size: " + sensorList.size());
            for (AbstractSensor sensor : sensorList) {
                if (sensor.isEnabled() && sensor.isAvailable(this) && (sensor.availableForPeriodicSampling() || !onlyPeriodic || (sensor.availableForContinuousSampling() && includeContinous))) {
                    sensor.start(this);

                    WHALELog.INSTANCE.i(TAG, sensor.getSensorName() + " turned on");
                } else {
                    WHALELog.INSTANCE.i(TAG, sensor.getSensorName() + " turned off");
                }
            }
            return Unit.INSTANCE;
        });
    }

    private void stopSensors(boolean includeContinuous) {
        if (sensorList == null) {
            WHALELog.INSTANCE.w(TAG, "stopSensors called but sensorList is null");
            return;
        }
        for (AbstractSensor sensor : sensorList) {
            if (sensor.isRunning() && ((sensor.availableForContinuousSampling() && includeContinuous) || !sensor.availableForContinuousSampling())) {
                sensor.stop();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(this, this));
        return mMessenger.getBinder();
    }

    public class LogBinder extends Binder {
        public LogService getService() {
            return LogService.this;
        }
    }
}
