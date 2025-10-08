package de.mimuc.senseeverything.sensor.implementation.deepactivity;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import de.mimuc.senseeverything.logging.WHALELog;

public class WindowContentAccessibilityService extends AccessibilityService {

    public static final String TAG = "WindowContentAccess.Se.";
    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        AccessibilityNodeInfo accessibilityNodeInfo = getRootInActiveWindow();
        if (accessibilityNodeInfo != null) {
            WHALELog.INSTANCE.i(TAG, accessibilityNodeInfo.toString());
        } else {
            WHALELog.INSTANCE.i(TAG,"accessibility Node was null");
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        WHALELog.INSTANCE.i(TAG,"onServiceConnected()");
    }


    @Override
    public void onInterrupt() {
        WHALELog.INSTANCE.i(TAG,"onInterrupt()");
    }
}
