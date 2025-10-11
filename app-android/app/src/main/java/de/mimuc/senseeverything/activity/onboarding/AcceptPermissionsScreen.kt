package de.mimuc.senseeverything.activity.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.getActivity
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.SEApplicationController
import de.mimuc.senseeverything.service.accessibility.AccessibilityLogService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AcceptPermissionsViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _permissions = MutableStateFlow(emptyMap<String, Boolean>())
    val permissions: StateFlow<Map<String, Boolean>> get() = _permissions

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        _permissions.value = mutableMapOf()

        checkAndSetPermission(Manifest.permission.WAKE_LOCK)
        checkAndSetPermission(Manifest.permission.RECORD_AUDIO)
        checkAndSetPermission(Manifest.permission.ACCESS_WIFI_STATE)
        checkAndSetPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        checkAndSetPermission(Manifest.permission.READ_PHONE_STATE)
        checkAndSetPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        checkAndSetPermission(Manifest.permission.FOREGROUND_SERVICE)
        checkAndSetPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        checkAndSetPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
        checkAndSetPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        checkAndSetPermission(Manifest.permission.BLUETOOTH_SCAN)
        checkAndSetPermission(Manifest.permission.POST_NOTIFICATIONS)
        checkAndSetPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        checkAndSetPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
        checkAndSetPermission(Manifest.permission.ACTIVITY_RECOGNITION)

        setPermission(Manifest.permission.PACKAGE_USAGE_STATS, hasAccessToUsageStats())

        if (Settings.canDrawOverlays(getApplication())) {
            setPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, true)
        }

        if (NotificationManagerCompat.getEnabledListenerPackages(getApplication())
                .contains(getApplication<SEApplicationController>().packageName)
        ) {
            setPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, true)
        }

        setPermission(
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            isAccessibilityServiceEnabled(getApplication())
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                getApplication<SEApplicationController>().getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                setPermission(
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    alarmManager.canScheduleExactAlarms()
                )
            }
        }

        val powerManager =
            getApplication<SEApplicationController>().getSystemService(Context.POWER_SERVICE) as PowerManager?
        val packageName = getApplication<SEApplicationController>().packageName
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)) {
            setPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)
        } else {
            setPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, false)
        }
    }

    private fun checkAndSetPermission(permission: String) {
        _permissions.value = _permissions.value.toMutableMap().apply {
            val checked = ContextCompat.checkSelfPermission(getApplication(), permission)
            put(permission, checked == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun setPermission(permission: String, granted: Boolean) {
        _permissions.value = _permissions.value.toMutableMap().apply {
            put(permission, granted)
        }
    }

    fun requestPermission(permission: String, context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), 1)
            checkAndSetPermission(permission)
        } else {
            WHALELog.e("AcceptPermissionsViewModel", "Could not get activity")
        }
    }

    fun requestSystemWindowPermission(context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            if (!Settings.canDrawOverlays(activity)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,

                    Uri.parse("package:" + context.packageName)
                )
                ActivityCompat.startActivityForResult(activity, intent, 1001, null)
            } else {
                _permissions.value = _permissions.value.toMutableMap().apply {
                    put(Manifest.permission.SYSTEM_ALERT_WINDOW, true)
                }
                WHALELog.i(
                    "AcceptPermissionsViewModel",
                    "SYSTEM_ALERT_WINDOW permission already granted"
                )
            }
        } else {
            WHALELog.e("AcceptPermissionsViewModel", "Could not get activity")
        }
    }

    fun requestNotificationListenerPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestAccessibilityServicePermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestUsageStatsPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestExactAlarmPermission(context: Context) {
        // permission is only not granted above API 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    fun requestPowerExemptionPermission(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        val packageName = context.packageName
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0

        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            WHALELog.e("Onboarding", e.toString())
        }

        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            val settingValue =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(
                            AccessibilityLogService.SERVICE,
                            ignoreCase = true
                        )
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasAccessToUsageStats(): Boolean {
        val application = getApplication<SEApplicationController>()
        val appOps = application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName);
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return (application.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            return (mode == AppOpsManager.MODE_ALLOWED);
        }
    }
}

@Composable
fun AcceptPermissionsScreen(
    nextStep: () -> Unit,
    innerPadding: PaddingValues,
    viewModel: AcceptPermissionsViewModel = viewModel()
) {
    val permissions = viewModel.permissions.collectAsState()
    val context = LocalContext.current
    val allAccepted = permissions.value.values.all { it }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(
            id = R.drawable.rounded_key_vertical_24,
            description = "Lock symbol",
            text = stringResource(R.string.onboarding_permissions_necessary)
        )
        Spacer(modifier = Modifier.Companion.padding(12.dp))
        Text(stringResource(R.string.onboarding_permissions_hint))

        Spacer(modifier = Modifier.Companion.padding(12.dp))

        Column(modifier = Modifier.Companion.verticalScroll(rememberScrollState())) {
            // Standard permissions
            StandardPermissionItem(
                label = "Wake Lock",
                permission = Manifest.permission.WAKE_LOCK,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Record Audio",
                permission = Manifest.permission.RECORD_AUDIO,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Wifi",
                permission = Manifest.permission.ACCESS_WIFI_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Bluetooth Scan",
                permission = Manifest.permission.BLUETOOTH_SCAN,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Network State",
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Receive Boot Completed",
                permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Read Phone State",
                permission = Manifest.permission.READ_PHONE_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Foreground Service",
                permission = Manifest.permission.FOREGROUND_SERVICE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Notifications",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Fine Location",
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Background Location",
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Activity Recognition",
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            // Special permissions with custom request methods
            SpecialPermissionItem(
                label = "Schedule Exact Alarm",
                permission = Manifest.permission.SCHEDULE_EXACT_ALARM,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestExactAlarmPermission(context) }
            )

            SpecialPermissionItem(
                label = "System Alert Window",
                permission = Manifest.permission.SYSTEM_ALERT_WINDOW,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestSystemWindowPermission(context) }
            )

            SpecialPermissionItem(
                label = "Notification Access",
                permission = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestNotificationListenerPermission(context) }
            )

            SpecialPermissionItem(
                label = "Accessibility Events",
                permission = Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestAccessibilityServicePermission(context) }
            )

            SpecialPermissionItem(
                label = "Usage Statistics",
                permission = Manifest.permission.PACKAGE_USAGE_STATS,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestUsageStatsPermission(context) }
            )

            SpecialPermissionItem(
                label = "Power Management",
                permission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestPowerExemptionPermission(context) }
            )

            Spacer(modifier = Modifier.Companion.padding(16.dp))
            Text(stringResource(R.string.onboarding_permissions_accept_all_hint))

            Spacer(modifier = Modifier.Companion.padding(12.dp))
            Button(onClick = nextStep, enabled = allAccepted, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> viewModel.checkPermissions()
                else -> {}
            }
        }
    }
}