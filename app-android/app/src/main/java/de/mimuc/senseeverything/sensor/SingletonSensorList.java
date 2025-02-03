package de.mimuc.senseeverything.sensor;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.SensorDatabaseHelper;
import de.mimuc.senseeverything.sensor.implementation.AccessibilitySensor;
import de.mimuc.senseeverything.sensor.implementation.BluetoothSensor;
import de.mimuc.senseeverything.sensor.implementation.ConversationSensor;
import de.mimuc.senseeverything.sensor.implementation.InteractionLogSensor;
import de.mimuc.senseeverything.sensor.implementation.MyAccelerometerSensor;
import de.mimuc.senseeverything.sensor.implementation.MyGyroscopeSensor;
import de.mimuc.senseeverything.sensor.implementation.MyLightSensor;
import de.mimuc.senseeverything.sensor.implementation.MyProximitySensor;
import de.mimuc.senseeverything.sensor.implementation.NotificationSensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOnOffSensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOrientationSensor;
import de.mimuc.senseeverything.sensor.implementation.ConnectedWifiSensor;

@Singleton
public class SingletonSensorList {
	private final List<AbstractSensor> list = new ArrayList<>();

	@Inject
	SingletonSensorList() {
	}

	private void initializeList(Context pContext, AppDatabase database, String sensitiveDataSalt) {
		this.list.clear();

		Context aContext = pContext.getApplicationContext();

		this.list.add(new ConversationSensor(aContext, database));
		this.list.add(new ScreenOrientationSensor(aContext, database));
		this.list.add(new MyProximitySensor(aContext, database));
		this.list.add(new ScreenOnOffSensor(aContext, database));
		this.list.add(new MyAccelerometerSensor(aContext, database));
		this.list.add(new MyGyroscopeSensor(aContext, database));
		this.list.add(new AccessibilitySensor(aContext, database));
		this.list.add(new MyLightSensor(aContext, database));
		this.list.add(new InteractionLogSensor(aContext, database));
		this.list.add(new NotificationSensor(aContext, database));
		this.list.add(new BluetoothSensor(aContext, database, sensitiveDataSalt));
		this.list.add(new ConnectedWifiSensor(aContext, database, sensitiveDataSalt));

		SensorDatabaseHelper db = new SensorDatabaseHelper(pContext);

		for (AbstractSensor s : this.list)
			db.addIfNotExists(s);

		for (AbstractSensor s : this.list)
			s.setEnabled(db.getSensorData(s));

		db.close();
	}

	public List<AbstractSensor> getOrInitializeList(Context pContext, AppDatabase database, String sensitiveDataSalt) {
		if (this.list.isEmpty()) {
			initializeList(pContext, database, sensitiveDataSalt);
		}

		return this.list;
	}

	public List<AbstractSensor> getList(Context pContext, AppDatabase database, String sensitiveDataSalt) {
		return getOrInitializeList(pContext, database, sensitiveDataSalt);
	}

	public AbstractSensor getSensorOfType(Class<?> sensorType) {
		for (AbstractSensor sensor : list) {
			if (sensor.getClass().equals(sensorType)) {
				return sensor;
			}
		}
		return null;
	}
}
