package de.mimuc.senseeverything.sensor.implementation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class ScreenOrientationSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;
	
	private BroadcastReceiver m_Receiver;
	private Context m_context;
	
	public boolean m_WasScreenOn = true;

	public ScreenOrientationSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Screen Orientation";
		FILE_NAME = "screen_orientation.csv";
		m_FileHeader = "TimeUnix,Value";
	}
	
	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return false;
	}

	@Override
	public void start(Context pContext) {
		super.start(pContext);
		Long t = System.currentTimeMillis();
		if (!m_isSensorAvailable)
			return;
		
		m_context = pContext;

		try {
			if(m_context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
				onLogDataItem(t, "LANDSCAPE");
			}
			else if(m_context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
				onLogDataItem(t, "PORTRAIT");
			} else {
				onLogDataItem(t, "UNDEFINED");
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
		m_Receiver = new ScreenReceiver();
		try{
			m_context.unregisterReceiver(m_Receiver);
		} catch (Exception e) {
			//Not Registered
		}
		m_context.registerReceiver(m_Receiver, filter);
		
		m_IsRunning = true;
	}
	
	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			m_context.unregisterReceiver(m_Receiver);	
			closeDataSource();
		}	
	}
	
	public class ScreenReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Long t = System.currentTimeMillis();
			if(m_IsRunning) {
				if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED) ) {
	                try {
		                if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
							onLogDataItem(t, "LANDSCAPE");
		                }
		                else if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
							onLogDataItem(t, "PORTRAIT");
		                } else {
							onLogDataItem(t, "UNDEFINED");
		                }
	                } catch (Exception e) {
						Log.e(TAG, e.toString());
					}
	            }				
			}			
		}		
	}	
}