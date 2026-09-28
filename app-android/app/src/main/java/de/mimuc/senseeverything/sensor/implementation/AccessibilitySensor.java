package de.mimuc.senseeverything.sensor.implementation;

import android.content.Context;
import android.content.Intent;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.service.accessibility.AccessibilityDataBus;
import de.mimuc.senseeverything.service.accessibility.AccessibilityLogService;

public class AccessibilitySensor extends AbstractSensor {

	private static final long serialVersionUID = 1L;

	private Context m_Context = null;
	private Intent m_Intent;

	public AccessibilitySensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = "AccessibilitySensor";
		SENSOR_NAME = "Accessibility";
		FILE_NAME = "accessibility.csv";
		m_FileHeader = "TimeUnix,Type,Class,Package,Text";
	}

	@Override
	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return false;
	}

	@Override
	public boolean availableForContinuousSampling() {
		return true;
	}

	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		m_Context = context;

		m_Intent = new Intent(m_Context, AccessibilityLogService.class);
		context.startService(m_Intent);

		// AccessibilityLogService runs in the same process, so events are handed over directly
		AccessibilityDataBus.setNameListener(line -> {
			if (m_IsRunning) {
				onLogDataItemBuffered(System.currentTimeMillis(), line);
			}
		});

		m_IsRunning = true;
	}

	@Override
	public void stop() {
		m_IsRunning = false;
		AccessibilityDataBus.setNameListener(null);
		flushBufferedLogData();
		if (m_Context == null)
			return;
		m_Context.stopService(m_Intent);
	}
}
