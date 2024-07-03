package de.mimuc.senseeverything.api.model

import org.json.JSONObject

open class QuestionnaireElement(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val step: Int,
    val position: Int,
    private val configuration: Any
) {
    open fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("type", type)
        json.put("step", step)
        json.put("position", position)
        json.put("configuration", JSONObject())
        return json
    }
}

fun makeElementFromJson(json: JSONObject): QuestionnaireElement? {
    val config = json.getJSONObject("configuration")
    val id = json.getInt("id")
    val questionnaireId = json.getInt("questionnaireId")
    val type = json.getString("type")
    val step = json.getInt("step")
    val position = json.getInt("position")
    val configuration = json.getJSONObject("configuration")

    when (type) {
        "text_view" -> {
            return TextViewElement(
                id,
                questionnaireId,
                step,
                position,
                configuration,
                config.getString("text")
            )
        }
        "radio_group" -> {
            val options = mutableListOf<String>()
            val optionsJson = config.getJSONArray("options")
            for (i in 0 until optionsJson.length()) {
                options.add(optionsJson.getString(i))
            }
            return RadioGroupElement(
                id,
                questionnaireId,
                step,
                position,
                configuration,
                options
            )
        }
        "checkbox_group" -> {
            val options = mutableListOf<String>()
            val optionsJson = config.getJSONArray("options")
            for (i in 0 until optionsJson.length()) {
                options.add(optionsJson.getString(i))
            }
            return CheckboxGroupElement(
                id,
                questionnaireId,
                step,
                position,
                configuration,
                options
            )
        }
        "slider" -> {
            return SliderElement(
                id,
                questionnaireId,
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
                step,
                position,
                configuration,
                config.getString("hint")
            )
        }
    }

    return null
}

class TextViewElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val textContent: String
) : QuestionnaireElement(id, questionnaireId, "text_view", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("content", textContent)
        return json
    }
}

class RadioGroupElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>
) : QuestionnaireElement(id, questionnaireId, "radio_group", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("options", options)
        return json
    }
}

class CheckboxGroupElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>
) : QuestionnaireElement(id, questionnaireId, "checkbox_group", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("options", options)
        return json
    }
}

class SliderElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val min: Int,
    val max: Int,
    val stepSize: Double
) : QuestionnaireElement(id, questionnaireId, "slider", step, position, configuration) {
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
    step: Int,
    position: Int,
    configuration: Any,
    val hint: String
) : QuestionnaireElement(id, questionnaireId, "text_entry", step, position, configuration) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("hint", hint)
        return json
    }
}
