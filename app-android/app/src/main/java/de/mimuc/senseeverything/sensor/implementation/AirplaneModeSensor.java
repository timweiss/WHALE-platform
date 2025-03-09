package de.mimuc.senseeverything.sensor.implementation;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;


public class AirplaneModeSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;

	public AirplaneModeSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Airplane Mode";
		FILE_NAME = "airplane_mode.csv";
		m_FileHeader = "TimeUnix,Value";
	}

	@Override
	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return true;
	}

	private static boolean isAirplaneModeOn(Context context) {
		return Settings.System.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
	}

	@Override
	public void start(Context context){
		super.start(context);
		Long t = System.currentTimeMillis();
		if (!m_isSensorAvailable)
			return;

		if(isAirplaneModeOn(context)) {
			onLogDataItem(t, "on\n");
		} else {
			onLogDataItem(t, "off\n");
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
