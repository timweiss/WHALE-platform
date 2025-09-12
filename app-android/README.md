# Android App

## ESM Components

### NotificationTrigger
The NotificationTrigger is used to conditionally display the interaction bubble (floating widget) based on study phase and previous EMA answers.
To display any questionnaire in the interaction bubble on unlock or through push notifications, the NotificationTriggers are planned on enrolment, depending on the study phase.

A questionnaire trigger, the `EMAFloatingWidgetNotificationTrigger`, is used to create NotificationTriggers for the respective questionnaire.
* defines if the questionnaire should only be shown after unlocking the phone, or also to push a notification proactively
* defines the phase in which the NotificationTrigger should be created
* defines the time intervals in which any NotificationTrigger should be created
* it also defines if the NotificationTrigger is planned or only created conditionally, e.g. after answering a previous questionnaire with a rule prompting to create a new NotificationTrigger

To decide if and which NotificationTrigger is shown on unlock (or through a push notification), the widget (or push BroadcastReceiver) checks if either
* the latest NotificationTrigger of the current time interval (called Time Bucket) is unanswered
* the last time interval contains an unanswered **wave-breaking** NotificationTrigger, which makes the time interval "virtually" expand to the current one
In both cases, the floating widget will show the questionnaire defined in the questionnaire trigger of the NotificationTrigger. If it is configured to be pushed, an additional push notification is created.

### Questionnaire Rules
todo

### String Interpolation
The text view components support string interpolation to include dynamic content in the questionnaire. Currently, the dynamic content is limited to the timestamps of NotificationTriggers.
It can be used in any `text_view` ESM element and the questionnaire shown needs to have a NotificationTrigger associated with its PendingNotification.
The format for the string interpolation for NotificationTrigger timestamps is

| Format                     | Description                                | Example |
|----------------------------|--------------------------------------------|---------|
| `{{triggerName_pushed}}`   | Time when NotificationTrigger was pushed   | 11:25   |
| `{{triggerName_answered}}` | Time when NotificationTrigger was answered | 11:30   |

where `triggerName` is the name of the NotificationTrigger, so for example the text of a TextViewElement could be `What did you do on {{Trigger1_pushed}}?`, which will replace to `What did you do on 11:25?` when the questionnaire is shown.

### PendingNotification
todo

## Lifecycle
[LogService](app/src/main/java/de/mimuc/senseeverything/service/LogService.java) is responsible for starting the [Sensors](app/src/main/java/de/mimuc/senseeverything/sensor).

### Sampling methodology
Currently, sampling is manually started. It is managed by [SamplingManager](app/src/main/java/de/mimuc/senseeverything/service/SamplingManager.java). We have different modes of sampling, depending on what a study wants to investigate:
* Continuous sampling, repeatedly for a specific duration
  * The LogService is started once and is always active as a foreground service. The SamplingManager can initialize LogService for different sampling 'strategies'.
    * The default strategy is [OnUnlockAndPeriodicSamplingStrategy](app/src/main/java/de/mimuc/senseeverything/service/sampling/OnUnlockAndPeriodicSamplingStrategy.java), which covers the following
      * Sampling on unlock
      * Periodic sampling (every **5 minutes** for **1 minute**)
      * Event-based (*continuous*) sampling, e.g. for notifications
    * This "wakes up" each enabled sensor through their `start` method
    * Sensors are only stopped through `stop` when the entire sampling process is stopped (through the UI)

#### Components
* [AudioSampleService](app/src/main/java/de/mimuc/senseeverything/sensor/implementation/AudioSampleSensor.java): Will record audio until it is stopped again
* [SingletonSensorList](app/src/main/java/de/mimuc/senseeverything/sensor/SingletonSensorList.java): For long-running tasks, we want to reuse the existing sensor instances, and clean them up manually

## Known Issues
* AppSensor does not sample the foreground activity (function was deprecated with Android Lollipop)
  * **Current Solution** We're using [AccessibilitySensor](app/src/main/java/de/mimuc/senseeverything/sensor/implementation/AccessibilitySensor.java) as it yields useful information about app use, which can be post-processed to infer opened apps and interactions
  * *Alternative*: We could also use the [UsageStats](https://developer.android.com/reference/android/app/usage/UsageStats) if [`getLastTimeUsed()`](https://developer.android.com/reference/android/app/usage/UsageStats#getLastTimeUsed()) is reliable enough
* Starting with Android 15, we can [no longer run a foreground service](https://developer.android.com/about/versions/15/changes/foreground-service-types#microphone) that'll record from the microphone
  * **Needs Clarification**: Instead, we could maybe ask the participants to open the app so the microphone becomes active again? 