package de.mimuc.senseeverything.activity;

import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

public class CONST
{
	private static final String TAG = "CONST";
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	public static final String SP_LOG_EVERYTHING = "sp_log_everything";
	public static final String SP_Accessibility_LOG_EVERYTHING = "sp_log_everything";
	public static final String KEY_LOG_EVERYTHING_RUNNING = "key_log_everything_running";
	public static final String KEY_Accessibility_LOG_EVERYTHING_RUNNING = "key_Accessibility_LOG_everything_running";
	
	
	
	private static final String BASE_DIR = "LogEverything";
	public static String RELATIVE_PATH;

	public static boolean sdk29AndUp() {
	 	return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
	}

	public static final NumberFormat numberFormat = NumberFormat.getInstance();

	static {
		numberFormat.setMaximumFractionDigits(Integer.MAX_VALUE);
		numberFormat.setGroupingUsed(false);
	}
}
	

