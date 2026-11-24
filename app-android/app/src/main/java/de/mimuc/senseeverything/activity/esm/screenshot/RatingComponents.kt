package de.mimuc.senseeverything.activity.esm.screenshot

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.esm.QuestionnaireHost
import de.mimuc.senseeverything.activity.esm.socialnetwork.AccordionItem
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.ScreenshotRatingElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getQuestionnaireById
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.withIndex

@HiltViewModel(assistedFactory = ScreenshotRatingViewModel.Factory::class)
class ScreenshotRatingViewModel @AssistedInject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
    @Assisted val element: ScreenshotRatingElement,
    @Assisted val value: Map<String, Map<Int, ElementValue>>,
    @Assisted val onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit
) : AndroidViewModel(application) {
    @AssistedFactory
    interface Factory {
        fun create(
            element: ScreenshotRatingElement,
            value: Map<String, Map<Int, ElementValue>>,
            onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit
        ): ScreenshotRatingViewModel
    }

    private val _screenshots = MutableStateFlow<List<ScreenshotReference>>(emptyList())
    val screenshots: StateFlow<List<ScreenshotReference>> = _screenshots

    private val _ratingQuestionnaires = MutableStateFlow<Map<String, FullQuestionnaire?>>(emptyMap())
    val ratingQuestionnaires: StateFlow<Map<String, FullQuestionnaire?>> = _ratingQuestionnaires

    private val _ratingValues = MutableStateFlow<Map<String, Map<Int, ElementValue>>>(emptyMap())
    val ratingValues: StateFlow<Map<String, Map<Int, ElementValue>>> = _ratingValues

    init {
        _ratingValues.value = value
        loadScreenshots()
        loadRatingQuestionnaire()
    }

    fun loadScreenshots() {
        viewModelScope.launch {
            val screenshots = withContext(Dispatchers.IO) {
                // todo: load screenshots from store
            }
            // if not saved, prefill empty so we can validate
            if (_ratingValues.value.isEmpty()) {
                _ratingValues.value =
                    screenshots.associate { it.snapshotId to emptyMap() }
                onValueChange(_ratingValues.value)
            }
            _screenshots.value = screenshots
        }
    }

    fun loadRatingQuestionnaire() {
        viewModelScope.launch {
            _ratingQuestionnaires.value = element.configuration.packageRatingQuestionnaires
                .map { (packageName, questionnaireId) -> Pair(packageName, dataStoreManager.getQuestionnaireById(questionnaireId)) }
                .toMap()
        }
    }

    fun setValue(snapshotId: String, value: Map<Int, ElementValue>) {
        _ratingValues.value = _ratingValues.value.toMutableMap().apply {
            put(snapshotId, value)
        }
        onValueChange(_ratingValues.value)
    }
}

@Composable
fun ScreenshotRatingElementComponent(
    element: ScreenshotRatingElement,
    value: Map<String, Map<Int, ElementValue>>,
    onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit,
) {
    val viewModel = hiltViewModel<ScreenshotRatingViewModel, ScreenshotRatingViewModel.Factory> { factory ->
        factory.create(element, value, onValueChange)
    }

    val screenshots by viewModel.screenshots.collectAsState()
    val values by viewModel.ratingValues.collectAsState()
    val questionnaires by viewModel.ratingQuestionnaires.collectAsState()

    val lifecycleState = Lifecycle.State.RESUMED

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            Lifecycle.State.RESUMED -> {
                viewModel.loadScreenshots()
            }

            else -> {}
        }
    }

    if (questionnaires == null || screenshots.isEmpty()) {
        // Show loading or error state
        // fixme: show if no screnshots have been added
        BasicText("Loading contacts...")
        return
    } else {
        Column {
            for ((index, screenshot) in screenshots.withIndex()) {
                val questionnaire = questionnaires.get(screenshot.packageName)

                if (questionnaire != null) {
                    val contactValue = values[screenshot.snapshotId] ?: emptyMap()
                    val completed = contactValue.isNotEmpty()
                    AccordionItem(
                        title = screenshot.label,
                        icon = { if (completed) Icon(Icons.Default.Check, contentDescription = null) },
                        enabled = !completed
                    ) {
                        Column {
                            QuestionnaireHost(questionnaire = questionnaire,
                                emptyMap(), // todo: replacements in ratings component not supported for now
                                onSave = { newValues ->
                                    viewModel.setValue(screenshot.snapshotId, newValues)
                                },
                                embedded = true,
                                hostKey = "hosted_questionnaire_${element.questionnaireId}_rating_${index}"
                            )
                        }
                    }
                } else {
                    BasicText("Could not load questionnaire for screenshot")
                }

                HorizontalDivider()
            }
        }
    }
}