package de.mimuc.senseeverything.activity.esm.screenshot

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    /**
     * The screenshot shown, an index into [screenshots]; `screenshots.size` is the summary once all are rated.
     * Lives here and not in the composable: the hosted questionnaires keep their onSave lambda across
     * configuration changes, and it must still reach the state that is shown.
     */
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

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
        // before the list, so the first screen shown is already the right one
        _currentIndex.value = nextUnratedIndex(rateable, _ratingValues.value, from = 0)
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

    /** Stores the rating and moves on to the next unrated screenshot, or the summary if there is none. */
    fun saveRating(snapshotId: String, value: Map<Int, ElementValue>) {
        _ratingValues.value = _ratingValues.value + (snapshotId to value)
        onValueChange(_ratingValues.value)

        val screenshots = _screenshots.value ?: return
        val saved = screenshots.indexOfFirst { it.snapshotId == snapshotId }
        _currentIndex.value = nextUnratedIndex(screenshots, _ratingValues.value, from = saved + 1)
    }

    fun showPrevious() {
        if (_currentIndex.value > 0) _currentIndex.value--
    }

    fun showNext() {
        val screenshots = _screenshots.value ?: return
        if (_currentIndex.value < lastNavigableIndex(screenshots, _ratingValues.value)) _currentIndex.value++
    }

    private companion object {
        const val TAG = "ScreenshotRating"
    }
}

private fun isRated(values: Map<String, Map<Int, ElementValue>>, snapshotId: String) =
    values[snapshotId].orEmpty().isNotEmpty()

/** Searches from [from] to the end, then from the start; `screenshots.size` (the summary) if all are rated. */
internal fun nextUnratedIndex(
    screenshots: List<ScreenshotReference>,
    values: Map<String, Map<Int, ElementValue>>,
    from: Int
): Int {
    val order = (from until screenshots.size) + (0 until minOf(from, screenshots.size))
    return order.firstOrNull { !isRated(values, screenshots[it].snapshotId) } ?: screenshots.size
}

/** The summary can only be reached once every screenshot is rated. */
internal fun lastNavigableIndex(
    screenshots: List<ScreenshotReference>,
    values: Map<String, Map<Int, ElementValue>>
): Int = if (screenshots.all { isRated(values, it.snapshotId) }) screenshots.size else screenshots.size - 1

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
    val index by viewModel.currentIndex.collectAsState()

    val loaded = screenshots
    if (loaded == null) {
        Text(stringResource(R.string.screenshot_rating_loading))
    } else if (loaded.isEmpty()) {
        Text(stringResource(R.string.screenshot_rating_empty))
    } else {
        val ratedCount = loaded.count { isRated(values, it.snapshotId) }

        // Save sits below the questions, so the next screenshot would otherwise start scrolled to its bottom
        val headerRequester = remember { BringIntoViewRequester() }
        var shownIndex by rememberSaveable { mutableIntStateOf(index) }
        LaunchedEffect(index) {
            // not on first display, the element may be further down in the questionnaire
            if (index != shownIndex) {
                shownIndex = index
                headerRequester.bringIntoView()
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(headerRequester),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::showPrevious, enabled = index > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.screenshot_rating_previous)
                    )
                }
                Text(
                    if (index < loaded.size) {
                        stringResource(R.string.screenshot_rating_position, index + 1, loaded.size)
                    } else {
                        pluralStringResource(R.plurals.screenshot_rating_all_rated, loaded.size, loaded.size)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = viewModel::showNext, enabled = index < lastNavigableIndex(loaded, values)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.screenshot_rating_next)
                    )
                }
            }
            // below the pager, next to the title it gets tight on small screens
            val left = loaded.size - ratedCount
            if (left > 0) {
                Text(
                    pluralStringResource(R.plurals.screenshot_rating_left, left, left),
                    style = MaterialTheme.typography.bodyMedium,
                    // in line with the arrow icon
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            LinearProgressIndicator(
                progress = { ratedCount.toFloat() / loaded.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (index < loaded.size) {
                val screenshot = loaded[index]
                // separate state per screenshot, so nothing remembered carries over to the next one
                key(screenshot.snapshotId) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(screenshot.label(), modifier = Modifier.weight(1f))
                        if (isRated(values, screenshot.snapshotId)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.screenshot_rating_rated),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    ScreenshotImage(screenshot)
                    QuestionnaireHost(
                        // only screenshots with a questionnaire are listed
                        questionnaire = questionnaires.getValue(screenshot.packageName),
                        emptyMap(), // todo: replacements in ratings component not supported for now
                        onSave = { newValues -> viewModel.saveRating(screenshot.snapshotId, newValues) },
                        // shows a saved rating again, also after the process was restarted
                        initialValues = values[screenshot.snapshotId].orEmpty(),
                        embedded = true,
                        hostKey = "hosted_questionnaire_${element.questionnaireId}_rating_${screenshot.snapshotId}"
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.screenshot_rating_done_instruction),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
