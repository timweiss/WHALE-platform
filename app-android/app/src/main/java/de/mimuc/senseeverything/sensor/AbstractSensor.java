package de.mimuc.senseeverything.sensor;

import java.io.Serializable;

import de.mimuc.senseeverything.data.SensorReadingDiskDataSource;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.LogData;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import androidx.room.Room;

public abstract class AbstractSensor implements Serializable  {

	protected String TAG;
	private static final long serialVersionUID = 1L;
	
	protected String SENSOR_NAME;
	private boolean m_IsEnabled = true;
	protected String FILE_NAME;
	protected String m_FileHeader;

	private String m_Settings = "";

	protected boolean m_isSensorAvailable = false;

	private final AppDatabase db;
	
	protected AbstractSensor(Context applicationContext) {
		db = Room.databaseBuilder(applicationContext,
				AppDatabase.class, "senseeverything-roomdb").build();

	}
	
	protected boolean m_IsRunning = false;

	private SensorReadingDiskDataSource dataSource;
	
	public String getSensorName() {
		return SENSOR_NAME;
	}
	
	public boolean isEnabled() {
		return m_IsEnabled;
	}

	public void setEnabled(boolean selected) {
		this.m_IsEnabled = selected;
	}
	
	public int getSettingsState() {
		return 0;
	}
	
	public String getSettings() {
		return m_Settings;
	}
	
	abstract public View getSettingsView(Context context);
	
	abstract public boolean isAvailable(Context context);

	abstract public boolean availableForPeriodicSampling();
	
	public void start(Context context){
		m_isSensorAvailable = isAvailable(context);
		if (!m_isSensorAvailable)
			Log.i(TAG, "Sensor not available");

		dataSource = new SensorReadingDiskDataSource(context, getSensorName());
	}

	protected void onLogDataItem(Long timestamp, String data){
		// Log.d(TAG, "onLogDataItem from " + SENSOR_NAME + ": " + data + " at " + timestamp);

		AsyncTask.execute(() -> {
			db.logDataDao().insertAll(new LogData(timestamp,SENSOR_NAME, data));
		});
	}

	public void tryLogStringData(String data) throws SensorNotRunningException {
		if (m_IsRunning) {
			onLogDataItem(System.currentTimeMillis(), data);
		} else {
			throw new SensorNotRunningException();
		}
	}

	protected void onLogDataItemWithFile(Long timestamp, String data, String fileName) {
		AsyncTask.execute(() -> {
			db.logDataDao().insertAll(new LogData(timestamp, SENSOR_NAME, data, true, fileName));
		});
	}

	protected void closeDataSource() {
		dataSource.close();
	}
	
	abstract public void stop();

	public boolean isRunning() {
		return m_IsRunning;
	}
	
}
