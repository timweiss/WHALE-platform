package de.mimuc.senseeverything.permissions.requester

import android.content.Context

/**
 * Interface for requesting a permission
 */
interface PermissionRequester {
    /**
     * Requests the permission from the user
     *
     * @param context The application context
     */
    fun request(context: Context)
}
