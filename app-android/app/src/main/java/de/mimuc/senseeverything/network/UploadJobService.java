package de.mimuc.senseeverything.network;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.util.Log;

import androidx.room.Room;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import de.mimuc.senseeverything.api.ApiClient;
import de.mimuc.senseeverything.data.DataStoreManager;
import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.db.LogData;
import kotlin.Unit;

@AndroidEntryPoint
public class UploadJobService extends JobService {
    @Inject
    DataStoreManager dataStore;

    public static final String TAG = "UploadJobService";

    private final ExecutorService myExecutor;
    private AppDatabase db;

    public UploadJobService() {
        myExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.i(TAG, "onStartJob()");

        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "senseeverything-roomdb").build();

        myExecutor.execute(() -> {
            // do stuff here
            syncNextNActivities(60, jobParameters);
        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "onStopJob()");
        myExecutor.shutdown();
        return false;
    }

    private void syncNextNActivities(int batchSize, JobParameters jobParameters) {
        List<LogData> data = db.logDataDao().getNextNUnsynced(batchSize);
        Log.i(TAG, "found data items: " + data.size());
        if (data.isEmpty()) {
            jobFinished(jobParameters, false);
            myExecutor.shutdown();
            Log.i(TAG, "finished");
            return;
        }

        // convert to json
        JSONArray jsonReadings = new JSONArray();
        for (LogData logData : data) {
            try {
                JSONObject o = new JSONObject();
                o.put("sensorType", logData.sensorName);
                o.put("data", logData.data);
                o.put("timestamp", logData.timestamp + ""); // fixme: we might need to resolve the timezone here?
                jsonReadings.put(o);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        ApiClient client = ApiClient.getInstance(getApplicationContext());

        dataStore.getTokenSync((token) -> {
            if (token.isEmpty()) {
                Log.e(TAG, "no token found");
                return Unit.INSTANCE;
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);

            client.postArray("https://sisensing.medien.ifi.lmu.de/v1/reading/batch", jsonReadings, headers
                    , response -> {
                        Log.d(TAG, response.toString());
                        for (LogData logData : data) {
                            logData.synced = true;
                        }

                        AsyncTask.execute(() -> {
                            try {
                                db.logDataDao().updateLogData(data.toArray(new LogData[data.size()]));
                                Log.i(TAG, "batch synced successful");

                                uploadFilesFromBatch(data, response, batchSize);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in AsyncTask: " + e.getMessage(), e);
                            } finally {
                                // Next batch
                                syncNextNActivities(batchSize, jobParameters);
                                Log.i(TAG, "Next batch sync initiated");
                            }
                        });
                    }, error -> Log.e(TAG, error.toString()));

            return Unit.INSTANCE;
        });
    }

    private void uploadFilesFromBatch(List<LogData> data, JSONArray backendReadings, int batchSize) throws InterruptedException {
        // count files to be synced
        int filesToSync = 0;
        for (LogData logData : data) {
            if (logData.hasFile) {
                filesToSync++;
            }
        }

        if (filesToSync == 0) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(filesToSync);

        // upload files if we need to
        for (LogData logData : data) {
            if (logData.hasFile) {
                JSONObject reading = findReading(backendReadings, logData);
                if (reading != null) {
                    try {
                        Log.i(TAG, "syncing file: " + reading.toString());
                        syncRequiredFile(logData, reading.getInt("id"), latch);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error syncing file: " + e.getMessage(), e);
                        latch.countDown();
                    }
                }
            }
        }

        // Wait for all file syncs to complete
        latch.await();
        Log.i(TAG, "All files synced");
    }

    private void syncRequiredFile(LogData data, int readingId, CountDownLatch latch) {
        String filePath = data.filePath;

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "file not found: " + filePath);

                latch.countDown();
                return;
            }

            byte[] bytes = readFileBytes(file);
            Log.i(TAG, "read file bytes: " + bytes.length);

            ApiClient client = ApiClient.getInstance(getApplicationContext());


            dataStore.getTokenSync(token -> {
                HashMap<String, String> formData = new HashMap<>();
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);

                client.postFile(
                        "https://sisensing.medien.ifi.lmu.de/v1/reading/" + readingId + "/file",
                        file.getName(),
                        "audio/aac",
                        bytes,
                        formData,
                        headers,
                        response -> {
                            Log.d(TAG, "uploaded reading file: " + response.toString());
                            latch.countDown();
                        },
                        error -> {
                            Log.e(TAG, error.toString());
                            latch.countDown();
                        }
                );

                Log.i(TAG, "file sync successful");
                latch.countDown();

                return Unit.INSTANCE;
            });
        } catch (IOException e) {
            Log.e(TAG, "failed to read file: " + filePath);
            latch.countDown();
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        DataInputStream dis = new DataInputStream(bis);
        dis.readFully(bytes);

        return bytes;
    }

    private JSONObject findReading(JSONArray array, LogData logData) {
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.getJSONObject(i);
                if (o.getString("data").equals(logData.data)) {
                    return o;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}