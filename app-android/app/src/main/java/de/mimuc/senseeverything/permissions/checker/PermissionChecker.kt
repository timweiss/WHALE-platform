package de.mimuc.senseeverything.permissions.checker

import android.content.Context

/**
 * Interface for checking if a permission is granted
 */
interface PermissionChecker {
    /**
     * Checks if the permission is currently granted
     *
     * @param context The application context
     * @return true if the permission is granted, false otherwise
     */
    fun isGranted(context: Context): Boolean
}
