package de.mimuc.senseeverything.permissions.model

import de.mimuc.senseeverything.permissions.checker.PermissionChecker
import de.mimuc.senseeverything.permissions.requester.PermissionRequester

/**
 * Defines a permission with all its metadata and behavior
 *
 * @param permission The Android permission string (e.g., Manifest.permission.CAMERA)
 * @param nameResId String resource ID for the permission's display name
 * @param descriptionResId String resource ID for the permission's description
 * @param type The type of permission (STANDARD or SPECIAL)
 * @param checker Strategy for checking if the permission is granted
 * @param requester Strategy for requesting the permission
 * @param isCritical Whether this permission is critical for core app functionality
 */
data class PermissionDefinition(
    val permission: String,
    val nameResId: Int,
    val descriptionResId: Int,
    val type: PermissionType,
    val checker: PermissionChecker,
    val requester: PermissionRequester,
    val isCritical: Boolean = false
)
