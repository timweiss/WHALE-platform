package de.mimuc.senseeverything.api.model.ema

import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import org.json.JSONObject

open class QuestionnaireTrigger(
    val id: Int,
    val questionnaireId: Int,
    val type: String,
    val validDuration: Long,
    val configuration: Any
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("questionnaireId", questionnaireId)
        json.put("type", type)
        json.put("validDuration", validDuration)
        json.put("configuration", configuration)
        return json
    }
}

class EventQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val eventName: String,
) : QuestionnaireTrigger(id, questionnaireId, "event", validUntil, configuration)

enum class PeriodicQuestionnaireTriggerInterval {
    DAILY,
    WEEKLY,
    MONTHLY
}

class PeriodicQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val interval: PeriodicQuestionnaireTriggerInterval,
    val time: String): QuestionnaireTrigger(id, questionnaireId, "periodic", validUntil, configuration)

class RandomEMAQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val distanceMinutes: Int,
    val randomToleranceMinutes: Int,
    val delayMinutes: Int,
    val timeBucket: String,
    val phaseName: String
): QuestionnaireTrigger(id, questionnaireId, "random_ema", validUntil, configuration)

class EMAFloatingWidgetNotificationTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val name: String,
    val phaseName: String,
    /** Defines the time buckets in which at least one notification should be scheduled. */
    val timeBuckets: List<String>,
    val distanceMinutes: Int,
    val delayMinutes: Int,
    val randomToleranceMinutes: Int,
    val modality: NotificationTriggerModality,
    val priority: NotificationTriggerPriority,
    val source: NotificationTriggerSource,
    val notificationText: String,
    /**
     * Defines a notification trigger which is to be pushed after the current trigger, serving as a timeout.
     * Due to the study design, there is a need to define a different questionnaire to be shown after the timeout.
     * The timeout notification trigger will use the same bucket, but adds the delayMinutes as additional delay.
     * */
    val timeoutNotificationTriggerId: Int?
): QuestionnaireTrigger(id, questionnaireId, "ema_floating_widget_notification", validUntil, configuration)

class OneTimeQuestionnaireTrigger(
    id: Int,
    questionnaireId: Int,
    validUntil: Long,
    configuration: Any,
    val studyDay: Int,
    val time: String,
    val randomToleranceMinutes: Int
): QuestionnaireTrigger(id, questionnaireId, "one_time", validUntil, configuration)

fun makeTriggerFromJson(json: JSONObject): QuestionnaireTrigger {
    val id = json.getInt("id")
    val questionnaireId = json.getInt("questionnaireId")
    val type = json.getString("type")
    val validDuration = json.getLong("validDuration")
    val configuration = json.getJSONObject("configuration")

    when (type) {
        "event" -> {
            return EventQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getString("eventName"),
            )
        }

        "periodic" -> {
            return PeriodicQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                PeriodicQuestionnaireTriggerInterval.valueOf(configuration.getString("interval").uppercase()),
                configuration.getString("time")
            )
        }

        "random_ema" -> {
            return RandomEMAQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getInt("distanceMinutes"),
                configuration.getInt("randomToleranceMinutes"),
                configuration.getInt("delayMinutes"),
                configuration.getString("timeBucket"),
                configuration.getString("phaseName")
            )
        }

        "one_time" -> {
            return OneTimeQuestionnaireTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getInt("studyDay"),
                configuration.getString("time"),
                configuration.getInt("randomToleranceMinutes")
            )
        }

        "ema_floating_widget_notification" -> {
            val timeoutTriggerId = if (configuration.has("timeoutNotificationTriggerId") && !configuration.isNull("timeoutNotificationTriggerId")) {
                configuration.getInt("timeoutNotificationTriggerId")
            } else {
                null
            }

            val timeBucketsJson = configuration.getJSONArray("timeBuckets")
            val timeBuckets = mutableListOf<String>()
            for (i in 0 until timeBucketsJson.length()) {
                timeBuckets.add(timeBucketsJson.getString(i))
            }

            return EMAFloatingWidgetNotificationTrigger(
                id,
                questionnaireId,
                validDuration,
                configuration,
                configuration.getString("name"),
                configuration.getString("phaseName"),
                timeBuckets,
                configuration.getInt("distanceMinutes"),
                configuration.getInt("delayMinutes"),
                configuration.getInt("randomToleranceMinutes"),
                NotificationTriggerModality.valueOf(configuration.getString("modality").replace(" ", "_")),
                NotificationTriggerPriority.valueOf(configuration.getString("priority").replace(" ", "_")),
                NotificationTriggerSource.valueOf(configuration.getString("source").replace(" ", "_")),
                configuration.getString("notificationText"),
                timeoutTriggerId
            )
        }

        else -> {
            return QuestionnaireTrigger(id, questionnaireId, type, validDuration, configuration)
        }
    }
}