package de.mimuc.senseeverything.sensor;

import java.util.ArrayList;
import java.util.List;

import de.mimuc.senseeverything.db.SensorDatabaseHelper;
import de.mimuc.senseeverything.sensor.implementation.AccessibilitySensor;
import de.mimuc.senseeverything.sensor.implementation.AppSensor;
import de.mimuc.senseeverything.sensor.implementation.ChargingSensor;
import de.mimuc.senseeverything.sensor.implementation.MyAccelerometerSensor;
import de.mimuc.senseeverything.sensor.implementation.MyGyroscopeSensor;
import de.mimuc.senseeverything.sensor.implementation.MyLightSensor;
import de.mimuc.senseeverything.sensor.implementation.MyProximitySensor;
import de.mimuc.senseeverything.sensor.implementation.OrientationSensor;
import de.mimuc.senseeverything.sensor.implementation.RingtoneVolumeSensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOnOffSensor;
import de.mimuc.senseeverything.sensor.implementation.ScreenOrientationSensor;
import de.mimuc.senseeverything.sensor.implementation.StillAliveSensor;
import de.mimuc.senseeverything.sensor.implementation.TouchSensor;
import de.mimuc.senseeverything.sensor.implementation.WifiSensor;

import android.content.Context;

public class SensorList {
		
	private SensorList() {
	}
		
	public static List<AbstractSensor> getList(Context pContext) {
		List<AbstractSensor> list  = new ArrayList<>();

		Context aContext = pContext.getApplicationContext();

		list.add(new AccessibilitySensor(aContext));
		list.add(new MyAccelerometerSensor(aContext));
		//list.add(new ActivitySensor()); // This is not longer supported by Android
		//list.add(new AirplaneModeSensor(aContext));
		list.add(new AppSensor(aContext));
		//list.add(new AudioLevelSensor(aContext));
		list.add(new ChargingSensor(aContext));
		list.add(new MyGyroscopeSensor(aContext));
		list.add(new MyLightSensor(aContext));
		list.add(new MyProximitySensor(aContext));
		list.add(new OrientationSensor(aContext));
		list.add(new RingtoneVolumeSensor(aContext));
		list.add(new ScreenOnOffSensor(aContext));
		list.add(new ScreenOrientationSensor(aContext));
		list.add(new StillAliveSensor(aContext));
		list.add(new TouchSensor(aContext));
		list.add(new WifiSensor(aContext));
		
		SensorDatabaseHelper db = new SensorDatabaseHelper(pContext);
		
		for (AbstractSensor s : list)
			db.addIfNotExists(s);
		
		for (AbstractSensor s : list)
			s.setEnabled(db.getSensorData(s));

		db.close();
			
		return list;
	}

}
