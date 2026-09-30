package de.mimuc.senseeverything.sensor;

import android.content.Context;

import java.io.Serializable;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.models.LogData;
import de.mimuc.senseeverything.logging.WHALELog;

public abstract class AbstractSensor implements Serializable  {

	protected String TAG;
	private static final long serialVersionUID = 1L;

	/**
	 * One writer for all sensors of a process, so rows from different sensors are committed together
	 * (one transaction and one cross-process invalidation per batch instead of per row).
	 */
	private static BufferedLogWriter sharedWriter;

	protected String SENSOR_NAME;
	private boolean m_IsEnabled = true;
	protected String FILE_NAME;
	protected String m_FileHeader;

	protected boolean m_isSensorAvailable = false;

	private final AppDatabase db;

	protected AbstractSensor(Context applicationContext, AppDatabase database) {
		db = database;
	}

	protected AbstractSensor(Context applicationContext, AppDatabase database, String salt) {
		db = database;
		sensitiveDataSalt = salt;
	}

	protected boolean m_IsRunning = false;

	protected String sensitiveDataSalt = "changemepleeease";

	public String getSensorName() {
		return SENSOR_NAME;
	}

	public boolean isEnabled() {
		return m_IsEnabled;
	}

	public void setEnabled(boolean selected) {
		this.m_IsEnabled = selected;
	}

	abstract public boolean isAvailable(Context context);

	/** Indicates whether the sensor can be used for periodic sampling, where it will be called at regular intervals. */
	abstract public boolean availableForPeriodicSampling();

	public boolean availableForContinuousSampling() {
		return false;
	}

	public void start(Context context){
		m_isSensorAvailable = isAvailable(context);
		if (!m_isSensorAvailable)
			WHALELog.INSTANCE.i(TAG, "Sensor not available");
	}

	/** Queues a row; rows are written in batches (see {@link BufferedLogWriter}). */
	protected void onLogDataItem(Long timestamp, String data){
		getWriter().add(new LogData(timestamp, SENSOR_NAME, data));
	}

    protected void onLogDataItem(Long timestamp, String data, String subsensor){
		getWriter().add(new LogData(timestamp, SENSOR_NAME + "+" + subsensor, data));
    }

	protected void onLogDataItemWithFile(Long timestamp, String data, String fileName) {
		getWriter().add(new LogData(timestamp, SENSOR_NAME, data, true, fileName));
	}

	/** Writes queued rows now, e.g. when a sensor stops. */
	protected void flushLogData() {
		getWriter().flush();
	}

	private BufferedLogWriter getWriter() {
		synchronized (AbstractSensor.class) {
			if (sharedWriter == null) {
				sharedWriter = new BufferedLogWriter(db.logDataDao(), 50, 2000L);
			}
			return sharedWriter;
		}
	}

	protected void closeDataSource() {

	}

	abstract public void stop();

	public boolean isRunning() {
		return m_IsRunning;
	}

	protected AppDatabase getDatabase() { return db; }
}
