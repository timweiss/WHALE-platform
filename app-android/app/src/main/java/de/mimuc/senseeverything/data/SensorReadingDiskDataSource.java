package de.mimuc.senseeverything.data;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import de.mimuc.senseeverything.activity.CONST;

public class SensorReadingDiskDataSource {
    final String TAG = "SensorReadingDiskDS";

    final Context applicationContext;
    final String sensorName;

    private OutputStream outputStream;
    private long streamCount;

    public SensorReadingDiskDataSource(Context context, String sensorName) {
        this.applicationContext = context;
        this.sensorName = sensorName;
        this.outputStream = getOutputStream();
    }

    public OutputStream getOutputStream() {
        OutputStream stream = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            ContentValues values = new ContentValues();

            values.put(MediaStore.MediaColumns.DISPLAY_NAME, getFileName());   // file name
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, CONST.RELATIVE_PATH);

            Uri extVolumeUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
                    MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
                    MediaStore.MediaColumns.DATE_MODIFIED   //used to set signature for Glide
            };

            String selection = MediaStore.MediaColumns.RELATIVE_PATH + "='" + CONST.RELATIVE_PATH + "' AND "
                    + MediaStore.MediaColumns.DISPLAY_NAME + "='" + getFileName() + "'";

            Uri fileUri = null;
            Cursor cursor = applicationContext.getContentResolver().query(extVolumeUri, projection, selection, null, null);
            if(cursor.getCount()>0){
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                    fileUri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,  id);
                }
            }
            cursor.close();
            if (fileUri == null)
                fileUri = applicationContext.getContentResolver().insert(extVolumeUri, values);


            try {
                stream = applicationContext.getContentResolver().openOutputStream(fileUri, "wa");
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Error #001: " + e.toString());
            }
        }
        else {
            String path = CONST.RELATIVE_PATH;
            File file = new File(path, getFileName());
            try {
                stream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (stream == null) {
            Log.e(TAG, "Could not create OutputStream for sensor " + sensorName);
        }

        return stream;
    }

    public void write(Long timestamp, String data) {
        if (outputStream == null) {
            Log.e(TAG, "OutputStream already closed for Sensor " + sensorName);
            return;
        }

        try {
            streamCount++;
            outputStream.write((timestamp + "," + data+"\n").getBytes());
            int flushLevel = 100;
            if(streamCount % flushLevel == 0) {
                outputStream.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void close() {
        if (outputStream == null) {
            Log.e(TAG, "OutputStream already closed for Sensor " + sensorName);
            return;
        }

        try {
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private String getFileName() {
        return sensorName.toLowerCase().replace(' ', '_');
    }
}
