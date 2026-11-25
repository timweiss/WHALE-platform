package de.mimuc.senseeverything.activity.esm.screenshot

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.esm.LocalPendingQuestionnaire
import de.mimuc.senseeverything.activity.esm.QuestionnaireHost
import de.mimuc.senseeverything.activity.esm.socialnetwork.AccordionItem
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.ScreenshotRatingElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getQuestionnaireById
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.accessibility.ScreenshotRatingContext
import de.mimuc.senseeverything.service.accessibility.ScreenshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = ScreenshotRatingViewModel.Factory::class)
class ScreenshotRatingViewModel @AssistedInject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    @Assisted val element: ScreenshotRatingElement,
    /** the pending questionnaire's context, names the period to rate */
    @Assisted val contextJson: String?,
    @Assisted val value: Map<String, Map<Int, ElementValue>>,
    @Assisted val onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit
) : AndroidViewModel(application) {
    @AssistedFactory
    interface Factory {
        fun create(
            element: ScreenshotRatingElement,
            contextJson: String?,
            value: Map<String, Map<Int, ElementValue>>,
            onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit
        ): ScreenshotRatingViewModel
    }

    /** null while loading */
    private val _screenshots = MutableStateFlow<List<ScreenshotReference>?>(null)
    val screenshots: StateFlow<List<ScreenshotReference>?> = _screenshots

    private val _ratingQuestionnaires = MutableStateFlow<Map<String, FullQuestionnaire>>(emptyMap())
    val ratingQuestionnaires: StateFlow<Map<String, FullQuestionnaire>> = _ratingQuestionnaires

    private val _ratingValues = MutableStateFlow(value)
    val ratingValues: StateFlow<Map<String, Map<Int, ElementValue>>> = _ratingValues

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val questionnaires = loadRatingQuestionnaires()
        val (rateable, unmapped) = loadScreenshots().partition { it.packageName in questionnaires }
        if (unmapped.isNotEmpty()) {
            WHALELog.w(TAG, "Not rating ${unmapped.size} screenshots, no questionnaire for ${unmapped.map { it.packageName }.distinct()}")
        }

        // if not saved, prefill empty so we can validate
        if (_ratingValues.value.isEmpty()) {
            _ratingValues.value = rateable.associate { it.snapshotId to emptyMap() }
            onValueChange(_ratingValues.value)
        }
        _ratingQuestionnaires.value = questionnaires
        _screenshots.value = rateable
    }

    private suspend fun loadScreenshots(): List<ScreenshotReference> {
        val ratingContext = ScreenshotRatingContext.fromJson(contextJson)
        if (ratingContext == null) {
            WHALELog.w(TAG, "The questionnaire doesn't refer to a screenshot period, nothing to rate")
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            ScreenshotReference.ofPeriod(ScreenshotStore.root(getApplication()), ratingContext.screenshotPeriodEnd)
        }
    }

    /** By package; questionnaires that don't exist are left out. */
    private suspend fun loadRatingQuestionnaires(): Map<String, FullQuestionnaire> =
        element.configuration.packageRatingQuestionnaires.mapNotNull { (packageName, questionnaireId) ->
            dataStoreManager.getQuestionnaireById(questionnaireId)?.let { packageName to it }
        }.toMap()

    fun setValue(snapshotId: String, value: Map<Int, ElementValue>) {
        _ratingValues.value = _ratingValues.value + (snapshotId to value)
        onValueChange(_ratingValues.value)
    }

    private companion object {
        const val TAG = "ScreenshotRating"
    }
}

@Composable
fun ScreenshotRatingElementComponent(
    element: ScreenshotRatingElement,
    value: Map<String, Map<Int, ElementValue>>,
    onValueChange: (Map<String, Map<Int, ElementValue>>) -> Unit,
) {
    val contextJson = LocalPendingQuestionnaire.current?.contextJson
    val viewModel = hiltViewModel<ScreenshotRatingViewModel, ScreenshotRatingViewModel.Factory>(
        key = "screenshot_rating_${element.id}"
    ) { factory ->
        factory.create(element, contextJson, value, onValueChange)
    }

    val screenshots by viewModel.screenshots.collectAsState()
    val values by viewModel.ratingValues.collectAsState()
    val questionnaires by viewModel.ratingQuestionnaires.collectAsState()

    // at most one item is open, so at most one image is in memory however many screenshots there are
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    val loaded = screenshots
    if (loaded == null) {
        Text(stringResource(R.string.screenshot_rating_loading))
    } else if (loaded.isEmpty()) {
        Text(stringResource(R.string.screenshot_rating_empty))
    } else {
        Column {
            for (screenshot in loaded) {
                val completed = values[screenshot.snapshotId].orEmpty().isNotEmpty()
                AccordionItem(
                    title = screenshot.label(),
                    expanded = expandedId == screenshot.snapshotId,
                    onToggle = {
                        expandedId = if (expandedId == screenshot.snapshotId) null else screenshot.snapshotId
                    },
                    icon = { if (completed) Icon(Icons.Default.Check, contentDescription = null) },
                    enabled = !completed
                ) {
                    Column {
                        ScreenshotImage(screenshot)
                        QuestionnaireHost(
                            // only screenshots with a questionnaire are listed
                            questionnaire = questionnaires.getValue(screenshot.packageName),
                            emptyMap(), // todo: replacements in ratings component not supported for now
                            onSave = { newValues -> viewModel.setValue(screenshot.snapshotId, newValues) },
                            embedded = true,
                            hostKey = "hosted_questionnaire_${element.questionnaireId}_rating_${screenshot.snapshotId}"
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
