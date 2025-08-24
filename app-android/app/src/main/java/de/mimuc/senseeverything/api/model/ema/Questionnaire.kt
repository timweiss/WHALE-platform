package de.mimuc.senseeverything.api.model.ema

import org.json.JSONArray
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

data class FullQuestionnaire(
    val questionnaire: Questionnaire,
    val elements: List<QuestionnaireElement>,
    val triggers: List<QuestionnaireTrigger>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("questionnaire", questionnaire.toJson())
        val elementsJson = JSONArray(elements.map { it.toJson() })
        json.put("elements", elementsJson)
        val triggersJson = JSONArray(triggers.map { it.toJson() })
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

fun makeQuestionnaireFromJson(json: JSONObject): Questionnaire {
    val name = json.getString("name")
    val id = json.getInt("id")
    val version = json.getInt("version")
    val studyId = json.getInt("studyId")
    val enabled = json.getBoolean("enabled")

    return Questionnaire(name, id, version, studyId, enabled)
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