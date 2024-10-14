package de.mimuc.senseeverything.service;

import java.lang.ref.WeakReference;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.R;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.sensor.SensorNotRunningException;
import de.mimuc.senseeverything.sensor.SingletonSensorList;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

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

import javax.inject.Inject;

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

    @Inject
    public SingletonSensorList singletonSensorList;

    @Inject
    public DataStoreManager dataStoreManager;

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
                        service.stopSampling();
                        service.setSleepMode();
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
    Runnable lockUnlockStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopSensors(false);
        }
    };

    private void listenForLockUnlock() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        lockUnlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "device locked, stopping sampling");
                    stopSensors(false);
                    hideInteractionWidget();
                    lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                } else {
                    Log.d(TAG, "device unlocked, starting sampling, sensorlist" + singletonSensorList);
                    startSensors(false, false);
                    lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable);
                    showInteractionWidget();
                    lockUnlockStopHandler.postDelayed(lockUnlockStopRunnable, LOCK_UNLOCK_SAMPLE_DURATION);
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
    Runnable periodicRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSampling) {
                Log.d(TAG, "periodic sampling start");
                startSensors(true, false);
                lockUnlockStopHandler.removeCallbacks(lockUnlockStopRunnable); // prevent lock/unlock from stopping the sensors
                periodicHandler.postDelayed(this, PERIODIC_SAMPLING_SAMPLE_DURATION); // 1 minute
            } else {
                Log.d(TAG, "periodic sampling stop");
                stopSensors(false);
                periodicHandler.postDelayed(this, PERIODIC_SAMPLING_CYCLE_DURATION); // 5 minutes
            }
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

    /* Section: Receiving Sensor Log Data */

    private void receiveSensorLogData(String sensorName, String sensorData) {
        Log.d(TAG, "received sensor log data: " + sensorName + " " + sensorData);

        try {
            Class<?> sensorClass = Class.forName(sensorName);
            AbstractSensor sensor = singletonSensorList.getSensorOfType(sensorClass);
            Log.d(TAG, "sensors active: " + singletonSensorList.getList(this).stream().filter(s -> s.isRunning()).count());
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

    private void setSleepMode() {
        isInSleepMode = true;
        try {
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPaused(true, continuation));
        } catch (Exception e) {
            Log.e(TAG, "Could not save study paused", e);
        }
        // Sleep Notification
        replaceNotification(getString(R.string.app_name), getString(R.string.notification_sleep_text), R.drawable.ic_launcher);
    }

    private void stopSleepMode() {
        try {
            BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, continuation) -> dataStoreManager.saveStudyPaused(false, continuation));
        } catch (Exception e) {
            Log.e(TAG, "Could not save study paused", e);
        }

        if (isInSleepMode) {
            isInSleepMode = false;
            replaceNotification(getString(R.string.app_name), getString(R.string.notif_title), R.drawable.ic_launcher);
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
        sensorList = singletonSensorList.getList(this);
    }

    private void startSensors(boolean onlyPeriodic, boolean includeContinous) {
        // use the singleton list because we want to keep our sensor's state inbetween activations
        sensorList = singletonSensorList.getList(this);

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
