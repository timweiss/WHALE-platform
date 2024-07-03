package de.mimuc.senseeverything.api.model

import org.json.JSONObject

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

fun emptyQuestionnaire(): FullQuestionnaire {
    return FullQuestionnaire(
        Questionnaire("", 0, 0, 0, false),
        emptyList(),
        emptyList()
    )
}

fun fakeQuestionnaire(): FullQuestionnaire {
    return FullQuestionnaire(
        Questionnaire("Test", 1, 1, 1, true),
        listOf(
            TextViewElement(1, 1, 1, 1, JSONObject(), "Hello World"),
            RadioGroupElement(2, 1, 1, 2, JSONObject(), listOf("Option 1", "Option 2")),
            CheckboxGroupElement(3, 1, 1, 3, JSONObject(), listOf("Option 1", "Option 2")),
            TextViewElement(4, 1, 2, 1, JSONObject(), "Goodbye World"),
            SliderElement(5, 1, 2, 2, JSONObject(), 0, 10, 5.0),
            TextEntryElement(6, 1, 2, 3, JSONObject(), "Enter your name")
        ),
        listOf(
            QuestionnaireTrigger(1, 1, "time", JSONObject()),
            QuestionnaireTrigger(2, 1, "location", JSONObject())
        )
    )
}