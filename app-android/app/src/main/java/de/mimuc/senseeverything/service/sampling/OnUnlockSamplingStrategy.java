package de.mimuc.senseeverything.service.sampling;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import de.mimuc.senseeverything.activity.CONST;
import de.mimuc.senseeverything.service.LogService;

public class OnUnlockSamplingStrategy implements SamplingStrategy {
    private final String TAG = OnUnlockSamplingStrategy.class.getSimpleName();
    private final long STOP_DURATION = 1000 * 60;

    private Handler stopHandler;

    @Override
    public void start(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        // fixme: this will stop working once the application is not visible
        // and afterwards, it will receive all events at once once the MainActivity is opened up again
        // probably we'll need another layering of the ForegroundService to allow this
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "screen locked");
                    stop(context);
                } else {
                    Log.d(TAG, "device unlocked, starting sampling");

                    if (isRunning(context))
                        return;

                    startLogService(context);
                }
            }
        }, filter);
    }

    @Override
    public void stop(Context context) {
        if (!isRunning(context))
            return;

        Intent intent = new Intent(context, LogService.class);
        context.stopService(intent);
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false).apply();

        if (stopHandler != null) {
            stopHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public boolean isRunning(Context context) {
        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        return sp.getBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, false);
    }

    private void startLogService(Context context) {
        Intent intent = new Intent(context, LogService.class);
        context.startService(intent);

        stopHandler = new Handler();
        stopHandler.postDelayed(() -> stop(context), STOP_DURATION);

        SharedPreferences sp = context.getSharedPreferences(CONST.SP_LOG_EVERYTHING, Activity.MODE_PRIVATE);
        sp.edit().putBoolean(CONST.KEY_LOG_EVERYTHING_RUNNING, true).apply();
    }
}
