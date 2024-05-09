package de.mimuc.senseeverything.sensor;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.mimuc.senseeverything.db.SensorDatabaseHelper;
import de.mimuc.senseeverything.sensor.implementation.AudioSampleSensor;

public class SingletonSensorList {
	private static SingletonSensorList instance;
	private List<AbstractSensor> list = new ArrayList<>();

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

		SensorDatabaseHelper db = new SensorDatabaseHelper(pContext);

		for (AbstractSensor s : this.list)
			db.addIfNotExists(s);

		for (AbstractSensor s : this.list)
			s.setEnabled(db.getSensorData(s));

		db.close();
	}

	public List<AbstractSensor> getOrInitializeList(Context pContext) {
		if (this.list == null || this.list.isEmpty()) {
			initializeList(pContext);
		}

		return this.list;
	}

	public static List<AbstractSensor> getList(Context pContext) {
		return getInstance().getOrInitializeList(pContext);
	}
}
