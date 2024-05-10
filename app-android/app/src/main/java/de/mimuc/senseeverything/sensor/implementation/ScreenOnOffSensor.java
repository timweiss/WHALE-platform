package de.mimuc.senseeverything.sensor.implementation;

import de.mimuc.senseeverything.sensor.AbstractSensor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;

public class ScreenOnOffSensor extends AbstractSensor {
	
	private static final long serialVersionUID = 1L;
	
	private BroadcastReceiver mReceiver;
	private Context m_context;
	
	private boolean wasScreenOn = true;
	
	public ScreenOnOffSensor(Context applicationContext) {
		super(applicationContext);
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Screen On/Off";
		FILE_NAME = "screen_on_off.csv";
		m_FileHeader = "TimeUnix,Value";
	}
	
	public View getSettingsView(Context context) {
		return null;
	}
	
	public boolean isAvailable(Context context) {
		return true;
	}
	
	@Override
	public void start(Context context) {	
		super.start(context);
		Long t = System.currentTimeMillis();
		if (!m_isSensorAvailable)
			return;
		
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		boolean isScreenOn = pm.isScreenOn();
		this.m_context = context;
		try {			
			if(isScreenOn) {
				onLogDataItem(t, "on");
			} else {
				onLogDataItem(t, "off");
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		mReceiver = new ScreenReceiver();
		
		try{
			context.unregisterReceiver(mReceiver);
		} catch (Exception e) {
			//Not Registered
		}
		context.registerReceiver(mReceiver, filter);
		
		m_IsRunning = true;
	}
	
	@Override
	public void stop() {
		if(m_IsRunning) {
			m_IsRunning = false;
			m_context.unregisterReceiver(mReceiver);	
			try {
				closeDataSource();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
			}	
		}	
	}
	
	public class ScreenReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Long t = System.currentTimeMillis();
			if(m_IsRunning) {
				if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
					onLogDataItem(t,"off");
					wasScreenOn = false;
				} else if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
					onLogDataItem(t,"on");
					wasScreenOn = true;
				}
			}
			
		}		
	}
	
    public void onPause() {
		Long t = System.currentTimeMillis();
    	if(m_IsRunning) {
        // WHEN THE SCREEN IS ABOUT TO TURN OFF
	        if (wasScreenOn) {
	            // THIS IS THE CASE WHEN ONPAUSE() IS CALLED BY THE SYSTEM DUE TO A SCREEN STATE CHANGE
				onLogDataItem(System.currentTimeMillis(), "off");
	        }
    	}
    }
 
    public void onResume() {
		Long t = System.currentTimeMillis();
    	if(m_IsRunning) {
	        // ONLY WHEN SCREEN TURNS ON
	        if (!wasScreenOn) {
	            // THIS IS WHEN ONRESUME() IS CALLED DUE TO A SCREEN STATE CHANGE
				onLogDataItem(System.currentTimeMillis(), "on");
	        }
    	}
    }
	
}