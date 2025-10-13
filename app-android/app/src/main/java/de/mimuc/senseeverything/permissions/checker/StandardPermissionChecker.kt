package de.mimuc.senseeverything.permissions.checker

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Checks standard Android runtime permissions
 */
class StandardPermissionChecker(private val permission: String) : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
