package de.mimuc.senseeverything.sensor.implementation;

import java.util.Random;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.handler.HandlerListener;
import de.mimuc.senseeverything.handler.TouchHandler;
import de.mimuc.senseeverything.logging.WHALELog;
import de.mimuc.senseeverything.sensor.AbstractSensor;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;

public class TouchSensor extends AbstractSensor implements HandlerListener {

	private static final long serialVersionUID = 1L;
	
	private TouchHandler m_ServiceHandler;
	
	private long count;

	public TouchSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = getClass().getSimpleName();
		SENSOR_NAME = "Touch Log";
		FILE_NAME = "touch.csv";
		m_FileHeader = "TimeUnix,Finger,Event,X,Y,Prs,Q";
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
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;
		
		// Start up the thread running the service.  Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block.  We also make it
		// background priority so CPU-intensive work will not disrupt our UI.
		HandlerThread thread = new HandlerThread("ServiceStartArguments",
		android.os.Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		m_ServiceHandler = new TouchHandler(thread.getLooper());
		m_ServiceHandler.addListener(this);
		// For each start request, send a message to start a job and deliver the
		// start ID so we know which request we're stopping when we finish the job
		Message msg = m_ServiceHandler.obtainMessage();
		msg.arg1 = (new Random()).nextInt(Integer.MAX_VALUE);
		m_ServiceHandler.sendMessage(msg);
		
		m_IsRunning = true;
	}

	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			closeDataSource();
			m_ServiceHandler.stop();
			m_ServiceHandler = null;
		}
	}

	@Override
	public void sendMessage(String msg) {
		if(m_IsRunning) {
			WHALELog.INSTANCE.d(TAG, "#"+msg);
			onLogDataItem(System.currentTimeMillis(), msg);
		}
		else
		{
			WHALELog.INSTANCE.d(TAG, "not running");
		}
	}

}
