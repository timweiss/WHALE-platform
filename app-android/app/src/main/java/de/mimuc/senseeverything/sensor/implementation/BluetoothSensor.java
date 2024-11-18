package de.mimuc.senseeverything.sensor.implementation;


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
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class BluetoothSensor extends AbstractSensor {

	private static final long serialVersionUID = 1L;

	private Context context;

	private ExecutorService executor;

	public BluetoothSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Nearby Bluetooth";
		FILE_NAME = "nearby_bluetooth.csv";
		m_FileHeader = "TimeUnix,DeviceName";
	}

	@Override
	public View getSettingsView(Context context) {
		return null;
	}

	@Override
	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return true;
	}

	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
			Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
			return;
		}

		/*
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
			return;
		}
		*/

		this.context = context;

		Log.d(TAG, "Starting discovery");
		executor = Executors.newFixedThreadPool(1);
		executor.execute(() -> {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!mBluetoothAdapter.startDiscovery()) {
                Log.e(TAG, "could not start discovery");
                return;
            }

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(mReceiver, filter);
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
					// getName() required BLUETOOTH_CONNECT permission
					Log.i(TAG, device.getAddress());
					onLogDataItem(t, device.getAddress());
				} catch (SecurityException e) {
					Log.e(TAG, e.getMessage());
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
			context.unregisterReceiver(mReceiver);
			closeDataSource();
		}
	}
}
