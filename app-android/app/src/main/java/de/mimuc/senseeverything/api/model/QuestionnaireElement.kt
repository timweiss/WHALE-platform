package de.mimuc.senseeverything.api.model

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

open class QuestionnaireElement(
    val id: Int,
    val questionnaireId: Int,
    val name: String,
    val type: String,
    val step: Int,
    val position: Int,
    private val configuration: Any
) {
    open fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("name", name)
        json.put("type", type)
        json.put("step", step)
        json.put("position", position)
        json.put("configuration", JSONObject())
        return json
    }
}

fun getAlignmentValue(alignmentString: String): GroupAlignment {
    var alignmentEnum: GroupAlignment = GroupAlignment.Vertical
    when (alignmentString) {
        "horizontal" -> alignmentEnum = GroupAlignment.Horizontal
        "vertical" -> alignmentEnum = GroupAlignment.Vertical
        else -> Log.e("QuestionnaireElement", "Unknown alignment: $alignmentString")
    }

    return alignmentEnum
}

fun makeElementFromJson(json: JSONObject): QuestionnaireElement? {
    val config = json.getJSONObject("configuration")
    val id = json.getInt("id")
    val questionnaireId = json.getInt("questionnaireId")
    val type = json.getString("type")
    val step = json.getInt("step")
    val position = json.getInt("position")
    val configuration = json.getJSONObject("configuration")
    val name = json.getString("name")

    when (type) {
        "text_view" -> {
            return TextViewElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getString("text")
            )
        }
        "radio_group" -> {
            val options = mutableListOf<String>()
            val optionsJson = config.getJSONArray("options")
            val alignment = getAlignmentValue(config.getString("alignment"))

            for (i in 0 until optionsJson.length()) {
                options.add(optionsJson.getString(i))
            }
            return RadioGroupElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                options,
                alignment = alignment
            )
        }
        "checkbox_group" -> {
            val options = mutableListOf<String>()
            val optionsJson = config.getJSONArray("options")
            val alignment = getAlignmentValue(config.getString("alignment"))

            for (i in 0 until optionsJson.length()) {
                options.add(optionsJson.getString(i))
            }
            return CheckboxGroupElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                options,
                alignment = alignment
            )
        }
        "slider" -> {
            return SliderElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getInt("min"),
                config.getInt("max"),
                config.getDouble("stepSize")
            )
        }
        "text_entry" -> {
            return TextEntryElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getString("hint")
            )
        }
        "external_questionnaire_link" -> {
            val urlParams = mutableMapOf<String, String>()
            val urlParamsJson = config.getJSONArray("urlParams")
            for (i in 0 until urlParamsJson.length()) {
                val param = urlParamsJson.getJSONObject(i)
                urlParams[param.getString("key")] = param.getString("value")
            }
            return ExternalQuestionnaireLinkElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getString("externalUrl"),
                config.getString("actionText"),
                urlParams
            )
        }
    }

    return null
}

class TextViewElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val textContent: String
) : QuestionnaireElement(id, questionnaireId, name, "text_view", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("text", textContent)
        return json
    }
}

enum class GroupAlignment {
    Horizontal,
    Vertical
}

class RadioGroupElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>,
    val alignment: GroupAlignment = GroupAlignment.Vertical
) : QuestionnaireElement(id, questionnaireId, name, "radio_group", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("options", JSONArray(options))
        json.getJSONObject("configuration").put("alignment", alignment.toString().lowercase())
        return json
    }
}

class CheckboxGroupElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>,
    val alignment: GroupAlignment = GroupAlignment.Vertical
) : QuestionnaireElement(id, questionnaireId, name, "checkbox_group", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("options", JSONArray(options))
        json.getJSONObject("configuration").put("alignment", alignment.toString().lowercase())
        return json
    }
}

class SliderElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val min: Int,
    val max: Int,
    val stepSize: Double
) : QuestionnaireElement(id, questionnaireId, name, "slider", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("min", min)
        json.getJSONObject("configuration").put("max", max)
        json.getJSONObject("configuration").put("stepSize", stepSize)
        return json
    }
}

class TextEntryElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val hint: String
) : QuestionnaireElement(id, questionnaireId, name, "text_entry", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("hint", hint)
        return json
    }
}

class ExternalQuestionnaireLinkElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val externalUrl: String,
    val actionText: String,
    val urlParams: Map<String, String>
) : QuestionnaireElement(id, questionnaireId, name, "external_questionnaire_link", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("externalUrl", externalUrl)
        json.getJSONObject("configuration").put("actionText", actionText)
        val paramsJson = JSONArray()
        for ((key, value) in urlParams) {
            val param = JSONObject()
            param.put("key", key)
            param.put("value", value)
            paramsJson.put(param)
        }
        json.getJSONObject("configuration").put("urlParams", paramsJson)
        return json
    }
}
