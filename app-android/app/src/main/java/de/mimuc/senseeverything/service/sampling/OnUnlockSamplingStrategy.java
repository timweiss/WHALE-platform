package de.mimuc.senseeverything.service.sampling;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
        startLogService(context);
    }

    @Override
    public void stop(Context context) {
        if (!isRunning(context))
            return;

        try {
            context.unbindService(serviceConnection);
        } catch (Exception e) {
            Log.e(TAG, "could not unbind context from connection, could be because it's a new activity", e);
        }

        // we should continue trying to stop the service even if unbinding fails (as it could be a new activity)

        try {
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
    public void pause(Context context) {
        if (!isRunning(context))
            return;

        try {
            logServiceMessenger.send(Message.obtain(null, LogService.SLEEP_MODE, 0, 0));
        } catch (Exception e) {
            Log.e(TAG, "failed to send message", e);
        }
    }

    @Override
    public boolean isRunning(Context context) {
        // fixme: even if a messenger exists, the service could still be dead
        return logServiceMessenger != null;
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

            // we can only tell it to listen once we've connected to the LogService
            try {
                logServiceMessenger.send(Message.obtain(null, LogService.LISTEN_LOCK_UNLOCK, 0, 0));
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
