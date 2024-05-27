package de.mimuc.senseeverything.service.sampling;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import de.mimuc.senseeverything.activity.CONST;
import de.mimuc.senseeverything.service.LogService;

public class OnUnlockSamplingStrategy implements SamplingStrategy {
    private final String TAG = OnUnlockSamplingStrategy.class.getSimpleName();
    private final long STOP_DURATION = 1000 * 60;

    private Handler stopHandler;
    private Messenger logServiceMessenger;

    @Override
    public void start(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        startLogService(context);

        // fixme: this will stop working once the application is not visible
        // and afterwards, it will receive all events at once once the MainActivity is opened up again
        // probably we'll need another layering of the ForegroundService to allow this
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "device locked, stopping sampling");
                    stopSampling();
                } else {
                    Log.d(TAG, "device unlocked, starting sampling");
                    // fixme: handle condition where logging might still be running?
                    startSampling();
                }
            }
        }, filter);
    }

    @Override
    public void stop(Context context) {
        if (!isRunning(context))
            return;

        try {
            context.unbindService(serviceConnection);
            Intent intent = new Intent(context, LogService.class);
            context.stopService(intent);

            SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
            sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false).apply();

            if (stopHandler != null) {
                stopHandler.removeCallbacksAndMessages(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to stop service", e);
        }

    }

    @Override
    public boolean isRunning(Context context) {
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        return sp.getBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false);
    }

    private void startLogService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        context.startService(intent);

        stopHandler = new Handler();
        stopHandler.postDelayed(this::stopSampling, STOP_DURATION);

        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, true).apply();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "LogService disconnected");
            logServiceMessenger = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "LogService connected");
            logServiceMessenger = new Messenger(service);
        }
    };

    private void startSampling() {
        if (logServiceMessenger == null)
        {
            Log.e(TAG, "logService is null");
            return;
        }

        try {
            logServiceMessenger.send(Message.obtain(null, LogService.START_SENSORS, 0, 0));
        } catch (Exception e) {
            Log.e(TAG, "failed to send message", e);
        }
    }

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
