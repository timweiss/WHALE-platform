package de.mimuc.senseeverything.permissions.model

/**
 * Enum representing the type of permission
 */
enum class PermissionType {
    /**
     * Standard Android runtime permissions that can be requested via ActivityCompat.requestPermissions()
     */
    STANDARD,

    /**
     * Special permissions that require navigating to system settings screens
     */
    SPECIAL
}
