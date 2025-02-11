package de.mimuc.senseeverything.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.model.InteractionWidgetDisplayStrategy
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getCurrentStudyPhase
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.getCurrentTimeBucket
import de.mimuc.senseeverything.helpers.shouldDisplayFromRandomDiceThrow
import de.mimuc.senseeverything.sensor.implementation.InteractionLogSensor
import de.mimuc.senseeverything.service.esm.SamplingEventReceiver.Companion.sendBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InteractionFloatingWidgetService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    internal enum class InteractionLogType {
        Asked,
        Start,
        End,
        Confirm,
        ConfirmAnotherInteraction,
        ConfirmSameInteraction,
        NoInteraction,
        NotAskedAlreadyDisplayedInBucket,
        NotAskedNotDisplayedInBucket
    }

    private var windowManager: WindowManager? = null
    private var floatingWidget: View? = null
    private var questionTextView: TextView? = null
    private var questionText = ""

    private var askedAnotherInteraction = false

    private val TAG = "InteractionFloatingWidgetService"

    private var logServiceMessenger: Messenger? = null

    @Inject
    lateinit var dataStore: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // bind to LogService
        val intent = Intent(this, LogService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        questionText = resources.getString(R.string.are_you_interacting)

        scope.launch {
            val studyPhase = dataStore.getCurrentStudyPhase()
            val isInInteraction = dataStore.inInteractionFlow.first()

            if (studyPhase == null) {
                return@launch
            }

            // set the question text depending on interaction state
            if (isInInteraction) {
                questionText = resources.getString(R.string.still_interacting)
                renderWidget()

                Log.d("FloatingWidget", "already in interaction, rendering widget")
                // no need to check strategy because we are already in interaction
                return@launch
            } else {
                questionText = resources.getString(R.string.are_you_interacting)
            }

            val displayStrategy = studyPhase?.interactionWidgetStrategy
                ?: InteractionWidgetDisplayStrategy.DEFAULT
            when (displayStrategy) {
                InteractionWidgetDisplayStrategy.DEFAULT -> {
                    renderWidget()
                    Log.d("FloatingWidget", "default display strategy, rendering widget")
                }

                InteractionWidgetDisplayStrategy.BUCKETED -> {
                    val timeBuckets = dataStore.interactionWidgetTimeBucketFlow.first()
                    val currentBucket = getCurrentTimeBucket()

                    // not yet displayed in this time bucket
                    if (!timeBuckets.contains(currentBucket)) {
                        if (shouldDisplayFromRandomDiceThrow()) {
                            renderWidget()
                            timeBuckets[currentBucket] = true
                            dataStore.setInteractionWidgetTimeBucket(timeBuckets)
                            Log.d("FloatingWidget", "now displayed in this time bucket")
                        } else {
                            Log.d("FloatingWidget", "not displayed in this time bucket")
                        }
                    } else {
                        Log.d("FloatingWidget", "already displayed in this time bucket")
                    }
                }
            }
        }

    }

    private fun renderWidget() {
        // Inflate the floating widget layout
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null)
        this.questionTextView = floatingWidget!!.findViewById(R.id.interaction_question)
        questionTextView!!.text = questionText

        val yesButton = floatingWidget!!.findViewById<Button>(R.id.yes_button)
        val noButton = floatingWidget!!.findViewById<Button>(R.id.no_button)

        yesButton.setOnClickListener { answerYes() }

        noButton.setOnClickListener { answerNo() }

        // Set up layout parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // For Android O and above
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // put it in the top right corner, a bit lower than the status bar
        params.gravity = Gravity.TOP or Gravity.END
        params.verticalMargin = 0.075f

        // Add the view to the window
        windowManager?.addView(floatingWidget, params)
        Log.d("FloatingWidget", "Floating widget added to screen")

        // Handle the widget's movement and interactions
        floatingWidget?.setOnTouchListener(object : OnTouchListener {
            private var lastAction = 0
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastAction = event.action
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            // Handle click event
                        }
                        lastAction = event.action
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager!!.updateViewLayout(floatingWidget, params)
                        lastAction = event.action
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun answerYes() {
        Log.d("FloatingWidget", "Yes button clicked")

        dataStore.getInInteractionSync { inInteraction: Boolean ->
            if (inInteraction) {
                if (askedAnotherInteraction) {
                    logInteractionMessage(InteractionLogType.ConfirmSameInteraction)
                    floatingWidget!!.visibility = View.GONE
                    askedAnotherInteraction = false
                } else {
                    questionTextView!!.setText(R.string.is_same_interaction)
                    askedAnotherInteraction = true
                }
            } else {
                floatingWidget!!.visibility = View.GONE
                logInteractionMessage(InteractionLogType.Start)
            }
            dataStore.setInInteractionSync(true)
        }
    }

    private fun answerNo() {
        Log.d("FloatingWidget", "No button clicked")
        floatingWidget!!.visibility = View.GONE
        dataStore.getInInteractionSync { inInteraction: Boolean ->
            if (inInteraction) {
                if (askedAnotherInteraction) {
                    askedAnotherInteraction = false
                    logInteractionMessage(InteractionLogType.ConfirmAnotherInteraction)
                } else {
                    logInteractionMessage(InteractionLogType.End)
                    SEApplicationController.getInstance().esmHandler.initializeTriggers(dataStore)

                    sendBroadcast(this, "interactionEnd")
                }
            } else {
                logInteractionMessage(InteractionLogType.NoInteraction)
            }
            dataStore.setInInteractionSync(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingWidget != null) windowManager!!.removeView(floatingWidget)
        unbindService(connection)
        job.cancel()
    }

    private fun logInteractionMessage(type: InteractionLogType) {
        when (type) {
            InteractionLogType.Asked -> sendDataToLogService("asked")
            InteractionLogType.Start -> sendDataToLogService("start")
            InteractionLogType.End -> sendDataToLogService("end")
            InteractionLogType.Confirm -> sendDataToLogService("confirm")
            InteractionLogType.ConfirmAnotherInteraction -> sendDataToLogService("confirmAnotherInteraction")
            InteractionLogType.ConfirmSameInteraction -> sendDataToLogService("confirmSameInteraction")
            InteractionLogType.NoInteraction -> sendDataToLogService("noInteraction")
            InteractionLogType.NotAskedAlreadyDisplayedInBucket -> sendDataToLogService("notAskedAlreadyDisplayedInBucket")
            InteractionLogType.NotAskedNotDisplayedInBucket -> sendDataToLogService("notAskedNotDisplayedInBucket")
        }
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            logServiceMessenger = Messenger(service)
            logInteractionMessage(InteractionLogType.Asked) // needs to be logged here because otherwise we have a race condition
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logServiceMessenger = null
        }
    }

    private fun sendDataToLogService(message: String) {
        if (logServiceMessenger != null) {
            val msg = Message.obtain(null, LogService.SEND_SENSOR_LOG_DATA)
            val bundle = Bundle()
            bundle.putString("sensorData", message)
            bundle.putString("sensorName", InteractionLogSensor::class.java.name)
            msg.data = bundle

            try {
                logServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to send message to LogService", e)
            }
        }
    }
}
