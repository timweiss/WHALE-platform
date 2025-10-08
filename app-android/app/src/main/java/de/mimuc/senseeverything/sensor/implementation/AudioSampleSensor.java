package de.mimuc.senseeverything.sensor.implementation;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.util.UUID;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.logging.WHALELog;
import de.mimuc.senseeverything.sensor.AbstractSensor;

public class AudioSampleSensor extends AbstractSensor {
	private static final long serialVersionUID = 1L;
	private MediaRecorder mediaRecorder;
	private final Context context;
	private final String guid = UUID.randomUUID().toString();
	private String currentRecordingFilename;

	public AudioSampleSensor(Context applicationContext, AppDatabase database) {
		super(applicationContext, database);
		context = applicationContext;
		m_IsRunning = false;
		TAG = getClass().getName();
		SENSOR_NAME = "Audio Sample";
		FILE_NAME = "audiosamples.csv";
		m_FileHeader = "Timestamp,Filename";
	}

	public boolean isAvailable(Context context) {
		return true;
	}

	@Override
	public boolean availableForPeriodicSampling() {
		return false;
	}

	@Override
	public void start(Context context) {
		super.start(context);
		if (!m_isSensorAvailable)
			return;

		// already running, keep sampling at our own pace
		if (m_IsRunning)
			return;

		WHALELog.INSTANCE.d(TAG, "audioSampleSensor: start called, guid" + guid);

		if (initMediaRecorder()) {
			startRecording();
			m_IsRunning = true;
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

		// write data
		Long t = System.currentTimeMillis();
		onLogDataItemWithFile(t, currentRecordingFilename, currentRecordingFilename);
	}

	private void startRecording() {
		try {
			String filename = getFilenameForSampleStorage();
			mediaRecorder.setOutputFile(filename);
			WHALELog.INSTANCE.i(TAG, "saving recording at location " + filename);
			currentRecordingFilename = filename;
			mediaRecorder.prepare();
			mediaRecorder.start();
		} catch (IOException e) {
			WHALELog.INSTANCE.e(TAG, e.toString());
		}
	}
	
	private boolean initMediaRecorder() {
		try {
			if (mediaRecorder != null) {
				WHALELog.INSTANCE.d(TAG, "initMediaRecorder: already initialized");
				return true;
			}

			try {
				WHALELog.INSTANCE.d(TAG, "initMediaRecorder");
				mediaRecorder = new MediaRecorder();
				mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
				mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
				return true;
			} catch (IllegalStateException e) {
				WHALELog.INSTANCE.e(TAG, e.toString());
			}
		} catch(Exception e) {
			WHALELog.INSTANCE.d(TAG, e.toString());
		}
		return false;
	}

	@Override
	public void stop() {
		if(m_IsRunning) {
			WHALELog.INSTANCE.d(TAG, "stopped recording by daemon");
			m_IsRunning = false;
			try {
				if (mediaRecorder != null) {
					stopRecording();
				}
				closeDataSource();
			} catch (Exception e) {
				WHALELog.INSTANCE.e(TAG, e.toString());
			}
		}	
	}	
}