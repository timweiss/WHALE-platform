package de.mimuc.senseeverything.service;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import de.mimuc.senseeverything.activity.CONST;

public class SamplingManager {
    public void startSampling(Context context) {
        startLogService(context);
    }

    public void stopSampling(Context context) {
        stopLogService(context);
    }

    public static boolean isLogServiceRunning(Context context) {
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        return sp.getBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false);
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent alarmIntent = new Intent(context.getApplicationContext(), LogService.class);
        return PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void startLogService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.startService(intent);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        long m_AlarmInterval = 60 * 1000;
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + m_AlarmInterval, m_AlarmInterval, pendingIntent);
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, true).apply();
    }

    private void stopLogService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.stopService(intent);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        alarmManager.cancel(pendingIntent);
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false).apply();
    }
}
