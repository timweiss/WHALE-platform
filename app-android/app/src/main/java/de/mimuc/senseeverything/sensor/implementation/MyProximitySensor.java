package de.mimuc.senseeverything.sensor.implementation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import de.mimuc.senseeverything.activity.CONST;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class MyProximitySensor extends AbstractSensor implements SensorEventListener {

	private static final long serialVersionUID = 1L;
	
	private SensorManager sensorManager;
	
	private long count;

	public MyProximitySensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = "ProximitySensor";
		SENSOR_NAME = "Proximity";
		FILE_NAME = "proximity.csv";
		m_FileHeader = "TimeUnix,Value,Reliable";
	}
	
	public boolean isAvailable(Context context) {
		SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		return !(sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null);
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

		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_FASTEST);		
		m_IsRunning = true;
		count = 0;
	}
	
	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			sensorManager.unregisterListener(this);
			closeDataSource();
		}	
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		Long t = System.currentTimeMillis();
		if(m_IsRunning) {
			if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
				onLogDataItem(t, CONST.numberFormat.format(event.values[0]) + ",false");
			} else {
				onLogDataItem(t, CONST.numberFormat.format(event.values[0]) + ",true");
			}
		}
	}

}