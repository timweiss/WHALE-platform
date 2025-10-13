package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.permissions.PermissionManager
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
        _permissions.value = PermissionManager.checkAll(getApplication())
    }

    fun requestPermission(permission: String, context: Context) {
        PermissionManager.requestPermission(permission, context)
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
            PermissionManager.allPermissions.forEach { permDef ->
                PermissionItem(
                    label = stringResource(permDef.nameResId),
                    description = stringResource(permDef.descriptionResId),
                    isGranted = permissions.value[permDef.permission] ?: false,
                    onRequestPermission = {
                        viewModel.requestPermission(permDef.permission, context)
                    }
                )
            }

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