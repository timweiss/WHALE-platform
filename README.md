# WHALE
Social Sensing environment with Experience Sampling capabilities.

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
- important: **user marks an interaction ended** (triggered by `event` trigger)
- in a specific interval
  - every day at the same time (triggered by `periodic` trigger)
  - EMA: across the day, with a minimum time between samples (triggered by `random_ema` trigger)
- once, for example for the takeout questionnaire at the end of a study (triggered by `one_time` trigger)

A sampling could include the following:
- content separated in steps (title, position)
  - visual/content
    - (rich) text
  - input
    - text
    - radio
    - checkbox
    - Likert scale
    - slider
    - affect grid
    - time/duration
    - circumplex (affect grid)
    - social network
      - entry: possibility to list people in sampling
      - rating: possibility to answer questions about a person
  - link to external questionnaire (is embedded in questionnaire to instruct the participant)
  - 

```mermaid
---
title: ESM Model
---
erDiagram
    Study
    Questionnaire {
        bool enabled
        string name
        number version
    }
    Trigger {
        string eventType
        bool enabled
        object configuration
    }
    Element {
        string type
        number step
        number position
        object configuration
    }
    Questionnaire ||--|{ Trigger : "has"
    Questionnaire ||--o{ Study : "belongs to"
    Element ||--o{ Questionnaire : "belongs to"
```

After updating any element of the questionnaire, its version needs to be updated. The app will only update the questionnaire if the version is higher than the one currently cached.

### When it samples
- after unlocking: for one minute
- in the background: ever five minutes for one minute

```mermaid
stateDiagram-v2
    state "Sample after Unlock" as Sample_Unlock
    state "Sample Periodically" as Sample_Periodic
    
    [*] --> Idle
    Idle --> Sample_Unlock: After Unlock
    Idle --> Sample_Periodic: After 5 minutes since last periodic
    Sample_Unlock --> Idle: After 1 minute
    Sample_Periodic --> Sample_Unlock: After unlock while sampling
    Sample_Periodic --> Idle: After 1 minute
```

### Study Configuration
The platform's backend allows multiple studies to be configured and run in parallel, while each participant can only take part in one study at a time.
Configuration can be done via the [backend's API](backend/api.yaml) or directly on the database.

A study's configuration consists of:
- Name
- Enrolment key (which participants need to enter to join the study, unique across all studies)
- Maximum number of participants `maxEnrolments`, -1 for unlimited participants
- Duration in days `durationDays`
- Experimental Group allocation strategy `allocationStrategy`, only `sequential` is supported for now
- **Experimental Groups**
  - Name
  - Order
  - **Phases**
    - Starting day
    - Duration in days
    - Configuration parameters
      - Interaction widget display mode (in time buckets, every time, disabled)

```mermaid
---
title: Study Configuration Model
---
erDiagram
    Study {
        string name
        string enrolmentKey
        int maxEnrolments
        int durationDays
        string allocationStrategy
    }
    Enrolment {
        int id
        int studyId
        string participantId
        int studyExperimentalGroupId
        timestamp enrolledAt
    }
    ExperimentalGroup {
        int id
        int studyId
        string internalName
        int order
    }
    ExperimentalGroupPhase {
        int experimentalGroupId
        int fromDay
        int durationDays
        string interactionWidgetDisplayMode
    }
    Study ||--|{ ExperimentalGroup : "has"
    ExperimentalGroup ||--|{ ExperimentalGroupPhase : "has"
    Study ||--|{ Enrolment : "is enrolled in"
    ExperimentalGroup ||--|{ Enrolment : "belongs to"
```

## License
All changes starting with commit `cf73063b` are licensed under the [AGPL-3.0 (links gnu.org)](https://www.gnu.org/licenses/agpl-3.0.html) license.
The original work remains under the MIT license. See the [LICENSE](LICENSE.md) file for details. Please adhere to the license to ensure **transparent and open research**.

## Acknowledgements & References
Based on the [SenseEverything](https://github.com/mimuc/SenseEverything) app.

* Weber, D., & Mayer, S. (2014). LogEverything. GitHub Repository. Retrieved from https://github.com/hcilab-org/LogEverything/