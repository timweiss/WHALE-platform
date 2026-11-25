package de.mimuc.senseeverything.helpers

import de.mimuc.senseeverything.api.model.ButtonGroupValue
import de.mimuc.senseeverything.api.model.CircumplexValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.Comparator
import de.mimuc.senseeverything.api.model.ema.Condition
import de.mimuc.senseeverything.api.model.ema.ConditionGroup
import de.mimuc.senseeverything.api.model.ema.LogicalOperator
import de.mimuc.senseeverything.api.model.ema.OpenQuestionnaire
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import de.mimuc.senseeverything.api.model.ema.QuestionnaireRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class QuestionnaireRuleEvaluatorTest {
    @Test
    fun testSingleRuleEvaluatedTrue() {
        val rules = listOf(
            QuestionnaireRule(
            "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.EQUALS,
                            JsonPrimitive("Yes")
                        )
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "Yes")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.containsKey("Test Rule"))
        assert(actions["Test Rule"]?.size == 1)
    }

    @Test
    fun testSingleRuleEvaluatedFalse() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.NOT_EQUALS,
                            JsonPrimitive("Yes")
                        )
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "Yes")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.isEmpty())
    }

    @Test
    fun testMultipleConditionsAndOperator() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.EQUALS,
                            JsonPrimitive("Yes")
                        ),
                        Condition(
                            "answer2",
                            Comparator.NOT_EQUALS,
                            JsonPrimitive("No")
                        )
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "Yes"),
            2 to ButtonGroupValue(2, "answer2", "Maybe")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.containsKey("Test Rule"))
        assert(actions["Test Rule"]?.size == 1)
    }

    @Test
    fun testMultipleConditionsOrOperator() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.OR,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.EQUALS,
                            JsonPrimitive("Yes")
                        ),
                        Condition(
                            "answer2",
                            Comparator.EQUALS,
                            JsonPrimitive("Yes")
                        )
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "No"),
            2 to ButtonGroupValue(2, "answer2", "Yes")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.containsKey("Test Rule"))
        assert(actions["Test Rule"]?.size == 1)
    }

    @Test
    fun testSingleRuleEvaluatesFalseWhenElementValueIsNull() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.NOT_EQUALS,
                            JsonPrimitive("Yes")
                        )
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = emptyMap<Int, ButtonGroupValue>()

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.isEmpty())
    }

    @Test
    fun testSingleRuleEvaluatesFalseWhenElementValueIsNotAnswered() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.NOT_EQUALS,
                            JsonPrimitive("Yes")
                        ),
                        Condition("answer1", Comparator.ANSWERED, JsonNull)
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.isEmpty())
    }

    @Test
    fun testSingleRuleEvaluatesTrueWhenElementValueIsAnsweredAndAnsweredIsRequired() {
        val rules = listOf(
            QuestionnaireRule(
                "Test Rule",
                ConditionGroup(
                    LogicalOperator.AND,
                    listOf(
                        Condition(
                            "answer1",
                            Comparator.NOT_EQUALS,
                            JsonPrimitive("Yes")
                        ),
                        Condition("answer1", Comparator.ANSWERED, JsonNull)
                    )
                ),
                listOf(
                    OpenQuestionnaire( 1)
                )
            )
        )

        val elementValues = mapOf(
            1 to ButtonGroupValue(1, "answer1", "No")
        )

        val evaluator = QuestionnaireRuleEvaluator(rules)
        val actions = evaluator.evaluate(elementValues)

        assert(actions.containsKey("Test Rule"))
    }

    /** null as a string value must not count as "not equal", with or without ANSWERED */
    @Test
    fun testNotEqualsNeverMatchesTypesWithoutStringValue() {
        val values = listOf(
            CircumplexValue(1, "answer1", 0.5 to 0.5),
            ElementValue(1, "answer1", QuestionnaireElementType.MALFORMED),
            ElementValue(1, "answer1", QuestionnaireElementType.TEXT_VIEW)
        )
        val notEquals = Condition("answer1", Comparator.NOT_EQUALS, JsonPrimitive("Yes"))
        val answered = Condition("answer1", Comparator.ANSWERED)

        for (conditions in listOf(listOf(notEquals), listOf(notEquals, answered))) {
            val rule = QuestionnaireRule(
                "Test Rule", ConditionGroup(LogicalOperator.AND, conditions), listOf(OpenQuestionnaire(1))
            )
            for (value in values) {
                assert(QuestionnaireRuleEvaluator(listOf(rule)).evaluate(mapOf(1 to value)).isEmpty()) {
                    "${value.elementType} matched $conditions"
                }
            }
        }
    }

    @Test
    fun testComparisonWithoutExpectedValueNeverMatches() {
        for (comparator in listOf(Comparator.EQUALS, Comparator.NOT_EQUALS)) {
            val rule = QuestionnaireRule(
                "Test Rule",
                ConditionGroup(LogicalOperator.AND, listOf(Condition("answer1", comparator))),
                listOf(OpenQuestionnaire(1))
            )
            for (answer in listOf("", "No", "null")) {
                val values = mapOf(1 to ButtonGroupValue(1, "answer1", answer))
                assert(QuestionnaireRuleEvaluator(listOf(rule)).evaluate(values).isEmpty()) { "$comparator matched '$answer'" }
            }
        }
    }
}
