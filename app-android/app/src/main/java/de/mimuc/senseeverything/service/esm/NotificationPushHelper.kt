package de.mimuc.senseeverything.service.esm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.MainActivity
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.floatingWidget.NotificationTriggerFloatingWidgetService
import java.util.UUID
import kotlin.time.Duration

class NotificationPushHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun sendReminderNotification(
        triggerId: Int,
        pendingQuestionnaireId: UUID?,
        title: String?,
        questionnaireName: String? = "",
        timeout: Duration? = null
    ) {
        val intent = Intent(context, QuestionnaireActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(QuestionnaireActivity.Companion.INTENT_TRIGGER_ID, triggerId)
            putExtra(
                QuestionnaireActivity.Companion.INTENT_PENDING_QUESTIONNAIRE_ID,
                pendingQuestionnaireId?.toString()
            )
        }

        val notification =
            buildActivityNotification(title ?: "It's time for $questionnaireName", intent, timeout)

        notificationManager.notify(triggerId, notification)
    }

    fun pushNotificationTrigger(
        notificationTrigger: NotificationTrigger,
        timeout: Duration? = null
    ) {
        try {
            val trigger: EMAFloatingWidgetNotificationTrigger =
                notificationTrigger.triggerJson.let {
                    fullQuestionnaireJson.decodeFromString<EMAFloatingWidgetNotificationTrigger>(
                        it
                    )
                }

            // start the floating widget service when notification is tapped
            val intent = Intent(context, NotificationTriggerFloatingWidgetService::class.java)
            val notification =
                buildServiceNotification(trigger.configuration.notificationText, intent, timeout)

            notificationManager.notify(notificationTrigger.uid.hashCode(), notification)
        } catch (e: Exception) {
            WHALELog.e(
                NotificationPushHelper::class.simpleName!!,
                e.message ?: "Error pushing notification trigger"
            )
        }
    }

    private fun buildActivityNotification(
        title: String,
        intent: Intent,
        timeout: Duration? = null
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return buildNotificationBase(title, pendingIntent, timeout)
    }

    private fun buildServiceNotification(
        title: String,
        intent: Intent,
        timeout: Duration? = null
    ): Notification {
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return buildNotificationBase(title, pendingIntent, timeout)
    }

    private fun buildNotificationBase(
        title: String,
        pendingIntent: PendingIntent,
        timeout: Duration? = null,
        bigText: String? = null
    ): Notification {
        val builder = NotificationCompat.Builder(context, "SEChannel")
            .setContentText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setSmallIcon(R.drawable.notification_silhouette_q)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_launcher_whale_foreground
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText ?: title)
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (timeout != null && timeout != Duration.INFINITE) {
            builder.setTimeoutAfter(timeout.inWholeMilliseconds)
        }

        return builder.build()
    }

    fun sendOldDataReminderNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1015,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = buildNotificationBase(context.getString(R.string.sensor_upload_stale_notification_title), pendingIntent, bigText = context.getString(R.string.sensor_upload_stale_notification_message))

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(1015, notification)
    }

    private fun Context.bitmapFromResource(
        @DrawableRes resId: Int
    ) = BitmapFactory.decodeResource(
        resources,
        resId
    )
}