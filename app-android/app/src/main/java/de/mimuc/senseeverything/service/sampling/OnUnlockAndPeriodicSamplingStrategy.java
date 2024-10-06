package de.mimuc.senseeverything.service.sampling;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Calendar;

import de.mimuc.senseeverything.activity.CONST;
import de.mimuc.senseeverything.service.LogService;

public class OnUnlockAndPeriodicSamplingStrategy implements SamplingStrategy {
    private final String TAG = OnUnlockAndPeriodicSamplingStrategy.class.getSimpleName();

    private Messenger logServiceMessenger;

    @Override
    public void start(Context context) {
        startLogService(context);
    }

    @Override
    public void stop(Context context) {
        if (!isRunning(context))
            return;

        if (logServiceMessenger != null) {
            try {
                logServiceMessenger.send(Message.obtain(null, LogService.STOP_SENSORS, 0, 0));
            } catch (RemoteException e) {
                Log.e(TAG, "failed to send message", e);
            }
        } else {
            Log.e(TAG, "logServiceMessenger is null");
        }

        try {
            context.unbindService(serviceConnection);
        } catch (Exception e) {
            Log.e(TAG, "failed to unbind service", e);
        }

        try {
            context.stopService(new Intent(context, LogService.class));
        } catch (Exception e) {
            Log.e(TAG, "failed to stop service", e);
        }
    }

    @Override
    public void pause(Context context) {
        if (!isRunning(context))
            return;

        try {
            logServiceMessenger.send(Message.obtain(null, LogService.SLEEP_MODE, 0, 0));
            scheduleResumeTimer(context);
        } catch (Exception e) {
            Log.e(TAG, "failed to send message", e);
        }
    }

    @Override
    public void resume(Context context) {
        if (!isRunning(context))
            return;

        try {
            logServiceMessenger.send(Message.obtain(null, LogService.LISTEN_LOCK_UNLOCK_AND_PERIODIC, 0, 0));
        } catch (Exception e) {
            Log.e(TAG, "failed to send message", e);
        }
    }

    @Override
    public boolean isRunning(Context context) {
        // fixme: even if a messenger exists, the service could still be dead
        return logServiceMessenger != null;
    }

    private void scheduleResumeTimer(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ResumeSamplingReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAtMillis = System.currentTimeMillis() + getNextDayTime();

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                    );
                } else {
                    Log.e(TAG, "Cannot schedule exact alarms");
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
            }
            Log.i(TAG, "Scheduled resume timer at " + triggerAtMillis);
        } else {
            Log.e(TAG, "AlarmManager is null");
        }
    }

    private long getNextDayTime() {
        Calendar calendar = Calendar.getInstance();

        // we'll restart the service at 6:00 the next day
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    private void startLogService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        context.startService(intent);

        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, true).apply();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "LogService disconnected");
            logServiceMessenger = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "LogService connected");
            logServiceMessenger = new Messenger(service);

            // we can only tell it to listen once we've connected to the LogService
            try {
                logServiceMessenger.send(Message.obtain(null, LogService.LISTEN_LOCK_UNLOCK_AND_PERIODIC, 0, 0));
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    };

    private void stopSampling() {
        if (logServiceMessenger == null)
        {
            Log.e(TAG, "logService is null");
            return;
        }

        try {
            logServiceMessenger.send(Message.obtain(null, LogService.STOP_SENSORS, 0, 0));
        } catch (Exception e) {
            Log.e(TAG, "failed to send message", e);
        }
    }
}
