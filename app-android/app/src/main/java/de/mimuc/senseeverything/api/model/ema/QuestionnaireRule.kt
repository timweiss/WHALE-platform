package de.mimuc.senseeverything.api.model.ema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
data class QuestionnaireRule(
    val name: String,
    val conditions: ConditionGroup,
    val actions: List<Action>
)

@Serializable
data class ConditionGroup(
    val operator: LogicalOperator = LogicalOperator.AND,
    val conditions: List<Condition>
)

@Serializable
enum class LogicalOperator {
    @SerialName("and") AND,
    @SerialName("or") OR
}

@Serializable
data class Condition(
    val fieldName: String,
    val comparator: Comparator,
    val expectedValue: JsonElement // any JSON type
)

@Serializable
enum class Comparator {
    @SerialName("equals") EQUALS,
    @SerialName("not_equals") NOT_EQUALS
}

enum class ActionType(val type: String) {
    PUT_NOTIFICATION_TRIGGER("put_notification_trigger"),
    OPEN_QUESTIONNAIRE("open_questionnaire")
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Action {
    abstract val type: ActionType
}

@Serializable
@SerialName("put_notification_trigger")
data class PutNotificationTrigger(
    val notificationTriggerId: Int,
    override val type: ActionType = ActionType.PUT_NOTIFICATION_TRIGGER,
) : Action()

@Serializable
@SerialName("open_questionnaire")
data class OpenQuestionnaire(
    val eventQuestionnaireTriggerId: Int,
    override val type: ActionType = ActionType.OPEN_QUESTIONNAIRE,
) : Action()

val ruleJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = false
}
