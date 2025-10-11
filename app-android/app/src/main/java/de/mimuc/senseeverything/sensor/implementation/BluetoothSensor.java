package de.mimuc.senseeverything.sensor.implementation;


import static de.mimuc.senseeverything.helpers.SensitiveDataKt.getSensitiveDataHash;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.logging.WHALELog;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class BluetoothSensor extends AbstractSensor {

	private static final long serialVersionUID = 1L;

	private Context context;

	private ExecutorService executor;

	public BluetoothSensor(Context applicationContext, AppDatabase database, String salt) {
		super(applicationContext, database, salt + "bt");
		m_IsRunning = false;
		TAG = "BluetoothSensor";
		SENSOR_NAME = "Nearby Bluetooth";
		FILE_NAME = "nearby_bluetooth.csv";
		m_FileHeader = "TimeUnix,DeviceName";
	}

	@Override
	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return true;
	}

	private boolean isRegistered = false;

	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
			WHALELog.INSTANCE.e(TAG, "BLUETOOTH_SCAN permission not granted");
			return;
		}

		this.context = context;

		WHALELog.INSTANCE.d(TAG, "Starting discovery");
		executor = Executors.newFixedThreadPool(1);
		executor.execute(() -> {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothAdapter.startDiscovery()) {
                WHALELog.INSTANCE.e(TAG, "could not start discovery");
                return;
            }

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(mReceiver, filter);
			isRegistered = true;
        });


		m_IsRunning = true;
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Long t = System.currentTimeMillis();

				try {
					String address = getSensitiveDataHash(device.getAddress(), sensitiveDataSalt);
					// getName() required BLUETOOTH_CONNECT permission
					WHALELog.INSTANCE.d(TAG, address);
					onLogDataItem(t, address);
				} catch (SecurityException e) {
					WHALELog.INSTANCE.e(TAG, e.getMessage());
				}
			}
		}
	};

	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			executor.shutdown();
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			mBluetoothAdapter.cancelDiscovery();
			if (isRegistered) {
				// unregister would fail if Bluetooth is off or permission is not granted
				context.unregisterReceiver(mReceiver);
			}
			closeDataSource();
		}
	}
}
