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
public class OrientationSensor extends AbstractSensor implements SensorEventListener {

	private static final long serialVersionUID = 1L;
	
	private SensorManager sensorManager;

	public OrientationSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		TAG = getClass().getName();
		m_IsRunning = false;
		SENSOR_NAME = "Orientation Sensor";
		FILE_NAME = "orientation.csv";
		m_FileHeader = "TimeUnix,X,Y,Z,Reliable";

	}
	
	@Override
	public boolean isAvailable(Context context) {
		SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		return !(sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) == null);
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return false;
	}

	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
				SensorManager.SENSOR_DELAY_NORMAL);		
		m_IsRunning = true;
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
			if(event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
				onLogDataItem(t, CONST.numberFormat.format(event.values[0])+ "," +
						CONST.numberFormat.format(event.values[1]) + "," +
						CONST.numberFormat.format(event.values[2]) + ",false\n");
			} else {
				onLogDataItem(t, CONST.numberFormat.format(event.values[0]) + "," +
						CONST.numberFormat.format(event.values[1]) + "," +
						CONST.numberFormat.format(event.values[2]) + ",true\n");
			}
		}
	}
}
