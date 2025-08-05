package de.mimuc.senseeverything.api.model

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

    val isAnswer: Boolean get() {
        return elementType != QuestionnaireElementType.TEXT_VIEW
    }

    companion object {
        fun valueMapToJson(values: Map<Int, ElementValue>): JSONObject {
            val json = JSONObject()
            for ((key, value) in values) {
                json.put(key.toString(), value.toJson())
            }
            return json
        }

        fun valueMapFromJson(json: JSONObject): Map<Int, ElementValue> {
            val values = mutableMapOf<Int, ElementValue>()
            for (key in json.keys()) {
                val valueJson = json.getJSONObject(key)
                val value = fromJson(valueJson)
                values[key.toInt()] = value
            }
            return values
        }

        fun fromJson(json: JSONObject): ElementValue {
            val elementId = json.getInt("elementId")
            val elementName = json.getString("elementName")
            val elementType = QuestionnaireElementType.fromApiName(json.getString("elementType"))

            return when (elementType) {
                QuestionnaireElementType.RADIO_GROUP -> RadioGroupValue(
                    elementId, elementName, json.getInt("value")
                )
                QuestionnaireElementType.CHECKBOX_GROUP -> CheckboxGroupValue(
                    elementId, elementName, json.getJSONArray("values").let { valuesJson ->
                        (0 until valuesJson.length()).map { index -> valuesJson.getString(index) }
                    }
                )
                QuestionnaireElementType.SLIDER -> SliderValue(
                    elementId, elementName, json.getDouble("value")
                )
                QuestionnaireElementType.TEXT_ENTRY -> TextEntryValue(
                    elementId, elementName, json.getString("value")
                )
                QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> SocialNetworkEntryValue(
                    elementId, elementName, json.getJSONArray("values").let { valuesJson ->
                        (0 until valuesJson.length()).map { index -> valuesJson.getLong(index) }
                    }
                )
                QuestionnaireElementType.SOCIAL_NETWORK_RATING -> {
                    val ratingsJson = json.getJSONObject("ratings")
                    val ratings = mutableMapOf<Int, Map<Int, ElementValue>>()

                    for (key in ratingsJson.keys()) {
                        val entryJson = ratingsJson.getJSONObject(key)
                        val entryRatings = mutableMapOf<Int, ElementValue>()
                        for (k in entryJson.keys()) {
                            val valueJson = entryJson.getJSONObject(k)
                            val value = fromJson(valueJson)
                            entryRatings[k.toInt()] = value
                        }
                        ratings[key.toInt()] = entryRatings
                    }

                    SocialNetworkRatingValue(
                        elementId, elementName, ratings
                    )
                }
                QuestionnaireElementType.CIRCUMPLEX -> CircumplexValue(
                    elementId, elementName,
                    Pair(json.getDouble("x"), json.getDouble("y"))
                )
                else -> ElementValue(elementId, elementName, QuestionnaireElementType.MALFORMED)
            }
        }
    }
}

class RadioGroupValue(elementId: Int, elementName: String, var value: Int) : ElementValue(elementId, elementName, QuestionnaireElementType.RADIO_GROUP) {
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
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("values", values)
        return json
    }
}

class SocialNetworkEntryValue(elementId: Int, elementName: String, var values: List<Long>) : ElementValue(elementId, elementName, QuestionnaireElementType.SOCIAL_NETWORK_ENTRY) {
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("values", values)
        return json
    }
}

class SocialNetworkRatingValue(elementId: Int, elementName: String, var values: Map<Int, Map<Int, ElementValue>>) : ElementValue(elementId, elementName, QuestionnaireElementType.SOCIAL_NETWORK_RATING) {
    override fun getSerializedValue(): String {
        return values.entries.joinToString(",") { (key, value) ->
            "$key:${value.entries.joinToString(",") { (k, v) -> "${v.elementName}:${v.getSerializedValue()}" }}"
        }
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        val ratingsJson = JSONObject()
        for ((key, value) in values) {
            val entryJson = JSONObject()
            for ((k, v) in value) {
                entryJson.put(k.toString(), v.valueAsJsonObject())
            }
            ratingsJson.put(key.toString(), entryJson)
        }
        json.put("ratings", ratingsJson)
        return json
    }
}

class SliderValue(elementId: Int, elementName: String, var value: Double) : ElementValue(elementId, elementName, QuestionnaireElementType.SLIDER) {
    override fun getSerializedValue(): String {
        return value.toString()
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

class CircumplexValue(elementId: Int, elementName: String, var value: Pair<Double, Double>) : ElementValue(elementId, elementName, QuestionnaireElementType.CIRCUMPLEX) {
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

class TextEntryValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName, QuestionnaireElementType.TEXT_ENTRY) {
    override fun getSerializedValue(): String {
        return value
    }

    override fun valueAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("value", value)
        return json
    }
}

fun emptyValueForElement(element: QuestionnaireElement): ElementValue {
    return when (element.type) {
        QuestionnaireElementType.RADIO_GROUP -> RadioGroupValue(element.id, element.name, -1)
        QuestionnaireElementType.CHECKBOX_GROUP -> CheckboxGroupValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SLIDER -> SliderValue(element.id,element.name, 0.0)
        QuestionnaireElementType.TEXT_ENTRY -> TextEntryValue(element.id, element.name, "")
        QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> SocialNetworkEntryValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SOCIAL_NETWORK_RATING -> SocialNetworkRatingValue(element.id, element.name, emptyMap())
        QuestionnaireElementType.CIRCUMPLEX -> CircumplexValue(element.id, element.name, (0.0 to 0.0))
        QuestionnaireElementType.MALFORMED -> ElementValue(element.id, element.name, element.type)
        else -> ElementValue(element.id, element.name, element.type)
    }
}