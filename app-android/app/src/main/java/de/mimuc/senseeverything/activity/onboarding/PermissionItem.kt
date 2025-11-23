package de.mimuc.senseeverything.activity.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.Center) {
            Icon(
                painter = painterResource(id = if (isGranted) R.drawable.baseline_check_24 else R.drawable.outline_close_24),
                contentDescription = if (isGranted) "Granted" else "Not granted",
                modifier = Modifier
                    .size(26.dp)
                    .padding(end = 6.dp)
            )

            Text(label, fontWeight = FontWeight.Bold)
        }


        Row {
            Spacer(modifier = Modifier.width(26.dp))

            Column {
                Text(description)

                if (!isGranted) {
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.onboarding_grant_permission))
                    }
                }
            }
        }
    }
}
