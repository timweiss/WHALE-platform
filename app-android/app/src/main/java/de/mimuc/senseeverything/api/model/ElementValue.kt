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
        "radio_group" -> RadioGroupValue(element.id, element.name, "")
        "checkbox_group" -> CheckboxGroupValue(element.id, element.name, emptyList())
        "slider" -> SliderValue(element.id,element.name, 0.0)
        "text_entry" -> TextEntryValue(element.id, element.name, "")
        else -> ElementValue(element.id, element.name)
    }
}