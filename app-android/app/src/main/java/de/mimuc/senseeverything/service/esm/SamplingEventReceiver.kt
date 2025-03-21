package de.mimuc.senseeverything.service.esm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.service.SEApplicationController
import javax.inject.Inject

@AndroidEntryPoint
class SamplingEventReceiver : BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        if (context == null) {
            Log.e("SamplingEventReceiver", "Context is null")
            return@goAsync
        }


        val eventName = intent?.getStringExtra("eventName")
        if (eventName == null) {
            Log.e("SamplingEventReceiver", "Event name is null")
            return@goAsync
        }

        // handle Event
        SEApplicationController.getInstance().esmHandler.handleEvent(
            eventName,
            context.applicationContext,
            dataStoreManager,
            database
        )
    }

    companion object {
        fun sendBroadcast(context: Context, eventName: String) {
            val intent = Intent(context.applicationContext, SamplingEventReceiver::class.java)
            intent.apply {
                putExtra("eventName", eventName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                101,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            pendingIntent.send()
        }
    }
}