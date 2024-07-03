package de.mimuc.senseeverything.api.model


open class ElementValue(val elementId: Int) {
    open fun getSerializedValue(): String {
        return ""
    }
}

class RadioGroupValue(elementId: Int, var value: String) : ElementValue(elementId) {
    override fun getSerializedValue(): String {
        return value
    }
}

class CheckboxGroupValue(elementId: Int, var values: List<String>) : ElementValue(elementId) {
    override fun getSerializedValue(): String {
        return values.joinToString(",")
    }
}

class SliderValue(elementId: Int, var value: Double) : ElementValue(elementId) {
    override fun getSerializedValue(): String {
        return value.toString()
    }
}

class TextEntryValue(elementId: Int, var value: String) : ElementValue(elementId) {
    override fun getSerializedValue(): String {
        return value
    }
}

fun emptyValueForElement(element: QuestionnaireElement): ElementValue {
    return when (element.type) {
        "radio_group" -> RadioGroupValue(element.id, "")
        "checkbox_group" -> CheckboxGroupValue(element.id, emptyList())
        "slider" -> SliderValue(element.id, 0.0)
        "text_entry" -> TextEntryValue(element.id, "")
        else -> ElementValue(element.id)
    }
}