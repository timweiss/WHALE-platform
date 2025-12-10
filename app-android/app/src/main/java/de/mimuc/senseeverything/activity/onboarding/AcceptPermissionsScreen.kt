package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.permissions.PermissionManager
import de.mimuc.senseeverything.permissions.model.PermissionCategory
import de.mimuc.senseeverything.permissions.model.PermissionDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

private data class PermissionTab(
    val title: String,
    val category: PermissionCategory?
)

private val permissionTabs = listOf(
    PermissionTab("Info", null),
    PermissionTab("1", null),
    PermissionTab("2", PermissionCategory.BackgroundOperation),
    PermissionTab("3", PermissionCategory.Questionnaires),
    PermissionTab("4", PermissionCategory.SensorA),
    PermissionTab("5", PermissionCategory.SensorB)
)

private val categoryTitles = mapOf(
    PermissionCategory.BackgroundOperation to R.string.permission_category_background,
    PermissionCategory.Questionnaires to R.string.permission_category_questionnaire,
    PermissionCategory.SensorA to R.string.permission_category_sensor,
    PermissionCategory.SensorB to R.string.permission_category_sensor
)

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

    val pagerState = rememberPagerState(pageCount = { permissionTabs.size })
    val coroutineScope = rememberCoroutineScope()

    val categoryPermissions = remember {
        PermissionCategory.entries.associateWith { category ->
            PermissionManager.allPermissions.filter { it.category == category }
        }
    }

    val allGrantedInCategory = remember(permissions.value) {
        categoryPermissions.mapValues { (_, perms) ->
            perms.all { permissions.value[it.permission] == true }
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
            text = stringResource(R.string.onboarding_permissions_necessary)
        )

        TabRow(selectedTabIndex = pagerState.currentPage) {
            permissionTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        val isComplete = tab.category?.let { allGrantedInCategory[it] == true } ?: false
                        Text(if (isComplete) "${tab.title} ✓" else tab.title)
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val tab = permissionTabs[page]
            if (tab.category == null && page == 0) {
                PermissionIntroduction()
            } else if (tab.category == null && page == 1) {
                SpecialPermissionSection()
            } else {
                val title = if (categoryTitles[tab.category] != null) stringResource(categoryTitles[tab.category]!!) else ""
                PermissionGroup(
                    title = title,
                    permissions = permissions,
                    definitions = categoryPermissions[tab.category] ?: emptyList(),
                    requestPermission = { viewModel.requestPermission(it, context) }
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.padding(6.dp))
            if (!allAccepted) {
                Text(
                    stringResource(R.string.onboarding_permissions_accept_all_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.padding(6.dp))
            }
            Button(
                onClick = {
                    if (allAccepted) {
                        nextStep()
                    } else if (pagerState.canScrollForward) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                enabled = allAccepted || (pagerState.canScrollForward),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (allAccepted || !pagerState.canScrollForward) stringResource(R.string.onboarding_continue) else stringResource(
                        R.string.questionnaire_next
                    )
                )
            }
        }

        LaunchedEffect(lifecycleState) {
            if (lifecycleState == Lifecycle.State.RESUMED) {
                viewModel.checkPermissions()
            }
        }
    }
}


@Composable
private fun PermissionIntroduction() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Text(AnnotatedString.fromHtml(stringResource(R.string.onboarding_permissions_hint)))
    }
}

@Composable
private fun SpecialPermissionSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Text(AnnotatedString.fromHtml(stringResource(R.string.onboarding_permissions_special_hint)))
    }
}

@Composable
private fun PermissionGroup(
    title: String,
    permissions: State<Map<String, Boolean>>,
    definitions: List<PermissionDefinition>,
    requestPermission: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        definitions.forEach { permDef ->
            PermissionItem(
                label = stringResource(permDef.nameResId),
                description = stringResource(permDef.descriptionResId),
                isGranted = permissions.value[permDef.permission] ?: false,
                onRequestPermission = {
                    requestPermission(permDef.permission)
                }
            )
        }
    }
}