# Android App

## Lifecycle
[LogService](/app/src/main/java/de/mimuc/senseeverything/service/LogService.java) is responsible for starting the [Sensors](app/src/main/java/de/mimuc/senseeverything/sensor).

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