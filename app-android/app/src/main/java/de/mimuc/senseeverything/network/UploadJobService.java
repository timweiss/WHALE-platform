package de.mimuc.senseeverything.network;

import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.room.Room;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.LogData;

public class UploadJobService extends JobService {

    public static final String TAG = "UploadJobService";

    private final ExecutorService myExecutor;
    private AppDatabase db;

    public UploadJobService() {
        myExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.i(TAG,"onStartJob()");

        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "senseeverything-roomdb").build();

        myExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // do stuff here
                syncNextNActivities(10); // TODO recursiveness / loop missing
            }
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "onStopJob()");
        return false;
    }

    private void syncNextNActivities(int batchSize){
        List<LogData> data = db.logDataDao().getNextNUnsynced(batchSize);
        Log.i(TAG,"found data items: "+data.size());
        if (data.size() == 0){
            return;
        }

        SenseEverythingDataBackendWrapper wrapper = new SenseEverythingDataBackendWrapper(data);


        UploadRequest uploadRequest = new UploadRequest(new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG,response);
                myExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject o = new JSONObject(response);
                            if(o.has("result") && "1".equals(o.getString("result"))){
                                // set data items to synced
                                for(LogData logData : wrapper.datas){
                                    logData.synced = true;

                                }
                                db.logDataDao().updateLogData(wrapper.datas.toArray(new LogData[wrapper.datas.size()]));
                                Log.i(TAG,"batch synced successful");

                                // next batch
                                syncNextNActivities(batchSize);
                            }
                            else {
                                Log.w(TAG,"Sync not successful");
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing response", e);
                        }
                    }
                });


            }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.w(TAG,"request resulted in error",error);
                }
            },
                wrapper);

        uploadRequest.addToRequestQueue();


        Log.i(TAG,"request sent");
    }

}