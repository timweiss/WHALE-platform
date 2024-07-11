package de.mimuc.senseeverything.service.esm

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.api.model.EventQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager

class EsmHandler {
    var triggers: List<QuestionnaireTrigger> = emptyList()

    fun initializeTriggers(dataStoreManager: DataStoreManager) {
        if (triggers.isNotEmpty()) {
            return
        }

        dataStoreManager.getQuestionnairesSync { questionnaires ->
            triggers = questionnaires.flatMap { it.triggers }
        }
    }

    fun handleEvent(eventName: String, context: Context, dataStoreManager: DataStoreManager) {
        val eventTriggers = triggers.filter { it.type == "event" }
        if (eventTriggers.isNotEmpty()) {
            // Handle event
            val matching = eventTriggers.find { (it as EventQuestionnaireTrigger).eventName == eventName }
            if (matching != null) {
                val trigger = matching as EventQuestionnaireTrigger
                dataStoreManager.getQuestionnairesSync { questionnaires ->
                    val matchingQuestionnaire = questionnaires.find { it.questionnaire.id == trigger.questionnaireId }
                    if (matchingQuestionnaire != null) {
                        // open questionnaire
                        val intent = Intent(context, QuestionnaireActivity::class.java)
                        intent.putExtra("questionnaire", matchingQuestionnaire.toJson().toString())
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}