package de.mimuc.senseeverything.service;

import android.app.Application;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import dagger.hilt.android.HiltAndroidApp;

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

}
