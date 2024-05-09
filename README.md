# SenseEverything (for Social Interaction Sensing)
Simple logging app for Android.

## Tasks
- [ ] Integrate AudioSampleService to use default content pipeline
- [ ] Create simple backend that receives pushed data (audio and other sample readings)
- [ ] Use WorkManager to schedule backend pushes
- [ ] Add study enrolment interface

## Lifecycle
[LogService](app-android/app/src/main/java/de/mimuc/senseeverything/service/LogService.java) is responsible for starting the [Sensors](app-android/app/src/main/java/de/mimuc/senseeverything/sensor).

### Sampling methodology
* The LogService is activated every **60 seconds** through an AlarmService in `startLogService` (MainActivity)
    * This "wakes up" each enabled sensor through their `start` method
    * Sensors are only stopped through `stop` when the entire sampling process is stopped (through the UI)
* [AudioSampleService](app-android/app/src/main/java/de/mimuc/senseeverything/sensor/implementation/AudioSampleSensor.java): We need to record in different intervals (eg. 60 seconds for every 10 minutes), so it does not "reactivate" through the default sampling method
* [SingletonSensorList](app-android/app/src/main/java/de/mimuc/senseeverything/sensor/SingletonSensorList.java): For long-running tasks, we want to reuse the existing sensor instances, and clean them up manually

## Things I'm not sure about yet
* Why does [ForegroundService](app-android/app/src/main/java/de/mimuc/senseeverything/service/ForegroundService.java) require a wakelock? I don't think it's necessary for it to do so (see [Foreground services](https://developer.android.com/develop/background-work/services/foreground-services))