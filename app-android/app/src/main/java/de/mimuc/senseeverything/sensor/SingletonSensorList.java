package de.mimuc.senseeverything.sensor;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.mimuc.senseeverything.db.SensorDatabaseHelper;
import de.mimuc.senseeverything.sensor.implementation.AccessibilitySensor;
import de.mimuc.senseeverything.sensor.implementation.AppSensor;
import de.mimuc.senseeverything.sensor.implementation.AudioSampleSensor;
import de.mimuc.senseeverything.sensor.implementation.MyAccelerometerSensor;
import de.mimuc.senseeverything.sensor.implementation.MyGyroscopeSensor;
import de.mimuc.senseeverything.sensor.implementation.MyProximitySensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOnOffSensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOrientationSensor;
import de.mimuc.senseeverything.sensor.implementation.WifiSensor;

public class SingletonSensorList {
	private static SingletonSensorList instance;
	private final List<AbstractSensor> list = new ArrayList<>();

	private SingletonSensorList() {
	}

	public static SingletonSensorList getInstance() {
		if (instance == null) {
			instance = new SingletonSensorList();
		}
		return instance;
	}

	private void initializeList(Context pContext) {
		this.list.clear();

		Context aContext = pContext.getApplicationContext();

		this.list.add(new AudioSampleSensor(aContext));
		this.list.add(new ScreenOrientationSensor(aContext));
		this.list.add(new MyProximitySensor(aContext));
		this.list.add(new ScreenOnOffSensor(aContext));
		this.list.add(new WifiSensor(aContext));
		this.list.add(new MyAccelerometerSensor(aContext));
		this.list.add(new MyGyroscopeSensor(aContext));
		this.list.add(new AccessibilitySensor(aContext));

		SensorDatabaseHelper db = new SensorDatabaseHelper(pContext);

		for (AbstractSensor s : this.list)
			db.addIfNotExists(s);

		for (AbstractSensor s : this.list)
			s.setEnabled(db.getSensorData(s));

		db.close();
	}

	public List<AbstractSensor> getOrInitializeList(Context pContext) {
		if (this.list.isEmpty()) {
			initializeList(pContext);
		}

		return this.list;
	}

	public static List<AbstractSensor> getList(Context pContext) {
		return getInstance().getOrInitializeList(pContext);
	}
}
