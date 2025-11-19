package de.mimuc.senseeverything.service;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;
import androidx.work.WorkerFactory;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import javax.inject.Inject;

import androidx.room.Room;

import dagger.hilt.android.HiltAndroidApp;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.service.esm.EsmHandler;

@HiltAndroidApp
public class SEApplicationController extends Application implements Configuration.Provider {

    private static final String TAG = "SEApplicationController";

    /**
     * A singleton instance of the application class for easy access in other places
     */
    protected static SEApplicationController sInstance;

    /**
     * Global request queue for Volley
     */
    private RequestQueue mRequestQueue;

    private EsmHandler mEsmHandler;

    private AppDatabase mAppDatabase;

    /**
     * Method to access the ApplicationController singleton instance
     * @return ApplicationController singleton instance
     */
    public static synchronized SEApplicationController getInstance() {
        return sInstance;
    }

    @Inject
    HiltWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        sInstance = this;
        super.onCreate();

        NotificationChannel channel = new NotificationChannel("SEChannel", "WHALE", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("WHALE Notifications");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
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

    /**
     * Method to lazy initialize the app database, the database instance will be created when it is accessed for the first time
     * @return The AppDatabase instance, the database will be created if it is null
     */
    public AppDatabase getAppDatabase() {
        if (mAppDatabase == null) {
            mAppDatabase = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class,
                    "senseeverything-roomdb"
            )
                    .fallbackToDestructiveMigration()
                    .enableMultiInstanceInvalidation()
                    .build();
        }
        return mAppDatabase;
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .setWorkerFactory(workerFactory)
                .build();
    }
}
