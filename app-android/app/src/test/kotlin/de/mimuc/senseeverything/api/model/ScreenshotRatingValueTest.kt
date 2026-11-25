package de.mimuc.senseeverything.api.model

import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenshotRatingValueTest {
    @Test
    fun partialRatingsSurviveSavingAndReopening() {
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
}
