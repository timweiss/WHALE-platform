package de.mimuc.senseeverything.api.model.ema

import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
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


@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Action

@Serializable
@SerialName("put_notification_trigger")
data class PutNotificationTrigger(
    val triggerId: Int
) : Action()

@Serializable
@SerialName("open_questionnaire")
data class OpenQuestionnaire(
    val eventQuestionnaireTriggerId: Int
) : Action()

@Serializable
@SerialName("update_next_notification_trigger")
data class UpdateNextNotificationTrigger(
    val triggerName: String,
    val toStatus: NotificationTriggerStatus,
    val requireSameTimeBucket: Boolean,
    val maxDistanceMinutes: Int
) : Action()

val ruleJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = false
}
