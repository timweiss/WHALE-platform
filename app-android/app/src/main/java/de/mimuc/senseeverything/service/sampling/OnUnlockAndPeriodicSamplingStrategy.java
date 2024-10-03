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

public class OnUnlockAndPeriodicSamplingStrategy implements SamplingStrategy {
    private final String TAG = OnUnlockAndPeriodicSamplingStrategy.class.getSimpleName();
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

        // stopHandler = new Handler();
        // stopHandler.postDelayed(this::stopSampling, STOP_DURATION);

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
