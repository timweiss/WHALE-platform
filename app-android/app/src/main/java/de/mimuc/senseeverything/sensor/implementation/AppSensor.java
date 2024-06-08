package de.mimuc.senseeverything.sensor.implementation;

import de.mimuc.senseeverything.sensor.AbstractSensor;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.view.View;

@Deprecated
/**
 * @deprecated AppSensor uses deprecated APIs that cannot be used anymore, instead AccessibilitySensor can be used to infer app usage.
 */
public class AppSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;

	public AppSensor(Context applicationContext) {
		super(applicationContext);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "App";
		FILE_NAME = "app.csv";
		m_FileHeader = "TimeUnix,Package";
	}


	@Override
	public View getSettingsView(Context context) {
		return null;
	}

	@Override
	public boolean isAvailable(Context context) {
		return true;
	}

	private String getForegroundApp(Context context) {
		try {
			// fixme: this has been deprecated and always just yields de.mimuc.senseeverything
			ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return activityManager.getRunningTasks(1).get(0).topActivity.getPackageName();
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void start(Context context) {
		super.start(context);

        Long t = System.currentTimeMillis();
		if (!m_isSensorAvailable)
			return;

		String info = getForegroundApp(context);

		if (info == null) {
			onLogDataItem(t, "NULL");
		} else {
			onLogDataItem(t, info);
		}
		m_IsRunning = true;
	}

	@Override
	public void stop() {
		if (m_IsRunning) {
			m_IsRunning = false;
			closeDataSource();
		}
	}

}
