package de.mimuc.senseeverything.service;

import java.lang.ref.WeakReference;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.sensor.SensorNotRunningException;
import de.mimuc.senseeverything.sensor.SingletonSensorList;

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

	private List<AbstractSensor> sensorList = null;
	private Messenger mMessenger;
	private BroadcastReceiver broadcastReceiver;
	private boolean isSampling = false;

	@Inject
	public SingletonSensorList singletonSensorList;

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
		unregisterReceiver(broadcastReceiver);
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
						break;
					}
					case STOP_SENSORS: {
						service.stopSensors(true);
						service.stopPeriodicSampling();
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
					case SEND_SENSOR_LOG_DATA: {
						String sensorName = msg.getData().getString("sensorName");
						String sensorData = msg.getData().getString("sensorData");

						service.receiveSensorLogData(sensorName, sensorData);
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

	/* todo: this behavior is necessary to run inside a ForegroundService
	    but I generally want to capsule the behavior to how we're sampling the sensors (so in the sampling strategy)
	    now we're calling it from the OnUnlockSamplingStrategy and would not be if we're periodically sensing, but we want to do both,
	    so instead we might want to have another ForegroundService for this?
	 */
	private void listenForLockUnlock() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_USER_PRESENT);
		filter.addAction(Intent.ACTION_SCREEN_OFF);

		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
					Log.d(TAG, "device locked, stopping sampling");
					stopSensors(false);
					hideInteractionWidget();
				} else {
					Log.d(TAG, "device unlocked, starting sampling, sensorlist" + singletonSensorList);
					// fixme: handle condition where logging might still be running?
					startSensors(false, false);
					showInteractionWidget();
				}
			}
		};
		initializeSensors();
		registerReceiver(broadcastReceiver, filter);
		Log.d(TAG, "registered broadcast receiver");
	}

	Handler periodicHandler = new Handler();
	Runnable periodicRunnable = new Runnable() {
		@Override
		public void run() {
			if (!isSampling) {
				Log.d(TAG, "periodic sampling start");
				startSensors(true, false);
				periodicHandler.postDelayed(this, 1000 * 60); // 1 minute
			} else {
				Log.d(TAG, "periodic sampling stop");
				stopSensors(false);
				periodicHandler.postDelayed(this, 1000 * 60 * 5); // 5 minutes
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

	private void receiveSensorLogData(String sensorName, String sensorData) {
		Log.d(TAG, "received sensor log data: " + sensorName + " " + sensorData);

		try {
			Class<?> sensorClass = Class.forName(sensorName);
			AbstractSensor sensor = singletonSensorList.getSensorOfType(sensorClass);
			Log.d(TAG, "sensors active: " + sensorList.stream().filter(s -> s.isRunning()).count());
			if (sensor != null) {
				sensor.tryLogStringData(sensorData);
			}
		} catch (ClassNotFoundException e) {
            Log.e(TAG, "Could not find sensor class: " + sensorName, e);
        } catch (SensorNotRunningException e) {
            Log.e(TAG, "Sensor not running: " + sensorName, e);
        }
    }

	@Override
	public IBinder onBind(Intent intent) {
		mMessenger = new Messenger(new IncomingHandler(this, this));
		return mMessenger.getBinder();
	}

	private void showInteractionWidget() {
		Intent intent = new Intent(this, InteractionFloatingWidgetService.class);
		startService(intent);
	}

	private void hideInteractionWidget() {
		Intent intent = new Intent(this, InteractionFloatingWidgetService.class);
		stopService(intent);
	}

	private void initializeSensors() {
		sensorList = singletonSensorList.getList(this);
	}

	private void startSensors(boolean onlyPeriodic, boolean includeContinous) {
		// use the singleton list because we want to keep our sensor's state inbetween activations
		sensorList = singletonSensorList.getList(this);

		Log.d(TAG, "size: "+sensorList.size());
		for(AbstractSensor sensor : sensorList) {
			if (sensor.isEnabled() && sensor.isAvailable(this) && (sensor.availableForPeriodicSampling() || !onlyPeriodic || (sensor.availableForContinuousSampling() && includeContinous)))
			{
				sensor.start(this);

				Log.d(TAG, sensor.getSensorName() + " turned on");
			}
			else
			{
				Log.w(TAG, sensor.getSensorName() + " turned off");
			}
		}

		isSampling = true;
	}

	private void stopSensors(boolean includeContinuous) {
		for(AbstractSensor sensor : sensorList) {
			if (sensor.isRunning() && ((sensor.availableForContinuousSampling() && includeContinuous) || !sensor.availableForContinuousSampling()))
			{
				sensor.stop();
			}
		}

		isSampling = false;
	}

	public class LogBinder extends Binder {
		public LogService getService() {
			return LogService.this;
		}
	}
}
