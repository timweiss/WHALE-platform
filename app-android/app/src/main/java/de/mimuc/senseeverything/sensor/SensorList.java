package de.mimuc.senseeverything.sensor;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.mimuc.senseeverything.db.SensorDatabaseHelper;
import de.mimuc.senseeverything.sensor.implementation.AudioSampleSensor;

public class SensorList {
		
	private SensorList() {
	}
		
	public static List<AbstractSensor> getList(Context pContext) {
		List<AbstractSensor> list  = new ArrayList<>();

		Context aContext = pContext.getApplicationContext();

		list.add(new AudioSampleSensor(aContext));
		
		SensorDatabaseHelper db = new SensorDatabaseHelper(pContext);
		
		for (AbstractSensor s : list)
			db.addIfNotExists(s);
		
		for (AbstractSensor s : list)
			s.setEnabled(db.getSensorData(s));

		db.close();
			
		return list;
	}

}
