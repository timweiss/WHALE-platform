package de.mimuc.senseeverything.activity.esm

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.model.SocialNetworkEntryElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.SocialNetworkContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SocialNetworkEntryViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _availablePersons = MutableStateFlow<List<SocialNetworkContact>>(emptyList())
    val availablePersons: StateFlow<List<SocialNetworkContact>> = _availablePersons

    private val _selectedPersons = MutableStateFlow<List<SocialNetworkContact>>(emptyList())
    val selectedPersons: StateFlow<List<SocialNetworkContact>> = _selectedPersons

    init {
        viewModelScope.launch {
            val persons = withContext(Dispatchers.IO) {
                database.socialNetworkContactDao().getAll()
            }
            _availablePersons.value = persons
        }
    }

    fun onPersonAdded(person: SocialNetworkContact) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                database.socialNetworkContactDao().insert(person)
            }
            person.uid = id
            _availablePersons.value = _availablePersons.value + person
            _selectedPersons.value = _selectedPersons.value + person
        }
    }

    fun onSelectionChanged(selected: List<SocialNetworkContact>) {
        _selectedPersons.value = selected
    }

    fun onUnselected(person: SocialNetworkContact) {
        _selectedPersons.value = _selectedPersons.value.filter { it.uid != person.uid }
    }
}

@Composable
fun SocialNetworkEntryElementComponent(
    viewModel: SocialNetworkEntryViewModel = viewModel(),
    element: SocialNetworkEntryElement
) {
    var availablePersons = viewModel.availablePersons.collectAsState()
    var selectedPersons = viewModel.selectedPersons.collectAsState()

    // Dialog Status
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showSelectionDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.social_network_choose_person) + "...")
            }

            Button(
                onClick = { showAddPersonDialog = true }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.social_network_add_person)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedPersons.value.isEmpty()) {
            Text(
                stringResource(R.string.social_network_no_person_selected),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = stringResource(R.string.social_network_selected_people),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column {
                for (person in selectedPersons.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(person.name)
                        IconButton(
                            onClick = {
                                viewModel.onUnselected(person)
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.gen_remove)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // select a person
    if (showSelectionDialog) {
        SelectionDialog(
            availablePersons = availablePersons.value,
            selectedPersons = selectedPersons.value,
            onSelectionChanged = { viewModel.onSelectionChanged(it) },
            onDismiss = { showSelectionDialog = false }
        )
    }

    // add a new person
    if (showAddPersonDialog) {
        AddPersonDialog(
            onPersonAdded = { person ->
                viewModel.onPersonAdded(person)
            },
            onDismiss = { showAddPersonDialog = false }
        )
    }
}

@Composable
fun SelectionDialog(
    availablePersons: List<SocialNetworkContact>,
    selectedPersons: List<SocialNetworkContact>,
    onSelectionChanged: (List<SocialNetworkContact>) -> Unit,
    onDismiss: () -> Unit
) {
    val tempSelectedIds = remember { selectedPersons.map { it.uid }.toMutableStateList() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.social_network_choose_person),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(availablePersons) { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = tempSelectedIds.contains(person.uid),
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        tempSelectedIds.add(person.uid)
                                    } else {
                                        tempSelectedIds.remove(person.uid)
                                    }
                                }
                            )
                            Text(person.name)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.gen_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSelectionChanged(availablePersons.filter { tempSelectedIds.contains(it.uid) })
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.gen_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun AddPersonDialog(
    onPersonAdded: (SocialNetworkContact) -> Unit,
    onDismiss: () -> Unit
) {
    var personName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.social_network_add_new_person),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text(stringResource(R.string.social_network_person_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.gen_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (personName.isNotBlank()) {
                                val newPerson = SocialNetworkContact(
                                    uid = 0,
                                    name = personName.trim(),
                                    addedAt = System.currentTimeMillis()
                                )
                                onPersonAdded(newPerson)
                                onDismiss()
                            }
                        },
                        enabled = personName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.gen_add))
                    }
                }
            }
        }
    }
}