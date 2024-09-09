package de.mimuc.senseeverything.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.R;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.sensor.SingletonSensorList;
import de.mimuc.senseeverything.sensor.implementation.InteractionLogSensor;
import kotlin.Unit;

@AndroidEntryPoint
public class InteractionFloatingWidgetService extends Service {
    enum InteractionLogType {
        Asked,
        Start,
        End,
        Confirm,
        NoInteraction
    }

    private WindowManager windowManager;
    private View floatingWidget;

    private String TAG = "InteractionFloatingWidgetService";

    private Messenger logServiceMessenger;

    @Inject
    DataStoreManager dataStore;

    @Inject
    SingletonSensorList singletonSensorList;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // bind to LogService
        Intent intent = new Intent(this, LogService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate the floating widget layout
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        Button yesButton = floatingWidget.findViewById(R.id.yes_button);
        Button noButton = floatingWidget.findViewById(R.id.no_button);
        TextView questionText = floatingWidget.findViewById(R.id.interaction_question);

        // Set the question text
        dataStore.getInInteractionSync((inInteraction) -> {
            if (inInteraction) {
                questionText.setText(R.string.still_interacting);
            } else {
                questionText.setText(R.string.are_you_interacting);
            }
            return Unit.INSTANCE;
        });

        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                answerYes();
            }
        });

        noButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                answerNo();
            }
        });

        // Set up layout parameters
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // For Android O and above
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        // put it in the top right corner, a bit lower than the status bar
        params.gravity = android.view.Gravity.TOP | Gravity.END;
        params.verticalMargin = 0.075f;

        // Add the view to the window
        windowManager.addView(floatingWidget, params);
        Log.d("FloatingWidget", "Floating widget added to screen");

        // Handle the widget's movement and interactions
        floatingWidget.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            // Handle click event
                        }
                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingWidget, params);
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });
    }

    private void answerYes() {
        Log.d("FloatingWidget", "Yes button clicked");
        floatingWidget.setVisibility(View.GONE);

        dataStore.getInInteractionSync((inInteraction) -> {
            if (inInteraction) {
                logInteractionMessage(InteractionLogType.Confirm);
            } else {
                logInteractionMessage(InteractionLogType.Start);
            }

            dataStore.setInInteractionSync(true);
            return Unit.INSTANCE;
        });
    }

    private void answerNo() {
        Log.d("FloatingWidget", "No button clicked");
        floatingWidget.setVisibility(View.GONE);
        dataStore.getInInteractionSync((inInteraction) -> {
            if (inInteraction) {
                logInteractionMessage(InteractionLogType.End);
                SEApplicationController.getInstance().getEsmHandler().initializeTriggers(dataStore);
                SEApplicationController.getInstance().getEsmHandler().handleEvent("interactionEnd", this, dataStore);
            } else {
                logInteractionMessage(InteractionLogType.NoInteraction);
            }

            dataStore.setInInteractionSync(false);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingWidget != null) windowManager.removeView(floatingWidget);
        unbindService(connection);
    }

    private void logInteractionMessage(InteractionLogType type) {
        switch (type) {
            case Asked:
                sendDataToLogService("asked");
                break;
            case Start:
                sendDataToLogService("start");
                break;
            case End:
                sendDataToLogService("end");
                break;
            case Confirm:
                sendDataToLogService("confirm");
                break;
            case NoInteraction:
                sendDataToLogService("noInteraction");
                break;
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logServiceMessenger = new Messenger(service);
            logInteractionMessage(InteractionLogType.Asked); // needs to be logged here because otherwise we have a race condition
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logServiceMessenger = null;
        }
    };

    private void sendDataToLogService(String message) {
        if (logServiceMessenger != null) {
            Message msg = Message.obtain(null, LogService.SEND_SENSOR_LOG_DATA);
            Bundle bundle = new Bundle();
            bundle.putString("sensorData", message);
            bundle.putString("sensorName", InteractionLogSensor.class.getName());
            msg.setData(bundle);

            try {
                logServiceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message to LogService", e);
            }
        }
    }
}
