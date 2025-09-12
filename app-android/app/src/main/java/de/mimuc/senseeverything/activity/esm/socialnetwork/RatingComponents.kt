package de.mimuc.senseeverything.activity.esm.socialnetwork

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.esm.QuestionnaireHost
import de.mimuc.senseeverything.activity.ui.theme.Typography
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.SocialNetworkRatingElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getQuestionnaireById
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.SocialNetworkContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = SocialNetworkRatingViewModel.Factory::class)
class SocialNetworkRatingViewModel @AssistedInject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
    @Assisted val element: SocialNetworkRatingElement,
    @Assisted val value: Map<Int, Map<Int, ElementValue>>,
    @Assisted val onValueChange: (Map<Int, Map<Int, ElementValue>>) -> Unit
) : AndroidViewModel(application) {
    @AssistedFactory
    interface Factory {
        fun create(
            element: SocialNetworkRatingElement,
            value: Map<Int, Map<Int, ElementValue>>,
            onValueChange: (Map<Int, Map<Int, ElementValue>>) -> Unit
        ): SocialNetworkRatingViewModel
    }

    private val _contacts = MutableStateFlow<List<SocialNetworkContact>>(emptyList())
    val contacts: StateFlow<List<SocialNetworkContact>> = _contacts

    private val _ratingQuestionnaire = MutableStateFlow<FullQuestionnaire?>(null)
    val ratingQuestionnaire: StateFlow<FullQuestionnaire?> = _ratingQuestionnaire

    private val _ratingValues = MutableStateFlow<Map<Int, Map<Int, ElementValue>>>(emptyMap())
    val ratingValues: StateFlow<Map<Int, Map<Int, ElementValue>>> = _ratingValues

    init {
        loadContacts()
        loadRatingQuestionnaire()
        _ratingValues.value = value
    }

    fun loadContacts() {
        viewModelScope.launch {
            val persons = withContext(Dispatchers.IO) {
                database.socialNetworkContactDao().getAllSortedByName()
            }
            _contacts.value = persons
        }
    }

    fun loadRatingQuestionnaire() {
        viewModelScope.launch {
            val questionnaire = dataStoreManager.getQuestionnaireById(element.configuration.ratingQuestionnaireId)
            if (questionnaire != null) {
                _ratingQuestionnaire.value = questionnaire
            } else {
                // Handle the case where the questionnaire is not found
            }
        }
    }

    fun setContactValue(contactId: Int, value: Map<Int, ElementValue>) {
        _ratingValues.value = _ratingValues.value.toMutableMap().apply {
            put(contactId, value)
        }
        onValueChange(_ratingValues.value)
    }
}

@Composable
fun SocialNetworkRatingElementComponent(
    element: SocialNetworkRatingElement,
    value: Map<Int, Map<Int, ElementValue>>,
    onValueChange: (Map<Int, Map<Int, ElementValue>>) -> Unit,
) {
    val viewModel = hiltViewModel<SocialNetworkRatingViewModel, SocialNetworkRatingViewModel.Factory> { factory ->
        factory.create(element, value, onValueChange)
    }

    val contacts by viewModel.contacts.collectAsState()
    val values by viewModel.ratingValues.collectAsState()
    val questionnaire by viewModel.ratingQuestionnaire.collectAsState()

    val lifecycleState = androidx.lifecycle.Lifecycle.State.RESUMED

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            androidx.lifecycle.Lifecycle.State.RESUMED -> {
                viewModel.loadContacts()
            }

            else -> {}
        }
    }

    if (questionnaire == null || contacts.isEmpty()) {
        // Show loading or error state
        BasicText("Loading contacts...")
        return
    } else {
        Column {
            for ((contactId, contact) in contacts.withIndex()) {
                val contactValue = values[contactId] ?: emptyMap()
                val completed = contactValue.isNotEmpty()
                AccordionItem(
                    title = contact.name,
                    icon = { if (completed) Icon(Icons.Default.Check, contentDescription = null) },
                    enabled = !completed
                ) {
                    Column {
                        QuestionnaireHost(questionnaire = questionnaire!!,
                            emptyMap(), // todo: replacements in ratings component not supported for now
                            onSave = { newValues ->
                                viewModel.setContactValue(contactId, newValues)
                            },
                            embedded = true,
                            hostKey = "hosted_questionnaire_${element.questionnaireId}_rating_${contactId}"
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AccordionItem(
    title: String,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = Typography.bodyLarge, fontWeight = (if (expanded && enabled) FontWeight.Bold else FontWeight.Normal))
            icon()
        }
        AnimatedVisibility(
            visible = expanded && enabled,
            enter = expandVertically(
                spring(
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntSize.VisibilityThreshold
                )
            ),
            exit = shrinkVertically()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}