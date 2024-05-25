package de.mimuc.senseeverything.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.mimuc.senseeverything.service.SEApplicationController;
import de.mimuc.senseeverything.service.SamplingManager;

public class BootUpReceiver extends BroadcastReceiver{
	
  	@Override
	public void onReceive(Context context, Intent intent) {
  		if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")){
  			if(SamplingManager.isLogServiceRunning(context)) {
				SEApplicationController.getInstance().getSamplingManager().startSampling(context);
  				// MainActivity.startLogService(context);
  			} 
  		}
	}
}