package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.ema.Action
import de.mimuc.senseeverything.api.model.ema.ApproveScreenshotUpload
import de.mimuc.senseeverything.api.model.ema.Comparator
import de.mimuc.senseeverything.api.model.ema.Condition
import de.mimuc.senseeverything.api.model.ema.ConditionGroup
import de.mimuc.senseeverything.api.model.ema.LogicalOperator
import de.mimuc.senseeverything.api.model.ema.QuestionnaireRule
import de.mimuc.senseeverything.api.model.ema.ruleJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.collections.emptySet

class ApprovedScreenshotsTest {
    @TempDir
    lateinit var dir: File

    private val app = "com.instagram.android"

    private fun sample(snapshotId: String): ScreenshotStore.StoredSample {
        val root = File(dir, "samples")
        val store = ScreenshotStore(root, 1_000L, log = {})
        store.commit(app, 0, store.newImageFile(app).apply { writeText("image") }, 42L, "signature", snapshotId)
        return ScreenshotStore.readPeriod(root, 1_000L).single()
    }

    @Test
    fun imageOutlivesItsPeriod() {
        val sample = sample("a")
        val image = ApprovedScreenshots.copyImage(File(dir, "approved"), sample)
        // approving twice overwrites it
        ApprovedScreenshots.copyImage(File(dir, "approved"), sample)
        assertTrue(sample.image.exists())

        ScreenshotStore.periodDir(File(dir, "samples"), 1_000L).deleteRecursively()
        assertEquals("image", image.readText())
    }

    @Test
    fun readingRefersToTheImage() {
        val row = ApprovedScreenshots.toLogData(sample("a"), File("/approved/a.jpg"), now = 5_000L)

        assertEquals("Screenshot", row.sensorName)
        assertEquals(5_000L, row.timestamp)
        assertTrue(row.hasFile)
        assertEquals("/approved/a.jpg", row.filePath)
        assertEquals("""{"snapshotId":"a","packageName":"$app","timestamp":42,"periodEnd":1000}""", row.data)
    }

    @Test
    fun localIdFollowsFromTheScreenshot() {
        val image = File("/approved/image.jpg")
        val first = ApprovedScreenshots.toLogData(sample("a"), image, now = 1L).localId

        assertEquals(first, ApprovedScreenshots.toLogData(sample("a"), image, now = 2L).localId)
        assertNotEquals(first, ApprovedScreenshots.toLogData(sample("b"), image, now = 1L).localId)
    }

    @Test
    fun onlyOldImagesWithoutReadingAreDeleted() {
        val hour = 3_600_000L
        val now = 1_800_000_000_000L
        fun image(name: String, age: Long) = File(dir, name).apply {
            writeText("image")
            setLastModified(now - age)
        }
        val referenced = image("referenced.jpg", 2 * hour)
        val orphan = image("orphan.jpg", 2 * hour)
        // its reading may be about to be added
        val recent = image("recent.jpg", hour / 2)

        assertEquals(1, ApprovedScreenshots.deleteUnreferenced(dir, setOf(referenced.path), now))
        assertTrue(referenced.exists())
        assertTrue(recent.exists())
        assertFalse(orphan.exists())
        // nothing was ever approved
        assertEquals(0, ApprovedScreenshots.deleteUnreferenced(File(dir, "missing"), emptySet(), now))
    }

    @Test
    fun actionIsReadFromTheRuleConfiguration() {
        assertEquals(
            ApproveScreenshotUpload,
            ruleJson.decodeFromString<Action>("""{"type":"approve_screenshot_upload"}""")
        )
    }

    @Test
    fun screenshotApprovedWhenConsentMatches() {
        val ratings = mapOf("screen1" to mapOf(1 to RadioGroupValue(1, "approval", 1)))
        val packages = mapOf("screen1" to "package1")
        val rules = mapOf(
            "package1" to listOf(
                QuestionnaireRule(
                    "screenshotApproval",
                    ConditionGroup(
                        LogicalOperator.AND,
                        listOf(Condition("approval", Comparator.EQUALS, JsonPrimitive("1")))
                    ),
                    listOf(ApproveScreenshotUpload)
                )
            )
        )

        val result = approvedScreenshots(ratings, packages, rules)

        assertEquals(
            setOf("screen1"),
            result
        )
    }

    @Test
    fun screenshotNotApprovedWhenUnansweredEqualsMatches() {
        val ratings = mapOf("screen1" to mapOf(1 to RadioGroupValue(1, "approval", -1)))
        val packages = mapOf("screen1" to "package1")
        val rules = mapOf(
            "package1" to listOf(
                QuestionnaireRule(
                    "screenshotApproval",
                    ConditionGroup(
                        LogicalOperator.AND,
                        listOf(Condition("approval", Comparator.EQUALS, JsonPrimitive("1")))
                    ),
                    listOf(ApproveScreenshotUpload)
                )
            )
        )

        val result = approvedScreenshots(ratings, packages, rules)

        assertEquals(
            emptySet<String>(),
            result
        )
    }

    @Test
    fun screenshotNotApprovedWhenUnansweredNotEqualsMatches() {
        val ratings = mapOf("screen1" to mapOf(1 to RadioGroupValue(1, "approval", -1)))
        val packages = mapOf("screen1" to "package1")
        val rules = mapOf(
            "package1" to listOf(
                QuestionnaireRule(
                    "screenshotApproval",
                    ConditionGroup(
                        LogicalOperator.AND,
                        listOf(
                            Condition("approval", Comparator.ANSWERED, JsonNull),
                            Condition("approval", Comparator.NOT_EQUALS, JsonPrimitive("0"))
                        )
                    ),
                    listOf(ApproveScreenshotUpload)
                )
            )
        )

        val result = approvedScreenshots(ratings, packages, rules)

        assertEquals(
            emptySet<String>(),
            result
        )
    }

    @Test
    fun screenshotNotApprovedWhenNoRuleConditions() {
        val ratings = mapOf("screen1" to mapOf(1 to RadioGroupValue(1, "approval", -1)))
        val packages = mapOf("screen1" to "package1")
        val rules = mapOf(
            "package1" to listOf(
                QuestionnaireRule(
                    "screenshotApproval",
                    ConditionGroup(
                        LogicalOperator.AND,
                        emptyList()
                    ),
                    listOf(ApproveScreenshotUpload)
                )
            )
        )

        val result = approvedScreenshots(ratings, packages, rules)

        assertEquals(
            emptySet<String>(),
            result
        )
    }

    @Test
    fun screenshotNotApprovedWhenNotRated() {
        val ratings = mapOf("screen1" to emptyMap<Int, ElementValue>())
        val packages = mapOf("screen1" to "package1")
        val rules = mapOf(
            "package1" to listOf(
                QuestionnaireRule(
                    "screenshotApproval",
                    ConditionGroup(
                        LogicalOperator.AND,
                        listOf(
                            Condition("approval", Comparator.ANSWERED, JsonNull),
                            Condition("approval", Comparator.NOT_EQUALS, JsonPrimitive("0"))
                        )),
                    listOf(ApproveScreenshotUpload)
                )
            )
        )

        val result = approvedScreenshots(ratings, packages, rules)

        assertEquals(
            emptySet<String>(),
            result
        )
    }

    /** every approval rule needs its own condition, another rule's condition doesn't make up for it */
    @Test
    fun screenshotNotApprovedByRuleWithoutConditionsNextToAnother() {
        fun approvalRule(name: String, vararg conditions: Condition) = QuestionnaireRule(
            name, ConditionGroup(LogicalOperator.AND, conditions.toList()), listOf(ApproveScreenshotUpload)
        )
        val rules = mapOf(
            "package1" to listOf(
                approvalRule("withCondition", Condition("approval", Comparator.EQUALS, JsonPrimitive("1"))),
                approvalRule("withoutConditions")
            )
        )
        val packages = mapOf("no" to "package1", "unrated" to "package1", "yes" to "package1")
        val ratings = mapOf(
            "no" to mapOf(1 to RadioGroupValue(1, "approval", 0)),
            "unrated" to emptyMap(),
            "yes" to mapOf(1 to RadioGroupValue(1, "approval", 1))
        )

        assertEquals(setOf("yes"), approvedScreenshots(ratings, packages, rules))
    }
}
