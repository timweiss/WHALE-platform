package de.mimuc.senseeverything.api.model

import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemRatingsValueTest {
    @Test
    fun partialScreenshotRatingsSurviveSavingAndReopening() {
        val value = ScreenshotRatingValue(
            7, "screenshots",
            mapOf("a-uuid" to mapOf(1 to RadioGroupValue(1, "mood", 2)), "b-uuid" to emptyMap())
        )

        val json = ElementValue.valueMapToJson(mapOf(7 to value)).toString()
        val restored = ElementValue.valueMapFromJson(JSONArray(json)).getValue(7) as ScreenshotRatingValue

        assertEquals(QuestionnaireElementType.SCREENSHOT_RATING, restored.elementType)
        assertEquals(2, (restored.values.getValue("a-uuid").getValue(1) as RadioGroupValue).value)
        assertTrue(restored.values.getValue("b-uuid").isEmpty())
        assertFalse(restored.isAnswered())
    }

    @Test
    fun contactRatingsSurviveSavingAndReopening() {
        val value = SocialNetworkRatingValue(
            8, "contacts",
            mapOf(3 to mapOf(1 to RadioGroupValue(1, "closeness", 4)), 5 to emptyMap())
        )

        val json = ElementValue.valueMapToJson(mapOf(8 to value)).toString()
        val restored = ElementValue.valueMapFromJson(JSONArray(json)).getValue(8) as SocialNetworkRatingValue

        assertEquals(QuestionnaireElementType.SOCIAL_NETWORK_RATING, restored.elementType)
        assertEquals(4, (restored.values.getValue(3).getValue(1) as RadioGroupValue).value)
        assertTrue(restored.values.getValue(5).isEmpty())
        assertFalse(restored.isAnswered())
    }

    @Test
    fun uploadedValueIsParseableJsonWithTypedAnswers() {
        val value = SocialNetworkRatingValue(
            8, "contacts",
            mapOf(3 to mapOf(2 to CheckboxGroupValue(2, "topics", listOf("work", "family"))), 5 to emptyMap())
        )

        // the backend stores only this string
        val ratings = JSONObject(value.getSerializedValue()).getJSONObject("ratings")

        val topics = ratings.getJSONObject("3").getJSONObject("2")
        assertEquals("topics", topics.getString("elementName"))
        assertEquals("checkbox_group", topics.getString("elementType"))
        assertEquals(listOf("work", "family"), topics.getJSONObject("valueJson").getJSONArray("values").let { values ->
            (0 until values.length()).map { values.getString(it) }
        })
        assertEquals(0, ratings.getJSONObject("5").length())
    }
}
