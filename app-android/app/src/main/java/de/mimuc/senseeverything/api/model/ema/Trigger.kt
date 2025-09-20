package de.mimuc.senseeverything.api.model.ema

import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
enum class PeriodicQuestionnaireTriggerInterval {
    @SerialName("daily") DAILY,
    @SerialName("weekly") WEEKLY,
    @SerialName("monthly") MONTHLY
}

@Serializable
sealed class QuestionnaireTrigger {
    abstract val id: Int
    abstract val questionnaireId: Int
    abstract val validDuration: Long
    abstract val enabled: Boolean
    
    // Computed property that derives type from the actual class
    val type: String
        get() = when (this) {
            is EventQuestionnaireTrigger -> "event"
            is PeriodicQuestionnaireTrigger -> "periodic"
            is RandomEMAQuestionnaireTrigger -> "random_ema"
            is EMAFloatingWidgetNotificationTrigger -> "ema_floating_widget_notification"
            is OneTimeQuestionnaireTrigger -> "one_time"
            is UnknownQuestionnaireTrigger -> this.originalType // For unknown types, use the actual type from the data
        }
}

@Serializable
@SerialName("event")
data class EventQuestionnaireTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    override val validDuration: Long,
    override val enabled: Boolean,
    val configuration: EventTriggerConfiguration
) : QuestionnaireTrigger()

@Serializable
data class EventTriggerConfiguration(
    val eventName: String,
    val notificationText: String,
    val modality: EventTriggerModality
)

enum class EventTriggerModality {
    Open,
    Push
}

@Serializable
@SerialName("periodic")
data class PeriodicQuestionnaireTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    override val validDuration: Long,
    override val enabled: Boolean,
    val configuration: PeriodicTriggerConfiguration
) : QuestionnaireTrigger()

@Serializable
data class PeriodicTriggerConfiguration(
    val interval: PeriodicQuestionnaireTriggerInterval,
    val time: String
)

@Serializable
@SerialName("random_ema")
data class RandomEMAQuestionnaireTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    override val validDuration: Long,
    override val enabled: Boolean,
    val configuration: RandomEMATriggerConfiguration
) : QuestionnaireTrigger()

@Serializable
data class RandomEMATriggerConfiguration(
    val distanceMinutes: Int,
    val randomToleranceMinutes: Int,
    val delayMinutes: Int,
    val timeBucket: String,
    val phaseName: String
)

@Serializable
@SerialName("ema_floating_widget_notification")
data class EMAFloatingWidgetNotificationTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    override val validDuration: Long,
    override val enabled: Boolean,
    val configuration: EMAFloatingWidgetTriggerConfiguration
) : QuestionnaireTrigger()

@Serializable
data class EMAFloatingWidgetTriggerConfiguration(
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
    val timeoutNotificationTriggerId: Int? = null
)

@Serializable
@SerialName("one_time")
data class OneTimeQuestionnaireTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    override val validDuration: Long,
    override val enabled: Boolean,
    val configuration: OneTimeTriggerConfiguration
) : QuestionnaireTrigger()

@Serializable
data class OneTimeTriggerConfiguration(
    val studyDay: Int,
    val time: String,
    val randomToleranceMinutes: Int,
    val notificationText: String
)

@Serializable
@SerialName("unknown")
data class UnknownQuestionnaireTrigger(
    override val id: Int,
    override val questionnaireId: Int,
    val originalType: String,
    override val validDuration: Long,
    override val enabled: Boolean = false,
    val configuration: UnknownTriggerConfiguration = UnknownTriggerConfiguration()
) : QuestionnaireTrigger()

@Serializable
data class UnknownTriggerConfiguration(
    val error: String = "Unknown trigger type"
)

// Serializers module for polymorphic serialization
val questionnaireTriggerModule = SerializersModule {
    polymorphic(QuestionnaireTrigger::class) {
        subclass(EventQuestionnaireTrigger::class)
        subclass(PeriodicQuestionnaireTrigger::class)
        subclass(RandomEMAQuestionnaireTrigger::class)
        subclass(EMAFloatingWidgetNotificationTrigger::class)
        subclass(OneTimeQuestionnaireTrigger::class)
        subclass(UnknownQuestionnaireTrigger::class)
    }
}
