package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionnaireDataRepository @Inject constructor(
    private val database: AppDatabase
) {
    fun getTextReplacementsForPendingQuestionnaire(pendingQuestionnaireId: UUID): Map<String, String> {
        val pendingQuestionnaire =
            database.pendingQuestionnaireDao().getById(pendingQuestionnaireId)
        if (pendingQuestionnaire == null) {
            return emptyMap()
        }

        // we only have text replacements for questionnaires triggered by a NotificationTrigger
        val notificationTrigger = findNextNotificationTrigger(pendingQuestionnaire)
        if (notificationTrigger == null) {
            return emptyMap()
        }

        val notificationTriggerReplacements =
            getNotificationTriggerTextReplacements(notificationTrigger)

        return notificationTriggerReplacements
    }

    private fun getNotificationTriggerTextReplacements(notificationTrigger: NotificationTrigger): Map<String, String> {
        val triggers = getTriggersForTimeBucketOnDay(
            notificationTrigger.validFrom,
            notificationTrigger.timeBucket
        )

        val map = mutableMapOf<String, String>()

        for (trigger in triggers) {
            val pushed = trigger.pushedAt
            if (pushed != null) {
                map["${trigger.name}_pushed"] = formatTriggerTimestamp(pushed)
            }

            val answered = trigger.answeredAt
            if (answered != null) {
                map["${trigger.name}_answered"] = formatTriggerTimestamp(answered)
            }
        }

        return map
    }

    /**
     * Get the NotificationTrigger of the PendingQuestionnaire. If the PendingQuestionnaire does not have one
     * but it has a source, it was created by an event and might have a NotificationTrigger in its ancestry.
     * We recursively search the ancestry until we find a NotificationTrigger or run out of ancestors.
     */
    private fun findNextNotificationTrigger(pendingQuestionnaire: PendingQuestionnaire): NotificationTrigger? {
        val notificationTriggerId = pendingQuestionnaire.notificationTriggerUid
        if (notificationTriggerId != null) {
            return database.notificationTriggerDao().getById(notificationTriggerId)
        }

        val sourcePendingNotificationId = pendingQuestionnaire.sourcePendingNotificationId
            ?: return null

        val sourcePendingQuestionnaire = database.pendingQuestionnaireDao()
            .getById(sourcePendingNotificationId)
            ?: return null

        return findNextNotificationTrigger(sourcePendingQuestionnaire)
    }

    private fun getTriggersForTimeBucketOnDay(
        timestamp: Long,
        timeBucket: String
    ): List<NotificationTrigger> {
        val startOfDay = Calendar.getInstance()
        startOfDay.timeInMillis = timestamp
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)

        val endOfDay = startOfDay.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        endOfDay.set(Calendar.MILLISECOND, 999)

        val triggersForDay = database.notificationTriggerDao()
            .getForInterval(startOfDay.timeInMillis, endOfDay.timeInMillis)
        return triggersForDay.filter { trigger ->
            trigger.timeBucket == timeBucket
        }
    }

    private fun formatTriggerTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val hours = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minutes = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$hours:$minutes"
    }
}