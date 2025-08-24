package de.mimuc.senseeverything.api.model.ema

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class QuestionnaireElementType(val apiName: String) {
    MALFORMED("malformed"),
    TEXT_VIEW("text_view"),
    RADIO_GROUP("radio_group"),
    CHECKBOX_GROUP("checkbox_group"),
    SLIDER("slider"),
    TEXT_ENTRY("text_entry"),
    EXTERNAL_QUESTIONNAIRE_LINK("external_questionnaire_link"),
    SOCIAL_NETWORK_ENTRY("social_network_entry"),
    SOCIAL_NETWORK_RATING("social_network_rating"),
    CIRCUMPLEX("circumplex"),
    LIKERT_SCALE_LABEL("likert_scale_label");

    companion object {
        fun fromApiName(apiName: String): QuestionnaireElementType? {
            return QuestionnaireElementType.entries.find { it.apiName == apiName }
                ?: throw IllegalArgumentException("Unknown QuestionnaireElementType: $apiName")
        }
    }
}

open class QuestionnaireElement(
    val id: Int,
    val questionnaireId: Int,
    val name: String,
    val type: QuestionnaireElementType,
    val step: Int,
    val position: Int,
    private val configuration: Any
) {
    open fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("name", name)
        json.put("type", type.apiName)
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
    val type = QuestionnaireElementType.fromApiName(json.getString("type"))
    val step = json.getInt("step")
    val position = json.getInt("position")
    val configuration = json.getJSONObject("configuration")
    val name = json.getString("name")

    when (type) {
        QuestionnaireElementType.TEXT_VIEW -> {
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

        QuestionnaireElementType.RADIO_GROUP -> {
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

        QuestionnaireElementType.CHECKBOX_GROUP -> {
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

        QuestionnaireElementType.SLIDER -> {
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

        QuestionnaireElementType.TEXT_ENTRY -> {
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

        QuestionnaireElementType.EXTERNAL_QUESTIONNAIRE_LINK -> {
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

        QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> {
            return SocialNetworkEntryElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration
            )
        }

        QuestionnaireElementType.SOCIAL_NETWORK_RATING -> {
            return SocialNetworkRatingElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getInt("ratingQuestionnaireId")
            )
        }

        QuestionnaireElementType.CIRCUMPLEX -> {
            var clipTop = 0
            var clipBottom = 0
            var clipLeft = 0
            var clipRight = 0
            if (config.has("clip")) {
                clipTop = config.getJSONObject("clip").getInt("top")
                clipBottom = config.getJSONObject("clip").getInt("bottom")
                clipLeft = config.getJSONObject("clip").getInt("left")
                clipRight = config.getJSONObject("clip").getInt("right")
            }

            return CircumplexElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                config.getString("imageUrl"),
                clipTop,
                clipBottom,
                clipLeft,
                clipRight
            )
        }

        QuestionnaireElementType.LIKERT_SCALE_LABEL -> {
            val options = mutableListOf<String>()
            val optionsJson = config.getJSONArray("options")

            for (i in 0 until optionsJson.length()) {
                options.add(optionsJson.getString(i))
            }
            return LikertScaleLabelElement(
                id,
                questionnaireId,
                name,
                step,
                position,
                configuration,
                options
            )
        }

        QuestionnaireElementType.MALFORMED -> {
            // show nothing
        }

        null -> {
            throw IllegalArgumentException("Unknown QuestionnaireElementType: ${json.getString("type")}")
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
) : QuestionnaireElement(
    id, questionnaireId, name,
    QuestionnaireElementType.TEXT_VIEW, step, position, configuration
) {
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
) : QuestionnaireElement(
    id, questionnaireId, name,
    QuestionnaireElementType.RADIO_GROUP, step, position, configuration
) {
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
) : QuestionnaireElement(
    id, questionnaireId, name,
    QuestionnaireElementType.CHECKBOX_GROUP, step, position, configuration
) {
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
) : QuestionnaireElement(id, questionnaireId, name,
    QuestionnaireElementType.SLIDER, step, position, configuration) {
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
) : QuestionnaireElement(id, questionnaireId, name,
    QuestionnaireElementType.TEXT_ENTRY, step, position, configuration) {
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
) : QuestionnaireElement(
    id,
    questionnaireId,
    name,
    QuestionnaireElementType.EXTERNAL_QUESTIONNAIRE_LINK,
    step,
    position,
    configuration
) {
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

class SocialNetworkEntryElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
) : QuestionnaireElement(
    id,
    questionnaireId,
    name,
    QuestionnaireElementType.SOCIAL_NETWORK_ENTRY,
    step,
    position,
    configuration
) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        return json
    }
}

class SocialNetworkRatingElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val ratingQuestionnaireId: Int,
) : QuestionnaireElement(
    id,
    questionnaireId,
    name,
    QuestionnaireElementType.SOCIAL_NETWORK_RATING,
    step,
    position,
    configuration
) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("ratingQuestionnaireId", ratingQuestionnaireId)
        return json
    }
}

class CircumplexElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val imageUrl: String,
    val clipTop: Int = 0,
    val clipBottom: Int = 0,
    val clipLeft: Int = 0,
    val clipRight: Int = 0
) : QuestionnaireElement(
    id,
    questionnaireId,
    name,
    QuestionnaireElementType.CIRCUMPLEX,
    step,
    position,
    configuration
) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("imageUrl", imageUrl)
        val clipJson = JSONObject()
        clipJson.put("top", clipTop)
        clipJson.put("bottom", clipBottom)
        clipJson.put("left", clipLeft)
        clipJson.put("right", clipRight)
        json.getJSONObject("configuration").put("clip", clipJson)
        return json
    }
}

class LikertScaleLabelElement(
    id: Int,
    questionnaireId: Int,
    name: String,
    step: Int,
    position: Int,
    configuration: Any,
    val options: List<String>
) : QuestionnaireElement(
    id, questionnaireId, name,
    QuestionnaireElementType.LIKERT_SCALE_LABEL, step, position, configuration
) {
    override fun toJson(): JSONObject {
        val json = super.toJson()
        json.getJSONObject("configuration").put("options", JSONArray(options))
        return json
    }
}