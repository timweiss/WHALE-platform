# SenseEverything (for Social Interaction Sensing)
Social Sensing environment.

## Tasks
- [ ] Use WorkManager to schedule backend pushes
- [ ] Implement API in app (pushing data, showing study name)
- [ ] Add radio frequency (maybe Bluetooth and WiFi?) sensor
- [ ] Add notification sensor
- [ ] Allow to sample on lock/unlock and continuously in a set interval (maybe we can define some sensors to run in a periodic interval and others to sample on interaction)
- [x] Detect if a conversation happens (see openSMILE)
- [x] Revamp AppSensor so it actually retrieves the currently open app (probably with AccessibilityService)
- [x] Sample whenever the device gets unlocked
- [x] Create simple backend that receives pushed data (audio and other sample readings)
- [x] Add (crude) study enrolment interface
- [x] Integrate AudioSampleService to use default content pipeline

## Components
* [Android App](app-android): The app that records sensor data. Needs to be installed on participant's devices
* [Backend](backend): The backend that allows for data storage. Deployed on a server.

> [!NOTE]  
> See the component's respective READMEs for more information.

### Interaction Bubble
We want to track if an interaction is happening and relying on sensors alone would not be sufficient. We use the following approach:
- a sticky and unobtrusive widget flows around the screen
- when no interaction was marked, we ask the user on unlock, if they are in an interaction
- on each unlock, we ask if this interaction is still ongoing
- if they mark the interaction as ended, an experience sampling form is triggered

```mermaid
---
title: Interaction Bubble State
---
stateDiagram-v2
    state "No Interaction" as NoInteraction
    state "In Interaction" as InInteraction
    state "Ask on Unlock" as AskOnUnlock
    state "Ask if Ongoing" as AskIfOngoing
    state "Trigger ESM Form" as TriggerForm
    
    [*] --> NoInteraction
    NoInteraction --> AskOnUnlock: Unlock
    AskOnUnlock --> InInteraction: Yes Interaction
    AskOnUnlock --> NoInteraction: No interaction
    
    InInteraction --> AskIfOngoing: Unlock
    AskIfOngoing --> InInteraction: Still ongoing
    AskIfOngoing --> TriggerForm: Ended
    TriggerForm --> NoInteraction: Form completed
```

### Experience Sampling
We want to trigger an experience sampling form when specific events happen:
- user marks a conversation start
- user marks a conversation end
- possibly in a specific interval

A sampling could include the following:
- content separated in steps (title, position)
  - displayed: (rich) text
  - input: text, radio, checkbox, Likert scale, slider, photo and
    video, audio, affect grid, time

```mermaid
---
title: ESM Model
---
erDiagram
    Study
    Questionnaire {
        bool enabled
        string name
    }
    Trigger {
        string eventType
        bool enabled
    }
    Element {
        string type
        number step
        number position
        object configuration
    }
    Questionnaire ||--|{ Trigger : "triggers"
    Questionnaire ||--o{ Study : "belongs to"
    Element ||--o{ Questionnaire : "belongs to"
```

## Acknowledgements & References
Based on the [SenseEverything](https://github.com/mimuc/SenseEverything) app.

* Weber, D., & Mayer, S. (2014). LogEverything. GitHub Repository. Retrieved from https://github.com/hcilab-org/LogEverything/