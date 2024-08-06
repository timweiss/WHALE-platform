package de.mimuc.senseeverything.service;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import dagger.hilt.android.HiltAndroidApp;
import de.mimuc.senseeverything.service.esm.EsmHandler;
import de.mimuc.senseeverything.service.sampling.OnUnlockSamplingStrategy;
import de.mimuc.senseeverything.service.sampling.PeriodicSamplingStrategy;
import de.mimuc.senseeverything.service.sampling.SamplingManager;

@HiltAndroidApp
public class SEApplicationController extends Application {

    private static final String TAG = "SEApplicationController";

    /**
     * A singleton instance of the application class for easy access in other places
     */
    protected static SEApplicationController sInstance;

    /**
     * Global request queue for Volley
     */
    private RequestQueue mRequestQueue;

    private SamplingManager mSamplingManager;

    private EsmHandler mEsmHandler;

    /**
     * Method to access the ApplicationController singleton instance
     * @return ApplicationController singleton instance
     */
    public static synchronized SEApplicationController getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        sInstance = this;
        super.onCreate();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("SEChannel", "SenseEverything", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("SenseEverything Notifications");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Method to lazy initialize the request queue, the queue instance will be created when it is accessed for the first time
     * @return The Volley Request queue, the queue will be created if it is null
     */
    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        return mRequestQueue;
    }

    /**
     * Adds the specified request to the global queue using the Default TAG.
     *
     * @param req
     */
    public <T> void addToRequestQueue(Request<T> req) {
        // set the default tag if tag is empty
        req.setTag(TAG);

        getRequestQueue().add(req);
    }

    public SamplingManager getSamplingManager() {
        if (mSamplingManager == null) {
            mSamplingManager = new SamplingManager(new OnUnlockSamplingStrategy());
        }
        return mSamplingManager;
    }

    public EsmHandler getEsmHandler() {
        if (mEsmHandler == null) {
            mEsmHandler = new EsmHandler();
        }
        return mEsmHandler;
    }
}
