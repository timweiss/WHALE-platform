@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.onboarding.Heading
import de.mimuc.senseeverything.activity.onboarding.PermissionItem
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.helpers.LogServiceHelper
import de.mimuc.senseeverything.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ReAcceptPermissionsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val _permissions = MutableStateFlow(emptyMap<String, Boolean>())
    val permissions: StateFlow<Map<String, Boolean>> get() = _permissions

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        _permissions.value = PermissionManager.checkAll(getApplication())
    }

    fun requestPermission(permission: String, context: Context) {
        PermissionManager.requestPermission(permission, context)
    }
}

@AndroidEntryPoint
class RecoverPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppandroidTheme {
                Scaffold(topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = {
                            Text(stringResource(R.string.permission_revoked_title))
                        }
                    )
                }, modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReAcceptPermissionsScreen(
                        onAllPermissionsGranted = { finishAndRemoveTask() },
                        innerPadding = innerPadding
                    )
                }
            }
        }
    }
}

@Composable
fun ReAcceptPermissionsScreen(
    onAllPermissionsGranted: () -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: ReAcceptPermissionsViewModel = viewModel()
) {
    val permissions = viewModel.permissions.collectAsState()
    val context = LocalContext.current

    val revokedPermissions = permissions.value.filterValues { !it }
    val allAccepted = revokedPermissions.isEmpty()

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    // Auto-close if all permissions are granted
    LaunchedEffect(allAccepted) {
        if (allAccepted) {
            Toast.makeText(
                context,
                R.string.permission_revoked_all_granted_toast,
                Toast.LENGTH_SHORT
            ).show()

            // Restart LogService now that permissions are fixed
            LogServiceHelper.startLogService(context)

            onAllPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(
            id = R.drawable.rounded_key_vertical_24,
            description = "Lock symbol",
            text = stringResource(R.string.permission_revoked_title)
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.permission_revoked_description))

        Spacer(modifier = Modifier.padding(12.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            PermissionManager.allPermissions.filter { permDef ->
                permissions.value[permDef.permission] == false
            }.forEach { permDef ->
                PermissionItem(
                    label = stringResource(permDef.nameResId),
                    description = stringResource(permDef.descriptionResId),
                    isGranted = false,
                    onRequestPermission = {
                        viewModel.requestPermission(permDef.permission, context)
                    }
                )
            }

            Spacer(modifier = Modifier.padding(16.dp))
            Button(
                onClick = onAllPermissionsGranted,
                enabled = allAccepted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_revoked_done))
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
