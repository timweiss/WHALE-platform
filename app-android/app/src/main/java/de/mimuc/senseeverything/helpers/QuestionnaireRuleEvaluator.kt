package de.mimuc.senseeverything.helpers

import android.content.Context
import de.mimuc.senseeverything.api.model.ButtonGroupValue
import de.mimuc.senseeverything.api.model.CheckboxGroupValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.SliderValue
import de.mimuc.senseeverything.api.model.SocialNetworkEntryValue
import de.mimuc.senseeverything.api.model.TextEntryValue
import de.mimuc.senseeverything.api.model.ema.Action
import de.mimuc.senseeverything.api.model.ema.OpenQuestionnaire
import de.mimuc.senseeverything.api.model.ema.PutNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import de.mimuc.senseeverything.api.model.ema.QuestionnaireRule
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.service.esm.SamplingEventReceiver

class QuestionnaireRuleEvaluator(var rules: List<QuestionnaireRule>) {
    fun evaluate(elementValues: Map<Int, ElementValue>): Map<String, List<Action>> {
        val ruleActions = mutableMapOf<String, List<Action>>()

        for (rule in rules) {
            val conditionsMet = rule.conditions.conditions.map { condition ->
                val elementValue =
                    elementValues.entries.find { it.value.elementName == condition.fieldName }?.value.let {
                        elementValueAsString(it)
                    }
                        ?: return@map false

                when (condition.comparator) {
                    de.mimuc.senseeverything.api.model.ema.Comparator.EQUALS -> elementValue == condition.expectedValue.toString()
                        .trim('"')

                    de.mimuc.senseeverything.api.model.ema.Comparator.NOT_EQUALS -> elementValue != condition.expectedValue.toString()
                        .trim('"')
                }
            }

            val allConditionsMet = when (rule.conditions.operator) {
                de.mimuc.senseeverything.api.model.ema.LogicalOperator.AND -> conditionsMet.all { it }
                de.mimuc.senseeverything.api.model.ema.LogicalOperator.OR -> conditionsMet.any { it }
            }

            if (allConditionsMet) {
                ruleActions[rule.name] = rule.actions
            }
        }

        return ruleActions
    }

    private fun elementValueAsString(value: ElementValue?): String? {
        if (value == null) return null

        return when (value.elementType) {
            QuestionnaireElementType.RADIO_GROUP -> (value as RadioGroupValue).value.toString()
            QuestionnaireElementType.CHECKBOX_GROUP -> (value as CheckboxGroupValue).values.toString()
            QuestionnaireElementType.SLIDER -> (value as SliderValue).value.toString()
            QuestionnaireElementType.TEXT_ENTRY -> (value as TextEntryValue).value
            QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> (value as SocialNetworkEntryValue).values.toString()
            QuestionnaireElementType.BUTTON_GROUP -> (value as ButtonGroupValue).value
            QuestionnaireElementType.EXTERNAL_QUESTIONNAIRE_LINK -> null
            QuestionnaireElementType.SOCIAL_NETWORK_RATING -> null
            QuestionnaireElementType.CIRCUMPLEX -> null
            QuestionnaireElementType.LIKERT_SCALE_LABEL -> null
            QuestionnaireElementType.MALFORMED -> null
            QuestionnaireElementType.TEXT_VIEW -> null
        }
    }

    companion object {
        fun handleOpenQuestionnaireAction(context: Context, action: OpenQuestionnaire, source: PendingQuestionnaire) {
            SamplingEventReceiver.sendBroadcast(
                context = context,
                eventName = "open_questionnaire",
                source = source,
                triggerId = action.eventQuestionnaireTriggerId
            )
        }

        fun handlePutNotificationTriggerAction(context: Context, action: PutNotificationTrigger, source: PendingQuestionnaire) {
            SamplingEventReceiver.sendBroadcast(
                context = context,
                eventName = "put_notification_trigger",
                source = source,
                triggerId = action.triggerId
            )
        }

        fun handleActions(context: Context, actions: List<Action>, source: PendingQuestionnaire) {
            if (actions.isEmpty()) return

            for (action in actions) {
                when (action) {
                    is OpenQuestionnaire -> handleOpenQuestionnaireAction(context, action, source)
                    is PutNotificationTrigger -> handlePutNotificationTriggerAction(context, action, source)
                }
            }
        }
    }
}