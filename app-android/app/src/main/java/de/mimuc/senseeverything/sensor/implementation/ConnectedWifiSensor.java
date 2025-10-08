package de.mimuc.senseeverything.sensor.implementation;


import static de.mimuc.senseeverything.helpers.SensitiveDataKt.getSensitiveDataHash;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.View;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.logging.WHALELog;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class ConnectedWifiSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;

	public ConnectedWifiSensor(Context applicationContext, AppDatabase database, String salt) {
		super(applicationContext, database, salt + "wifi");
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Wi-Fi SSID";
		FILE_NAME = "wifi_ssid.csv";
		m_FileHeader = "TimeUnix,Ssid";
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
		String rawssid = wifiInfo.getSSID();
		String ssid = (rawssid == null || rawssid.equals(WifiManager.UNKNOWN_SSID)) ? "NONE/UNKNOWN" : getSensitiveDataHash(wifiInfo.getSSID(), sensitiveDataSalt);
		try {
			onLogDataItem(t, ssid);
		} catch (Exception e) {
			WHALELog.INSTANCE.e(TAG, e.toString());
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
