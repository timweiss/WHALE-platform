package de.mimuc.senseeverything.api.model

data class Questionnaire(
    val name: String,
    val id: Int,
    val version: Int,
    val studyId: Int,
    val enabled: Boolean
)

data class QuestionnaireTrigger(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val configuration: Any
)

data class FullQuestionnaire(
    val questionnaire: Questionnaire,
    val elements: List<QuestionnaireElement>,
    val triggers: List<QuestionnaireTrigger>
)