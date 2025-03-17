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
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.R;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.sensor.SensorNotRunningException;
import de.mimuc.senseeverything.sensor.SingletonSensorList;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

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
    public static final int SEND_SENSOR_LOG_DATA = 4;
    public static final int SLEEP_MODE = 5;

    private final int PERIODIC_SAMPLING_SAMPLE_DURATION = 60 * 1000; // 1 minute
    private final int PERIODIC_SAMPLING_CYCLE_DURATION = 5 * 60 * 1000; // 5 minutes
    private final int LOCK_UNLOCK_SAMPLE_DURATION = 60 * 1000; // 1 minute

    private List<AbstractSensor> sensorList = null;
    private Messenger mMessenger;
    private BroadcastReceiver lockUnlockReceiver;
    private boolean isSampling = false;
    private boolean isInSleepMode = false;

    private LogServiceState state = LogServiceState.IDLE;

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
        stopSensors(true);
        unregisterReceiver(lockUnlockReceiver);
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
                Log.d(TAG, "message: " + msg.what);
                switch (msg.what) {
                    case START_SENSORS: {
                        service.startSensors(false, true);
                        service.stopSleepMode();
                        break;
                    }
                    case STOP_SENSORS: {
                        service.stopSampling();
                        break;
                    }
                    case LISTEN_LOCK_UNLOCK: {
                        service.listenForLockUnlock();
                        service.stopSleepMode();
                        break;
                    }
                    case LISTEN_LOCK_UNLOCK_AND_PERIODIC: {
                        service.listenForLockUnlock();
                        service.setupPeriodicSampling();
                        service.setupContiunousSampling();
                        service.stopSleepMode();
                        break;
                    }
                    case SEND_SENSOR_LOG_DATA: {
                        String sensorName = msg.getData().getString("sensorName");
                        String sensorData = msg.getData().getString("sensorData");

                        service.receiveSensorLogData(sensorName, sensorData);
                        break;
                    }
                    case SLEEP_MODE: {
                        String until = msg.getData().getString("until");
                        long untilTime = Long.parseLong(until);

                        service.stopSampling();
                        service.setSleepMode(untilTime);
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                }
            } else {
                Log.e(TAG, "Service has unexpectedly died");
            }
        }
    }

    /* Section: Sampling */

    Handler lockUnlockStopHandler = new Handler();
    Runnable lockUnlockStopRunnable = () -> {
        Log.d(TAG, "lockUnlockStopRunnable: stop sampling");
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
                    Log.d(TAG, "lockUnlockReceiver: device locked");
                    hideInteractionWidget();
                } else {
                    Log.d(TAG, "lockUnlockReceiver: device unlocked, sensorlist" + singletonSensorList);
                    showInteractionWidget();
                    setState(LogServiceState.SAMPLING_AFTER_UNLOCK);
                }
            }
        };
        initializeSensors();
        registerReceiver(lockUnlockReceiver, filter);
        Log.i(TAG, "registered lock/unlock receiver");
    }

    private void stopListeningForLockUnlock() {
        unregisterReceiver(lockUnlockReceiver);
        Log.i(TAG, "unregistered lock/unlock receiver");
    }

    Handler periodicHandler = new Handler();
    Runnable periodicRunnable = () -> {
        if (!isSampling) {
            Log.d(TAG, "periodicRunnable: start sampling");
            setState(LogServiceState.SAMPLING_PERIODIC);
        } else {
            Log.d(TAG, "periodicRunnable: stop sampling");
            setState(LogServiceState.IDLE);
        }
    };

    private void setupPeriodicSampling() {
        periodicRunnable.run();
    }

    private void stopPeriodicSampling() {
        periodicHandler.removeCallbacks(periodicRunnable);
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
        Log.d(TAG, "state transition: " + previousState + " -> " + newState);
        if (previousState == LogServiceState.IDLE) {
            if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                Log.d(TAG, "device unlocked, starting sampling, sensorlist" + singletonSensorList);
                startSensors(false, false);
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                lockUnlockStopHandler.postDelayed(lockUnlockStopRunnable, LOCK_UNLOCK_SAMPLE_DURATION);
                state = LogServiceState.SAMPLING_AFTER_UNLOCK;
            } else if (newState == LogServiceState.SAMPLING_PERIODIC) {
                startSensors(true, false);
                periodicHandler.postDelayed(periodicRunnable, PERIODIC_SAMPLING_SAMPLE_DURATION); // 1 minute
                state = LogServiceState.SAMPLING_PERIODIC;
            }
        } else if (previousState == LogServiceState.SAMPLING_PERIODIC) {
            if (newState == LogServiceState.IDLE) {
                stopSensors(false);
                periodicHandler.removeCallbacks(periodicRunnable);
                periodicHandler.postDelayed(periodicRunnable, PERIODIC_SAMPLING_CYCLE_DURATION); // 5 minutes
                state = LogServiceState.IDLE;
            } else if (newState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
                // if running periodic sampling and device gets unlocked, start full sampling instead
                stopSensors(false);
                startSensors(false, false);
                periodicHandler.removeCallbacks(periodicRunnable);
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                lockUnlockStopHandler.postDelayed(lockUnlockStopRunnable, LOCK_UNLOCK_SAMPLE_DURATION);
                state = LogServiceState.SAMPLING_AFTER_UNLOCK;
            }
        } else if (previousState == LogServiceState.SAMPLING_AFTER_UNLOCK) {
            if (newState == LogServiceState.IDLE) {
                stopSensors(false);
                state = LogServiceState.IDLE;
            } else {
                // throw new IllegalStateException("Cannot transition from SAMPLING_AFTER_UNLOCK to SAMPLING_PERIODIC");
            }
        }
    }

    /* Section: Receiving Sensor Log Data */

    private void receiveSensorLogData(String sensorName, String sensorData) {
        Log.d(TAG, "received sensor log data: " + sensorName + " " + sensorData);

        try {
            Class<?> sensorClass = Class.forName(sensorName);
            AbstractSensor sensor = singletonSensorList.getSensorOfType(sensorClass);
            Log.d(TAG, "sensors active: " + singletonSensorList.getList(this, database, "").stream().filter(s -> s.isRunning()).count());
            if (sensor != null) {
                sensor.tryLogStringData(sensorData);
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Could not find sensor class: " + sensorName, e);
        } catch (SensorNotRunningException e) {
            Log.e(TAG, "Sensor not running: " + sensorName, e);
        }
    }

    /* Section: Sleep Mode */

    private void setSleepMode(long untilTime) {
        isInSleepMode = true;
        try {
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPaused(true, continuation));
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPausedUntil(untilTime, continuation));
        } catch (Exception e) {
            Log.e(TAG, "Could not save study paused", e);
        }
        // Sleep Notification
        replaceNotification(getString(R.string.app_name), getString(R.string.notification_sleep_text), R.drawable.notification_whale);
    }

    private void stopSleepMode() {
        try {
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPaused(false, continuation));
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPausedUntil(-1, continuation));
        } catch (Exception e) {
            Log.e(TAG, "Could not save study paused", e);
        }

        if (isInSleepMode) {
            isInSleepMode = false;
            replaceNotification(getString(R.string.app_name), getString(R.string.notif_title), R.drawable.notification_whale);
        }
    }

    /* Section: Interaction Widget */

    private void showInteractionWidget() {
        Intent intent = new Intent(this, InteractionFloatingWidgetService.class);
        startService(intent);
    }

    private void hideInteractionWidget() {
        Intent intent = new Intent(this, InteractionFloatingWidgetService.class);
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

            Log.d(TAG, "size: " + sensorList.size());
            for (AbstractSensor sensor : sensorList) {
                if (sensor.isEnabled() && sensor.isAvailable(this) && (sensor.availableForPeriodicSampling() || !onlyPeriodic || (sensor.availableForContinuousSampling() && includeContinous))) {
                    sensor.start(this);

                    Log.d(TAG, sensor.getSensorName() + " turned on");
                } else {
                    Log.w(TAG, sensor.getSensorName() + " turned off");
                }
            }

            isSampling = true;

            return Unit.INSTANCE;
        });
    }

    private void stopSensors(boolean includeContinuous) {
        for (AbstractSensor sensor : sensorList) {
            if (sensor.isRunning() && ((sensor.availableForContinuousSampling() && includeContinuous) || !sensor.availableForContinuousSampling())) {
                sensor.stop();
            }
        }

        isSampling = false;
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
