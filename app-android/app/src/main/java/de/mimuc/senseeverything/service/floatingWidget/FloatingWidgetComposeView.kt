package de.mimuc.senseeverything.service.floatingWidget

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import java.util.UUID

/**
 * Bridge between Service and Jetpack Compose for the floating widget
 */
class FloatingWidgetComposeView(
    private val context: Context,
    private val questionnaire: FullQuestionnaire,
    private val triggerUid: UUID?,
    private val onComplete: (Map<Int, ElementValue>) -> Unit,
    private val onDismiss: () -> Unit
) : ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val floatingWidgetViewModel by lazy {
        ViewModelProvider(this)[FloatingWidgetViewModel::class.java]
    }

    private var composeView: ComposeView? = null

    fun createView(owner: SavedStateRegistryOwner?): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppandroidTheme {
                    FloatingQuestionnaireHost(
                        questionnaire = questionnaire,
                        triggerUid = triggerUid,
                        onComplete = onComplete,
                        onDismiss = onDismiss,
                        viewModel = floatingWidgetViewModel
                    )
                }
            }
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(this@FloatingWidgetComposeView)
        }.also {
            composeView = it
        }
    }

    fun getAttachedView(): ComposeView? = composeView

    fun dispose() {
        viewModelStore.clear()
        composeView = null
    }

    companion object {
        fun createLayoutParams(): WindowManager.LayoutParams {
            return WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
            }
        }
    }
}

@Composable
fun FloatingQuestionnaireHost(
    questionnaire: FullQuestionnaire,
    triggerUid: UUID?,
    onComplete: (Map<Int, ElementValue>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FloatingWidgetViewModel,
    modifier: Modifier = Modifier
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val elementValues by viewModel.elementValues.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()

    // Initialize the ViewModel with questionnaire data
    LaunchedEffect(questionnaire) {
        viewModel.initialize(questionnaire, triggerUid) // triggerUid will be passed from service
    }

    // Handle completion
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            onComplete(elementValues)
        }
    }

    val currentStepElements = remember(currentStep) {
        questionnaire.elements.filter { it.step == currentStep }
            .sortedBy { it.position }
    }

    if ((currentStepElements.isEmpty() || elementValues.isEmpty()) && !isCompleted) {
        // Show initial state or loading
        Card(
            modifier = modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = questionnaire.questionnaire.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Loading questionnaire...",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss")
                }
            }
        }
    } else {
        // Show current step
        FloatingQuestionStep(
            elements = currentStepElements,
            elementValues = elementValues,
            onElementValueChange = { elementId, value ->
                viewModel.updateElementValue(elementId, value)
            },
            onButtonSelection = { buttonElement, selectedButton ->
                viewModel.handleButtonSelection(buttonElement, selectedButton)
            },
            modifier = modifier
        )
    }
}
