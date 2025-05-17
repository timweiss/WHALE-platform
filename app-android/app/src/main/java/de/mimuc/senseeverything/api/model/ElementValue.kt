package de.mimuc.senseeverything.api.model

import org.json.JSONObject


open class ElementValue(val elementId: Int, val elementName: String) {
    open fun getSerializedValue(): String {
        return ""
    }

    open fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("elementId", elementId)
        json.put("elementName", elementName)
        json.put("value", getSerializedValue())
        return json
    }
}

class RadioGroupValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return value
    }
}

class CheckboxGroupValue(elementId: Int, elementName: String, var values: List<String>) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }
}

class SocialNetworkEntryValue(elementId: Int, elementName: String, var values: List<Long>) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }
}

class SocialNetworkRatingValue(elementId: Int, elementName: String, var values: Map<Int, Map<Int, ElementValue>>) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return values.entries.joinToString(",") { (key, value) ->
            "$key:${value.entries.joinToString(",") { (k, v) -> "${v.elementName}:${v.getSerializedValue()}" }}"
        }
    }
}

class SliderValue(elementId: Int, elementName: String, var value: Double) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return value.toString()
    }
}

class TextEntryValue(elementId: Int, elementName: String, var value: String) : ElementValue(elementId, elementName) {
    override fun getSerializedValue(): String {
        return value
    }
}

fun emptyValueForElement(element: QuestionnaireElement): ElementValue {
    return when (element.type) {
        QuestionnaireElementType.RADIO_GROUP -> RadioGroupValue(element.id, element.name, "")
        QuestionnaireElementType.CHECKBOX_GROUP -> CheckboxGroupValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SLIDER -> SliderValue(element.id,element.name, 0.0)
        QuestionnaireElementType.TEXT_ENTRY -> TextEntryValue(element.id, element.name, "")
        QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> SocialNetworkEntryValue(element.id, element.name, emptyList())
        QuestionnaireElementType.SOCIAL_NETWORK_RATING -> SocialNetworkRatingValue(element.id, element.name, emptyMap())
        else -> ElementValue(element.id, element.name)
    }
}