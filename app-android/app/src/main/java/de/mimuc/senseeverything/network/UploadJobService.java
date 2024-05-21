package de.mimuc.senseeverything.network;

import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.common.api.Api;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.api.ApiClient;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.LogData;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.FlowCollector;

@AndroidEntryPoint
public class UploadJobService extends JobService {
    @Inject DataStoreManager dataStore;

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

        // convert to json
        JSONArray jsonReadings = new JSONArray();
        for (LogData logData : data) {
            try {
                JSONObject o = new JSONObject();
                o.put("sensorType", logData.sensorName);
                o.put("data", logData.data);
                jsonReadings.put(o);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        ApiClient client = ApiClient.getInstance(getApplicationContext());

        dataStore.tokenBlocking((token) -> {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);

            client.postArray("https://siapi.timweiss.dev/v1/reading/batch", jsonReadings, headers
                    , response -> {
                        Log.d("VolleyResponse", response.toString());
                        for (LogData logData : data){
                            logData.synced = true;
                        }

                        AsyncTask.execute(() -> {
                            db.logDataDao().updateLogData(data.toArray(new LogData[data.size()]));
                            Log.i(TAG,"batch synced successful");

                            // next batch
                            syncNextNActivities(batchSize);
                        });
                    }, error -> Log.e("VolleyError", error.toString()));

            return Unit.INSTANCE;
        });
    }

}