package de.mimuc.senseeverything.api.model.ema

import kotlinx.serialization.Serializable

@Serializable
data class Questionnaire(
    val name: String,
    val id: Int,
    val version: Int,
    val studyId: Int,
    val enabled: Boolean
)

@Serializable
data class FullQuestionnaire(
    val questionnaire: Questionnaire,
    val elements: List<QuestionnaireElement>,
    val triggers: List<QuestionnaireTrigger>
)

fun emptyQuestionnaire(): FullQuestionnaire {
    return FullQuestionnaire(
        Questionnaire("", 0, 0, 0, false),
        emptyList(),
        emptyList()
    )
}

