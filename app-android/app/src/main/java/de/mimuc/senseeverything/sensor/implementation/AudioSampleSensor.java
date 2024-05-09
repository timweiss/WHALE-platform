package de.mimuc.senseeverything.sensor.implementation;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.util.UUID;

import de.mimuc.senseeverything.sensor.AbstractSensor;

public class AudioSampleSensor extends AbstractSensor {
	private static final long serialVersionUID = 1L;

	private static final int RECORDING_TIME = 1000 * 60; // one minute
	// private static final int COOLDOWN_TIME = 1000 * 60 * 10; // 10 minutes
	private static final int COOLDOWN_TIME = 1000 * 60 * 3; // 10 minutes

	private final Handler handler = new Handler();
	private MediaRecorder mediaRecorder;
	private boolean shouldRecord = true;
	private final Context context;
	private final String guid = UUID.randomUUID().toString();

	public AudioSampleSensor(Context applicationContext) {
		super(applicationContext);
		context = applicationContext;
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Audio Sample";
		FILE_NAME = "audiosamples.csv";
		m_FileHeader = "Timestamp,Filename";
	}
		
	public View getSettingsView(Context context) {	
		return null;
	}
	
	public boolean isAvailable(Context context) {
		return true;
	}
	
	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		// already running, keep sampling at our own pace
		if (m_IsRunning)
			return;

		Log.d(TAG, "audioSampleSensor: start called, guid" + guid);

		try {
			m_IsRunning = true;
			Runnable recordJob = new Runnable() {
				@Override
				public void run() {
					if (initMediaRecorder()) {
						Log.d(TAG, "running in loop again with shouldRecord =" + shouldRecord + " guid " + guid);
						// we start
						if (m_IsRunning && shouldRecord) {
							Log.d(TAG, "starting recording, will stop in " + RECORDING_TIME + "ms");
							startRecording();
							handler.postDelayed(this, RECORDING_TIME);
						} else if (m_IsRunning && mediaRecorder != null) {
							Log.d(TAG, "resetting recording, will start again in " + COOLDOWN_TIME + "ms");
							stopRecording();
							handler.postDelayed(this, COOLDOWN_TIME);
						}
					}
				}
			};
			recordJob.run();
			m_OutputStream.flush();
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	private String getFilenameForSampleStorage() {
		String path = context.getFilesDir().getAbsolutePath();
		String recordingName = "se-" + System.currentTimeMillis() / 1000 + ".webm";

		return path + "/" + recordingName;
	}

	private void stopRecording() {
		mediaRecorder.stop();
		mediaRecorder.reset();
		mediaRecorder.release();
		mediaRecorder = null;
		shouldRecord = true;
	}

	private void startRecording() {
		try {
			String filename = getFilenameForSampleStorage();
			mediaRecorder.setOutputFile(filename);
			Log.i(TAG, "saving recording at location " + filename);
			mediaRecorder.prepare();
			mediaRecorder.start();
			shouldRecord = false;
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
	}
	
	private boolean initMediaRecorder() {
		try {
			if (mediaRecorder != null) {
				Log.d(TAG, "initMediaRecorder: already initialized");
				return true;
			}

			try {
				Log.d(TAG, "initMediaRecorder");
				mediaRecorder = new MediaRecorder();
				mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
				mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
				return true;
			} catch (IllegalStateException e) {
				Log.e(TAG, e.toString());
			}
		} catch(Exception e) {
			Log.d(TAG, e.toString());
		}
		return false;
	}

	@Override
	public void stop() {
		if(m_IsRunning) {
			Log.d(TAG, "stopped recording by daemon");
			m_IsRunning = false;
			try {
				stopRecording();
				m_OutputStream.flush();
				m_OutputStream.close();
				m_OutputStream = null;
			} catch (Exception e) {
				Log.e(TAG, e.toString());
			}

		}	
	}	
}