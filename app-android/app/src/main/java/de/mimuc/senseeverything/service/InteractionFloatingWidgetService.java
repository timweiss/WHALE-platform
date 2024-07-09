package de.mimuc.senseeverything.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import de.mimuc.senseeverything.R;

public class InteractionFloatingWidgetService extends Service {
    private WindowManager windowManager;
    private View floatingWidget;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate the floating widget layout
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        Button yesButton = floatingWidget.findViewById(R.id.yes_button);
        Button noButton = floatingWidget.findViewById(R.id.no_button);
        TextView questionText = floatingWidget.findViewById(R.id.interaction_question);

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
    }

    private void answerNo() {
        Log.d("FloatingWidget", "No button clicked");
        floatingWidget.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingWidget != null) windowManager.removeView(floatingWidget);
    }
}
