package de.mimuc.senseeverything.service.sampling;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import de.mimuc.senseeverything.activity.CONST;
import de.mimuc.senseeverything.service.LogService;

public class PeriodicSamplingStrategy implements SamplingStrategy {
    @Override
    public void start(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.startService(intent);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        long m_AlarmInterval = 60 * 1000;
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + m_AlarmInterval, m_AlarmInterval, pendingIntent);
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, true).apply();
    }

    @Override
    public void stop(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.stopService(intent);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        alarmManager.cancel(pendingIntent);
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false).apply();
    }

    @Override
    public void pause(Context context) {
        // unsupported
    }

    @Override
    public void resume(Context context) {
        // unsupported
    }

    @Override
    public boolean isRunning(Context context) {
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        return sp.getBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false);
    }

    private PendingIntent getPendingIntent(Context context) {
        Intent alarmIntent = new Intent(context.getApplicationContext(), LogService.class);
        return PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
