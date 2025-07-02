package de.mimuc.senseeverything.activity.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.R

/**
 * A component for displaying and requesting a permission
 *
 * @param label The label to display for this permission
 * @param isGranted Whether the permission has been granted
 * @param onRequestPermission Callback to request the permission
 */
@Composable
fun PermissionItem(
    label: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: $isGranted")
        if (!isGranted) {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.onboarding_grant_permission))
            }
        }
    }
}

/**
 * Extension function to create a PermissionItem for a standard permission
 */
@Composable
fun StandardPermissionItem(
    label: String,
    permission: String,
    permissions: Map<String, Boolean>,
    viewModel: AcceptPermissionsViewModel,
    context: Context
) {
    PermissionItem(
        label = label,
        isGranted = permissions[permission] ?: false,
        onRequestPermission = {
            viewModel.requestPermission(permission, context)
        }
    )
}

/**
 * Extension function to create a PermissionItem for a special permission
 */
@Composable
fun SpecialPermissionItem(
    label: String,
    permission: String,
    permissions: Map<String, Boolean>,
    onRequestPermission: () -> Unit
) {
    PermissionItem(
        label = label,
        isGranted = permissions[permission] ?: false,
        onRequestPermission = onRequestPermission
    )
}
