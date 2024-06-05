# Android App

## Lifecycle
[LogService](app-android/app/src/main/java/de/mimuc/senseeverything/service/LogService.java) is responsible for starting the [Sensors](app-android/app/src/main/java/de/mimuc/senseeverything/sensor).

### Sampling methodology
Currently, sampling is manually started. It is managed by [SamplingManager](app/src/main/java/de/mimuc/senseeverything/service/SamplingManager.java). We have different modes of sampling, depending on what a study wants to investigate:
* Continuous sampling, repeatedly for a specific duration
  * The LogService is activated every **60 seconds** through an AlarmService in `startLogService` (MainActivity)
    * This "wakes up" each enabled sensor through their `start` method
    * Sensors are only stopped through `stop` when the entire sampling process is stopped (through the UI)
* Sampling on/after events (for now: Screen Unlock/Lock)
  * LogService is listening to Screen Unlock/Lock events and starts sampling based on that
    * for a specific duration after the event
    * until another events stops it

In the future, **both** strategies should be applied:
* we should be able to define some sensors for each strategy
* a scheduling service will set up the listeners as well as running the continuous sampling

#### Components
* [AudioSampleService](app/src/main/java/de/mimuc/senseeverything/sensor/implementation/AudioSampleSensor.java): Will record audio until it is stopped again
* [SingletonSensorList](app/src/main/java/de/mimuc/senseeverything/sensor/SingletonSensorList.java): For long-running tasks, we want to reuse the existing sensor instances, and clean them up manually

## Known Issues
* AppSensor does not sample the foreground activity (function was deprecated with Android Lollipop)
  * **Needs Clarification**: Instead, we could either use the AccessibilityService to sample open apps, but this different modelling of data flow
  * **Needs Clarification**: We could also use the [UsageStats](https://developer.android.com/reference/android/app/usage/UsageStats) if [`getLastTimeUsed()`](https://developer.android.com/reference/android/app/usage/UsageStats#getLastTimeUsed()) is reliable enough
* Starting with Android 15, we can [no longer run a foreground service](https://developer.android.com/about/versions/15/changes/foreground-service-types#microphone) that'll record from the microphone
  * **Needs Clarification**: Instead, we could maybe ask the participants to open the app so the microphone becomes active again? 