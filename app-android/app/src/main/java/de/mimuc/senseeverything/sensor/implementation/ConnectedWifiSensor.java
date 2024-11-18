package de.mimuc.senseeverything.sensor.implementation;


import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.View;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class ConnectedWifiSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;

	public ConnectedWifiSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Wi-Fi SSID";
		FILE_NAME = "wifi_ssid.csv";
		m_FileHeader = "TimeUnix,Ssid";
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
        Long t = System.currentTimeMillis();
		if (!m_isSensorAvailable)
			return;		

		WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();		
		String ssid = (wifiInfo.getSSID() == null) ? "NONE" : wifiInfo.getSSID();
		try {
			onLogDataItem(t, ssid);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		m_IsRunning = true;
	}

	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			closeDataSource();
		}
	}
}
