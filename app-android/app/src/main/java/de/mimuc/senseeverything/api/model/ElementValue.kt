package de.mimuc.senseeverything.api.model

import de.mimuc.senseeverything.api.model.ema.QuestionnaireElement
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import org.json.JSONArray
import org.json.JSONObject


open class ElementValue(val elementId: Int, val elementName: String, val elementType: QuestionnaireElementType) {
    open fun getSerializedValue(): String {
        return ""
    }

    open fun valueAsJsonObject(): JSONObject {
        return JSONObject()
    }

    open fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("elementId", elementId)
        json.put("elementName", elementName)
        json.put("value", getSerializedValue())
        json.put("valueJson", valueAsJsonObject())
        json.put("elementType", elementType.apiName)
        return json
    }

    open fun isAnswered(): Boolean {
        return true
    }

    val isAnswer: Boolean
        get() {
            return listOf(
                QuestionnaireElementType.TEXT_VIEW,
                QuestionnaireElementType.LIKERT_SCALE_LABEL
            ).contains(elementType).not()
        }

    companion object {
        fun valueMapToJson(values: Map<Int, ElementValue>): JSONArray {
            val jsonArray = JSONArray()
            for (value in values.values) {
                jsonArray.put(value.toJson())
            }
            return jsonArray
        }

        fun valueMapFromJson(jsonArray: JSONArray): Map<Int, ElementValue> {
            val values = mutableMapOf<Int, ElementValue>()
            for (i in 0 until jsonArray.length()) {
                val valueJson = jsonArray.getJSONObject(i)
                val value = fromJson(valueJson)
                values[value.elementId] = value
            }
            return values
        }

        fun fromJson(json: JSONObject): ElementValue {
            val elementId = json.getInt("elementId")
            val elementName = json.getString("elementName")
            val elementType = QuestionnaireElementType.fromApiName(json.getString("elementType"))
            val valueJson = json.getJSONObject("valueJson")

            return when (elementType) {
                QuestionnaireElementType.RADIO_GROUP -> RadioGroupValue(
                    elementId, elementName, valueJson.getInt("value")
                )
                QuestionnaireElementType.CHECKBOX_GROUP -> CheckboxGroupValue(
                    elementId, elementName, valueJson.getJSONArray("values").let { entries ->
                        (0 until entries.length()).map { index -> entries.getString(index) }
                    }
                )
                QuestionnaireElementType.SLIDER -> SliderValue(
                    elementId, elementName, valueJson.getDouble("value")
                )
                QuestionnaireElementType.TEXT_ENTRY -> TextEntryValue(
                    elementId, elementName, valueJson.getString("value")
                )
                QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> SocialNetworkEntryValue(
                    elementId, elementName, valueJson.getJSONArray("values").let { entries ->
                        (0 until entries.length()).map { index -> entries.getLong(index) }
                    }
                )
                QuestionnaireElementType.SOCIAL_NETWORK_RATING -> SocialNetworkRatingValue(
                    elementId, elementName, ItemRatingsValue.ratingsFromJson(valueJson).mapKeys { it.key.toInt() }
                )
                QuestionnaireElementType.SCREENSHOT_RATING -> ScreenshotRatingValue(
                    elementId, elementName, ItemRatingsValue.ratingsFromJson(valueJson)
                )
                QuestionnaireElementType.CIRCUMPLEX -> CircumplexValue(
                    elementId, elementName,
                    Pair(valueJson.getDouble("x"), valueJson.getDouble("y"))
                )
                QuestionnaireElementType.BUTTON_GROUP -> ButtonGroupValue(
                    elementId, elementName, valueJson.getString("selectedButton")
                )
                QuestionnaireElementType.TIME_INPUT -> TimeInputValue(
                    elementId, elementName,
                    Pair(valueJson.getInt("hour"), valueJson.getInt("minute"))
                )
                QuestionnaireElementType.QUANTITY_ENTRY -> QuantityEntryValue(
                    elementId, elementName, valueJson.getString("value")
                )
                else -> ElementValue(elementId, elementName, QuestionnaireElementType.MALFORMED)
            }
        }
    }
}

class RadioGroupValue(elementId: Int, elementName: String, var value: Int) : ElementValue(elementId, elementName, QuestionnaireElementType.RADIO_GROUP) {
    override fun isAnswered(): Boolean {
        return value != -1
    }

    override fun getSerializedValue(): String {
        return value.toString()
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class CheckboxGroupValue(elementId: Int, elementName: String, var values: List<String>) : ElementValue(elementId, elementName, QuestionnaireElementType.CHECKBOX_GROUP) {
    override fun isAnswered(): Boolean {
        return values.isNotEmpty()
    }

    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("values", JSONArray(values))
        return json
    }
}

class SocialNetworkEntryValue(elementId: Int, elementName: String, var values: List<Long>) : ElementValue(elementId, elementName, QuestionnaireElementType.SOCIAL_NETWORK_ENTRY) {
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("values", JSONArray(values))
        return json
    }

    override fun isAnswered(): Boolean {
        return values.isNotEmpty()
    }
}

/**
 * Ratings of several items (contacts, screenshots), each answered with its own hosted questionnaire.
 * The item key [K] is stored as a JSON object key, so it has to survive a round trip through `toString()`.
 */
abstract class ItemRatingsValue<K>(
    elementId: Int,
    elementName: String,
    elementType: QuestionnaireElementType,
    var values: Map<K, Map<Int, ElementValue>>
) : ElementValue(elementId, elementName, elementType) {
    override fun isAnswered(): Boolean {
        // either no items exist (empty) or all need to be answered (so value is present) for it to be valid
        return values.isEmpty() || values.entries.all { it.value.isNotEmpty() }
    }

    /**
     * The same JSON as [valueAsJsonObject]: the backend only stores this string, and the nested answers
     * can't be told apart in a flat format (checkbox lists, times and free text contain the separators).
     */
    override fun getSerializedValue(): String {
        return valueAsJsonObject().toString()
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        val ratingsJson = JSONObject()
        for ((key, value) in values) {
            val entryJson = JSONObject()
            for ((k, v) in value) {
                // the full element, fromJson needs its type to restore it
                entryJson.put(k.toString(), v.toJson())
            }
            ratingsJson.put(key.toString(), entryJson)
        }
        json.put("ratings", ratingsJson)
        return json
    }

    companion object {
        /** Keyed as stored, the caller converts the keys back to its [K]. */
        fun ratingsFromJson(valueJson: JSONObject): Map<String, Map<Int, ElementValue>> {
            val ratingsJson = valueJson.getJSONObject("ratings")
            return ratingsJson.keys().asSequence().associateWith { key ->
                val entryJson = ratingsJson.getJSONObject(key)
                entryJson.keys().asSequence().associate { k -> k.toInt() to ElementValue.fromJson(entryJson.getJSONObject(k)) }
            }
        }
    }
}

/** Keyed by the contact's uid. */
class SocialNetworkRatingValue(elementId: Int, elementName: String, values: Map<Int, Map<Int, ElementValue>>) :
    ItemRatingsValue<Int>(elementId, elementName, QuestionnaireElementType.SOCIAL_NETWORK_RATING, values)

/** Keyed by the screenshot's snapshotId. */
class ScreenshotRatingValue(elementId: Int, elementName: String, values: Map<String, Map<Int, ElementValue>>) :
    ItemRatingsValue<String>(elementId, elementName, QuestionnaireElementType.SCREENSHOT_RATING, values)

class SliderValue(elementId: Int, elementName: String, var value: Double) : ElementValue(elementId, elementName, QuestionnaireElementType.SLIDER) {
    override fun isAnswered(): Boolean {
        return value != 0.0
    }

    override fun getSerializedValue(): String {
        return value.toString()
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class TextEntryValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName, QuestionnaireElementType.TEXT_ENTRY) {
    override fun isAnswered(): Boolean {
        return value.isNotBlank()
    }

    override fun getSerializedValue(): String {
        return value
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class QuantityEntryValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName, QuestionnaireElementType.QUANTITY_ENTRY) {
    override fun isAnswered(): Boolean {
        return value.isNotBlank()
    }

    override fun getSerializedValue(): String {
        return value
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class CircumplexValue(elementId: Int, elementName: String, var value: Pair<Double, Double>) : ElementValue(elementId, elementName, QuestionnaireElementType.CIRCUMPLEX) {
    override fun isAnswered(): Boolean {
        return value.first != 0.0 || value.second != 0.0
    }

    override fun getSerializedValue(): String {
        return "x:${value.first}, y:${value.second}"
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("x", value.first)
        json.put("y", value.second)
        return json
    }
}

class ButtonGroupValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName, QuestionnaireElementType.BUTTON_GROUP) {
    override fun isAnswered(): Boolean {
        return value.isNotBlank()
    }

    override fun getSerializedValue(): String {
        return value
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class TimeInputValue(elementId: Int, elementName: String, var value: Pair<Int, Int>) : ElementValue(elementId, elementName, QuestionnaireElementType.TIME_INPUT) {
    override fun isAnswered(): Boolean {
        return value.first != -1 && value.second != -1
    }

    override fun getSerializedValue(): String {
        return String.format("%02d:%02d", value.first, value.second)
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("hour", value.first)
        json.put("minute", value.second)
        return json
    }
}

fun emptyValueForElement(element: QuestionnaireElement): ElementValue {
    return when (element.type) {
        QuestionnaireElementType.RADIO_GROUP -> RadioGroupValue(element.id, element.name, -1)
        QuestionnaireElementType.CHECKBOX_GROUP -> CheckboxGroupValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SLIDER -> SliderValue(element.id, element.name, 0.0)
        QuestionnaireElementType.TEXT_ENTRY -> TextEntryValue(element.id, element.name, "")
        QuestionnaireElementType.QUANTITY_ENTRY -> QuantityEntryValue(element.id, element.name, "")
        QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> SocialNetworkEntryValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SOCIAL_NETWORK_RATING -> SocialNetworkRatingValue(element.id, element.name, emptyMap())
        QuestionnaireElementType.SCREENSHOT_RATING -> ScreenshotRatingValue(element.id, element.name, emptyMap())
        QuestionnaireElementType.CIRCUMPLEX -> CircumplexValue(element.id, element.name, (0.0 to 0.0))
        QuestionnaireElementType.BUTTON_GROUP -> ButtonGroupValue(element.id, element.name, "")
        QuestionnaireElementType.TIME_INPUT -> TimeInputValue(element.id, element.name, (-1 to -1))
        QuestionnaireElementType.MALFORMED -> ElementValue(element.id, element.name, element.type)
        else -> ElementValue(element.id, element.name, element.type)
    }
}
