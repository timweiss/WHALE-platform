package de.mimuc.senseeverything.service.sampling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import de.mimuc.senseeverything.service.LogService

class ResumeSamplingReceiver : BroadcastReceiver() {
    val TAG = "ResumeSamplingReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Log.e(TAG, "Context is null")
            return
        }

        // hack: BroadcastReceivers cannot bind to services, but we can use the application context instead
        // see https://stackoverflow.com/a/34752888/7735299
        val applicationContext = context.applicationContext

        val serviceIntent = Intent(applicationContext, LogService::class.java)
        applicationContext.bindService(serviceIntent, object : ServiceConnection {
            private var serviceMessenger: Messenger? = null

            override fun onServiceConnected(name: android.content.ComponentName?, binder: IBinder?) {
                serviceMessenger = binder?.let { Messenger(it) }
                if (serviceMessenger == null) {
                    Log.e(TAG, "Messenger is null")
                    applicationContext.unbindService(this)
                    return
                }

                // Create and send the message to the service
                val msg = Message.obtain(null, LogService.LISTEN_LOCK_UNLOCK_AND_PERIODIC, 0, 0)
                try {
                    serviceMessenger?.send(msg)
                    Log.d(TAG, "Message sent to service")
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error sending message to service", e)
                } finally {
                    // Unbind from the service to prevent leaks
                    applicationContext.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                Log.e(TAG, "Service disconnected unexpectedly")
                serviceMessenger = null
            }
        }, Context.BIND_AUTO_CREATE)
    }
}