package de.mimuc.senseeverything.api.model

import org.json.JSONObject

open class QuestionnaireElement(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val step: Int,
    val position: Int,
    private val configuration: Any
)

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
            TextViewElement(
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
            RadioGroupElement(
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
            CheckboxGroupElement(
                id,
                questionnaireId,
                step,
                position,
                configuration,
                options
            )
        }
        "slider" -> {
            SliderElement(
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
            TextEntryElement(
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
    val textContet: String
) : QuestionnaireElement(id, questionnaireId, "text_view", step, position, configuration)

class RadioGroupElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>
) : QuestionnaireElement(id, questionnaireId, "radio_group", step, position, configuration)

class CheckboxGroupElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>
) : QuestionnaireElement(id, questionnaireId, "checkbox_group", step, position, configuration)

class SliderElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val min: Int,
    val max: Int,
    val stepSize: Double
) : QuestionnaireElement(id, questionnaireId, "slider", step, position, configuration)

class TextEntryElement(
    id: Int,
    questionnaireId: Int,
    step: Int,
    position: Int,
    configuration: Any,
    val hint: String
) : QuestionnaireElement(id, questionnaireId, "text_entry", step, position, configuration)
