package de.mimuc.senseeverything.service;

import java.lang.ref.WeakReference;
import java.util.List;

import de.mimuc.senseeverything.sensor.AbstractSensor;
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

public class LogService extends AbstractService {
	public static final int START_SENSORS = 0;
	public static final int STOP_SENSORS = 1;
	public static final int LISTEN_LOCK_UNLOCK = 2;

	private List<AbstractSensor> sensorList = null;
	private Messenger mMessenger;
	private BroadcastReceiver broadcastReceiver;

	@Override
	public void onCreate() {
		TAG = getClass().getName();
		super.onCreate();
	}	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int ret = super.onStartCommand(intent, flags, startId);

		startSensors();
		
		return ret;
	}

	@Override
	public void onDestroy() {
		stopSensors();
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
						service.startSensors();
						break;
					}
					case STOP_SENSORS: {
						service.stopSensors();
						break;
					}
					case LISTEN_LOCK_UNLOCK: {
						service.listenForLockUnlock();
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
					stopSensors();
				} else {
					Log.d(TAG, "device unlocked, starting sampling");
					// fixme: handle condition where logging might still be running?
					startSensors();
				}
			}
		};
		registerReceiver(broadcastReceiver, filter);
		Log.d(TAG, "registered broadcast receiver");
	}


	@Override
	public IBinder onBind(Intent intent) {
		mMessenger = new Messenger(new IncomingHandler(this, this));
		return mMessenger.getBinder();
	}

	public void startSensors() {
		// use the singleton list because we want to keep our sensor's state inbetween activations
		sensorList = SingletonSensorList.getList(this);

		Log.d(TAG, "size: "+sensorList.size());
		for(AbstractSensor sensor : sensorList) {
			if (sensor.isEnabled() && sensor.isAvailable(this))
			{
				sensor.start(this);

				Log.d(TAG, sensor.getSensorName() + " turned on");
			}
			else
			{
				Log.w(TAG, sensor.getSensorName() + " turned off");
			}
		}
	}

	public void stopSensors() {
		for(AbstractSensor sensor : sensorList) {
			if (sensor.isRunning())
			{
				sensor.stop();
			}
		}
	}

	public class LogBinder extends Binder {
		public LogService getService() {
			return LogService.this;
		}
	}
}
