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
Questionnaire rules enable conditional logic within questionnaires, allowing dynamic responses based on participant answers. When a questionnaire is completed, the rules are evaluated against the provided answers, and matching rules trigger specific actions.

#### Rule Structure
Each questionnaire can have an optional `rules` field containing a list of `QuestionnaireRule` objects. Each rule consists of:

- **name**: A unique identifier for the rule
- **conditions**: A `ConditionGroup` that defines when the rule should trigger
- **actions**: A list of actions to execute when conditions are met

#### Conditions
Conditions are evaluated against questionnaire element values using:

- **fieldName**: The name of the questionnaire element to check (must match the element's `name` property)
- **comparator**: Either `equals` or `not_equals`
- **expectedValue**: The value to compare against (supports any JSON type)

Multiple conditions can be combined using logical operators:
- **and**: All conditions must be true
- **or**: At least one condition must be true

#### Actions
Two types of actions are supported:

1. **put_notification_trigger**: Creates a new notification trigger
   - `triggerId`: The ID of the trigger to create

2. **open_questionnaire**: Immediately opens another questionnaire
   - `eventQuestionnaireTriggerId`: The ID of the questionnaire trigger to open

#### Example Rule Configuration
```json
{
  "name": "Follow-up Rule",
  "conditions": {
    "operator": "and",
    "conditions": [
      {
        "fieldName": "mood_rating",
        "comparator": "equals",
        "expectedValue": "low"
      },
      {
        "fieldName": "needs_support",
        "comparator": "not_equals",
        "expectedValue": "no"
      }
    ]
  },
  "actions": [
    {
      "type": "put_notification_trigger",
      "triggerId": 123
    },
    {
      "type": "open_questionnaire",
      "eventQuestionnaireTriggerId": 456
    }
  ]
}
```

This rule would trigger if the participant rated their mood as "low" AND didn't answer "no" to needing support, then both create a notification trigger and open another questionnaire.

#### Implementation Details
Rules are evaluated in `QuestionnaireRuleEvaluator.kt:20` after questionnaire completion. The evaluator supports various element types including radio groups, checkboxes, sliders, text entries, button groups, and social network entries. If any condition references a non-existent field or the field has no value, that condition evaluates to false.

### String Interpolation
The text view components support string interpolation to include dynamic content in the questionnaire. Currently, the dynamic content is limited to the timestamps of NotificationTriggers.
It can be used in any `text_view` ESM element and the questionnaire shown needs to have a NotificationTrigger associated with its PendingNotification.
The format for the string interpolation for NotificationTrigger timestamps is

| Format                     | Description                                | Example |
|----------------------------|--------------------------------------------|---------|
| `{{triggerName_pushed}}`   | Time when NotificationTrigger was pushed   | 11:25   |
| `{{triggerName_answered}}` | Time when NotificationTrigger was answered | 11:30   |

where `triggerName` is the name of the NotificationTrigger, so for example the text of a TextViewElement could be `What did you do on {{Trigger1_pushed}}?`, which will replace to `What did you do on 11:25?` when the questionnaire is shown.

### PendingQuestionnaire
The PendingQuestionnaire is the central data model for managing questionnaire lifecycle, from creation to completion and upload. It serves as a persistent storage container that tracks questionnaire state, participant answers, and metadata throughout the entire ESM process.

#### Core Functionality
PendingQuestionnaire acts as a bridge between questionnaire definitions and participant responses, providing:

- **State Management**: Tracks questionnaire status from notification through completion
- **Answer Storage**: Persists participant responses as JSON during and after completion
- **Upload Coordination**: Manages data synchronization with the backend

#### Database Schema
The PendingQuestionnaire entity in `PendingQuestionnaire.kt:38` contains:

| Field                    | Type                            | Description                                                  |
|--------------------------|---------------------------------|--------------------------------------------------------------|
| `uid`                    | UUID                            | Unique identifier for the questionnaire instance             |
| `addedAt`                | Long                            | Timestamp when questionnaire was created                     |
| `validUntil`             | Long                            | Expiration timestamp (-1 for no expiration)                  |
| `questionnaireJson`      | String                          | Serialized questionnaire definition and elements             |
| `triggerJson`            | String                          | Serialized trigger configuration that created this instance  |
| `elementValuesJson`      | String?                         | JSON-encoded participant answers                             |
| `updatedAt`              | Long                            | Last modification timestamp                                  |
| `openedPage`             | Int?                            | Current/last viewed page (for multi-page questionnaires)     |
| `status`                 | PendingQuestionnaireStatus      | Current state (NOTIFIED, PENDING, COMPLETED)                 |
| `finishedAt`             | Long?                           | Completion timestamp                                         |
| `notificationTriggerUid` | UUID?                           | Reference to associated NotificationTrigger (if any)         |
| `displayType`            | PendingQuestionnaireDisplayType | How questionnaire is presented (INBOX, NOTIFICATION_TRIGGER) |

#### Status Lifecycle
PendingQuestionnaire progresses through three states:

1. **NOTIFIED**: Initial state when questionnaire is created and ready for display
2. **PENDING**: Participant has opened questionnaire and is actively answering
3. **COMPLETED**: All responses submitted and questionnaire finished

#### Creation and Display Types
Questionnaires can be created through two pathways:

- **INBOX**: Manual questionnaires added directly to participant's inbox
- **NOTIFICATION_TRIGGER**: Automatic questionnaires triggered by NotificationTrigger events

When created via NotificationTrigger, the `notificationTriggerUid` field links to the triggering event, enabling string interpolation and contextual data access.

#### Answer Management
Participant responses are stored in `elementValuesJson` as a serialized map of element IDs to ElementValue objects.

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

## Sensors

### UI Tree Sensor

The [UITreeSensor](app/src/main/java/de/mimuc/senseeverything/sensor/implementation/UITreeSensor.java) captures privacy-preserving structural representations of UI screens and user interactions for behavioral analysis. It works in conjunction with the accessibility service to log detailed UI hierarchies without capturing any personal data.

#### How It Works

1. **Accessibility Service** captures UI trees and user interactions via [UITreeConsumer](app/src/main/java/de/mimuc/senseeverything/service/accessibility/UITreeConsumer.kt)
2. **SnapshotBatchManager** batches the data (50 snapshots or 60 seconds)
4. **UITreeSensor** receives and logs the batch to database (`ui_tree.json`)

#### Data Structure

Each logged line contains a batch with two types of snapshots:

**1. Skeleton Snapshots** (Full UI Structure)
```json
{
  "timestamp": 1699876543210,
  "appPackage": "com.instagram.android",
  "framework": "NATIVE",
  "skeleton": {
    "signature": "a3f5b8c...",
    "nodes": [
      {
        "id": 0,
        "type": "CONTAINER",
        "region": "CENTER",
        "sizeClass": "FULLSCREEN",
        "relativeX": 0.0,
        "relativeY": 0.0,
        "clickable": false,
        "hasText": false,
        "textCategory": "SHORT_PHRASE"
      }
    ]
  }
}
```

**2. Interaction Snapshots** (User Actions)
```json
{
  "timestamp": 1699876545123,
  "skeleton": {
    "signature": "a3f5b8c...",
    "nodes": []
  },
  "interaction": {
    "type": "TAP",
    "targetNodeId": 2,
    "tapX": 0.9,
    "tapY": 0.89
  }
}
```

#### Privacy Features
- Text content categorized by length only (SINGLE_WORD, SHORT_PHRASE, SENTENCE, etc.)
- No actual text, usernames, messages, or content
- Only structural properties (type, position, size)

#### Interaction Types

The sensor tracks five types of user interactions:
- **TAP**: Single tap, double-tap, button clicks
- **LONG_PRESS**: Long press events
- **SCROLL**: Scrolling through content
- **TEXT_INPUT**: Text entry in input fields
- **SWIPE**: Swipe gestures

#### Supported Frameworks

Works across UI frameworks via accessibility service:
- Native Android (View, Jetpack Compose)
- React Native
- Flutter
- Xamarin
- WebView (Cordova, Ionic, Capacitor)
- Unity

## Known Issues
* AppSensor does not sample the foreground activity (function was deprecated with Android Lollipop)
  * **Current Solution** We're using [AccessibilitySensor](app/src/main/java/de/mimuc/senseeverything/sensor/implementation/AccessibilitySensor.java) as it yields useful information about app use, which can be post-processed to infer opened apps and interactions
  * *Alternative*: We could also use the [UsageStats](https://developer.android.com/reference/android/app/usage/UsageStats) if [`getLastTimeUsed()`](https://developer.android.com/reference/android/app/usage/UsageStats#getLastTimeUsed()) is reliable enough
* Starting with Android 15, we can [no longer run a foreground service](https://developer.android.com/about/versions/15/changes/foreground-service-types#microphone) that'll record from the microphone
  * **Needs Clarification**: Instead, we could maybe ask the participants to open the app so the microphone becomes active again? 