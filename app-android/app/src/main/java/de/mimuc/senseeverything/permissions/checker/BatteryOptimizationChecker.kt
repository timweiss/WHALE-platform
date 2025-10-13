package de.mimuc.senseeverything.permissions.checker

import android.content.Context
import android.os.PowerManager

/**
 * Checks if the app is exempt from battery optimizations
 */
class BatteryOptimizationChecker : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }
}
