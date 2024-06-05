# SenseEverything (for Social Interaction Sensing)
Social Sensing environment.

## Tasks
- [ ] Use WorkManager to schedule backend pushes
- [ ] Implement API in app (pushing data, showing study name)
- [ ] Revamp AppSensor so it actually retrieves the currently open app (probably with AccessibilityService)
- [ ] Add radio frequency (maybe Bluetooth and WiFi?) sensor
- [ ] Add notification sensor
- [ ] Detect if a conversation happens (see openSMILE)
- [ ] Allow to sample on lock/unlock and continuously in a set interval (maybe we can define some sensors to run in a periodic interval and others to sample on interaction)
- [x] Sample whenever the device gets unlocked
- [x] Create simple backend that receives pushed data (audio and other sample readings)
- [x] Add (crude) study enrolment interface
- [x] Integrate AudioSampleService to use default content pipeline

## Components
* [Android App](app-android): The app that records sensor data. Needs to be installed on participant's devices
* [Backend](backend): The backend that allows for data storage. Deployed on a server.

> [!NOTE]  
> See the component's respective READMEs for more information.

## Acknowledgements & References
Based on the [SenseEverything](https://github.com/mimuc/SenseEverything) app.

* Weber, D., & Mayer, S. (2014). LogEverything. GitHub Repository. Retrieved from https://github.com/hcilab-org/LogEverything/