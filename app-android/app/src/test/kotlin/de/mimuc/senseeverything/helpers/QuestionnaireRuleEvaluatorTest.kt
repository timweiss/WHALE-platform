package de.mimuc.senseeverything.helpers

import de.mimuc.senseeverything.api.model.ButtonGroupValue
import de.mimuc.senseeverything.api.model.ema.Comparator
import de.mimuc.senseeverything.api.model.ema.Condition
import de.mimuc.senseeverything.api.model.ema.ConditionGroup
import de.mimuc.senseeverything.api.model.ema.LogicalOperator
import de.mimuc.senseeverything.api.model.ema.OpenQuestionnaire
import de.mimuc.senseeverything.api.model.ema.QuestionnaireRule
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
}