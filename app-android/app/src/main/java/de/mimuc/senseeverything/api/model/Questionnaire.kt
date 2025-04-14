package de.mimuc.senseeverything.api.model

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

open class QuestionnaireTrigger(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val validDuration: Long,
    val configuration: Any
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("type", type)
        json.put("validDuration", validDuration)
        json.put("configuration", configuration)
        return json
    }
}

class EventQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val eventName: String,
) : QuestionnaireTrigger(id, questionnaireId, "event", validUntil, configuration)

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

enum class PeriodicQuestionnaireTriggerInterval {
    DAILY,
    WEEKLY,
    MONTHLY
}

class PeriodicQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val interval: PeriodicQuestionnaireTriggerInterval,
    val time: String): QuestionnaireTrigger(id, questionnaireId, "periodic", validUntil, configuration)

class RandomEMAQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val distanceMinutes: Int,
    val randomToleranceMinutes: Int,
    val delayMinutes: Int,
    val timeBucket: String,
    val phaseName: String
): QuestionnaireTrigger(id, questionnaireId, "random_ema", validUntil, configuration)

class OneTimeQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val studyDay: Int,
    val time: String,
    val randomToleranceMinutes: Int
): QuestionnaireTrigger(id, questionnaireId, "one_time", validUntil, configuration)

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

fun makeTriggerFromJson(json: JSONObject): QuestionnaireTrigger {
    val id = json.getInt("id")
    val questionnaireId = json.getInt("questionnaireId")
    val type = json.getString("type")
    val validDuration = json.getLong("validDuration")
    val configuration = json.getJSONObject("configuration")

    when (type) {
        "event" -> {
            return EventQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getString("eventName"),
            )
        }

        "periodic" -> {
            return PeriodicQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                PeriodicQuestionnaireTriggerInterval.valueOf(configuration.getString("interval").uppercase()),
                configuration.getString("time")
            )
        }

        "random_ema" -> {
            return RandomEMAQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getInt("distanceMinutes"),
                configuration.getInt("randomToleranceMinutes"),
                configuration.getInt("delayMinutes"),
                configuration.getString("timeBucket"),
                configuration.getString("phaseName")
            )
        }

        "one_time" -> {
            return OneTimeQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getInt("studyDay"),
                configuration.getString("time"),
                configuration.getInt("randomToleranceMinutes")
            )
        }

        else -> {
            return QuestionnaireTrigger(id, questionnaireId, type, validDuration, configuration)
        }
    }
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