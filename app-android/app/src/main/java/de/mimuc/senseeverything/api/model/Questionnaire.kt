package de.mimuc.senseeverything.api.model

import org.json.JSONObject

data class Questionnaire(
    val name: String,
    val id: Int,
    val version: Int,
    val studyId: Int,
    val enabled: Boolean
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("id", id)
        json.put("version", version)
        json.put("studyId", studyId)
        json.put("enabled", enabled)
        return json
    }
}

data class QuestionnaireTrigger(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val configuration: Any
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("type", type)
        json.put("configuration", configuration)
        return json
    }
}

data class FullQuestionnaire(
    val questionnaire: Questionnaire,
    val elements: List<QuestionnaireElement>,
    val triggers: List<QuestionnaireTrigger>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("questionnaire", questionnaire.toJson())
        val elementsJson = elements.map { it.toJson() }
        json.put("elements", elementsJson)
        val triggersJson = triggers.map { it.toJson() }
        json.put("triggers", triggersJson)
        return json
    }
}

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

fun makeQuestionnaireFromJson(json: JSONObject): Questionnaire {
    val name = json.getString("name")
    val id = json.getInt("id")
    val version = json.getInt("version")
    val studyId = json.getInt("studyId")
    val enabled = json.getBoolean("enabled")

    return Questionnaire(name, id, version, studyId, enabled)
}

fun makeTriggerFromJson(json: JSONObject): QuestionnaireTrigger {
    val id = json.getInt("id")
    val questionnaireId = json.getInt("questionnaireId")
    val type = json.getString("type")
    val configuration = json.getJSONObject("configuration")

    return QuestionnaireTrigger(id, questionnaireId, type, configuration)
}

fun makeFullQuestionnaireFromJson(json: JSONObject): FullQuestionnaire {
    val questionnaire = makeQuestionnaireFromJson(json.getJSONObject("questionnaire"))
    val elementsJson = json.getJSONArray("elements")
    val elements = mutableListOf<QuestionnaireElement>()
    for (i in 0 until elementsJson.length()) {
        val elementJson = elementsJson.getJSONObject(i)
        val element = makeElementFromJson(elementJson)
        if (element != null) {
            elements.add(element)
        }
    }
    val triggersJson = json.getJSONArray("triggers")
    val triggers = mutableListOf<QuestionnaireTrigger>()
    for (i in 0 until triggersJson.length()) {
        val triggerJson = triggersJson.getJSONObject(i)
        triggers.add(makeTriggerFromJson(triggerJson))
    }

    return FullQuestionnaire(questionnaire, elements, triggers)
}