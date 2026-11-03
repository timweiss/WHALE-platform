# Screenshot sampling

Takes a random sample of screenshots per app that participants rate in the evening. Each screenshot
belongs to exactly one logged UI tree (same app, timestamp and signature). Screenshots stay on the
device; only counts are uploaded.

## How it works

- **Sampling unit:** every new screen that is logged as a UI tree (a changed tree signature) of a
  configured app. Nothing is sampled while the UI tree sensor isn't running, because then no tree
  is logged either.
- **Algorithm:** reservoir sampling (Algorithm R), one reservoir of N slots per app and period.
  The first N screens fill the reservoir. After that, screen number t replaces a random slot with
  probability N/t. At the end of the period, every screen has had the same chance of N/t to be in
  the sample, whatever the total number of screens.
- **Period:** from one rating time to the next, all planned when the study starts (see
  [Configuration](#configuration)). The period's key is its end time. The first logged UI tree
  inside a planned period (of any app) opens its store, so no alarm is needed. A screenshot that
  finishes after the rating time is discarded. A period covers the full 24 hours of use; there is
  no sampling window within the day.
- **Frozen settings:** a period keeps the settings of the phase it started in until it ends, even
  if the study phase changes meanwhile.
- **Persistence:** the counter t and the slot table are written to disk on every change, so
  sampling continues correctly after the process restarts. Without them, a restart would reset t
  and bias the sample towards later screens.

## Screenshots

- **Android 14 and later:** only the app window the UI tree was read from is captured. This
  excludes the status bar, notifications and the keyboard.
- **Android 12 and 13:** the whole display is captured, including the status bar, notifications
  and the keyboard.
- **Format:** the short side is scaled to 720 px, saved as JPEG at quality 80, about 110 KB per
  image.
- **Threading:** captures run on their own background thread, one at a time, and never delay UI
  tree captures.

## Dropped screens

A selected screen is dropped when its screenshot fails. Possible reasons:
- *busy:* the previous screenshot was still being written
- *interval too short:* Android's limit on how often an app may take screenshots
- *invalid window:* the window disappeared
- *secure window:* the app blocks screenshots

A dropped screen is taken out of the stream: t goes back down by one, and the screen is counted as
dropped. The sample stays uniform over the screens that weren't dropped.

Almost all drops are screens that appear within a few hundred milliseconds of a sampled screen,
i.e. intermediate screens during fast navigation. Most of them happen early in each period, while
the reservoir is filling. A rough estimate from a first test is under 1% of an app's screens per
day. The real share is reported for every app and period (see below).

## Data for the analysis

For each ended period, one log line per app goes to the diagnostic log, which is uploaded:

```
sampling summary: periodEnd=<epoch ms> package=<app> itemsSeen=<t> itemsDropped=<n> samples=<filled slots> complete=<bool> phase=<name>
```

- **`phase`:** the study phase whose settings the period was opened with. A period can outlast
  its phase, so this is the phase the sample belongs to. It is the last field because the name
  may contain spaces: everything after `phase=` is the name. `?` if the period isn't in the schedule (e.g. the
  DataStore was erased before the period was reported).
- **`itemsSeen` + `itemsDropped`** = the app's UI trees logged during the period. Every logged tree
  is in exactly one of the two counts. Dropped share = `itemsDropped / (itemsSeen + itemsDropped)`.
- **When it's written:** at the first UI tree after the period ended (when the next period opens,
  or when it is the last one before a gap or the study end), and before a period is deleted. This
  means the line can arrive after the rating time.
- **`complete=false`:** the period was deleted before it ended (study end).
- **Duplicates:** two processes reporting at the same moment can duplicate a line. Deduplicate on
  `(periodEnd, package)`.
- **Known gaps:**
  - A screen whose screenshot finished just after the rating time counts in `itemsSeen`, but its
    screenshot was discarded. This is at most one screen per period.
  - A power loss right after a state write can lose that period's counts.

## Storage and privacy

- **Location:** `noBackupFilesDir/screenshot_samples/<periodEnd>/<package>/`, containing
  `state.json` and one JPEG per filled slot.
- **Internal storage:** private to the app, so no other app can read the files.
- **Not backed up:** `noBackupFilesDir` is excluded from Auto Backup and from device-to-device
  transfer.
- **Never uploaded:** no upload or FileProvider path covers this directory.
- **Replaced images** are deleted immediately. Files the state doesn't reference (left over after
  a crash) are deleted when the state is loaded.
- **Deletion:** `ScreenshotStore.deletePeriod` deletes a period after rating, and
  `ScreenshotStore.deleteAll` deletes everything at study end. *TODO:* both are wired up together
  with the rating lifecycle. Until then, screenshots stay on the device.

## Performance (first test, low-spec device)

The 5-minute `UITreeConsumer` stats line includes `screenshotsRequested/Saved/Failed`,
`screenshotMsTotal/Max` (from request to written file), `screenshotCpuMsTotal`,
`screenshotBitmapKbMax` (memory peak per capture) and `screenshotKbWritten`.

First test interval, while the reservoir was filling:

| Measure | Value |
|---|---|
| Screenshots requested / saved / failed | 35 / 33 / 2 |
| Time per screenshot (average / max) | about 150 ms / 348 ms |
| CPU per screenshot | about 70 ms |
| Memory peak per capture | about 10 MB, freed right after |

Screenshots added about 20% on top of the tree captures' CPU time. That share falls once the
reservoir is full and screens are selected rarely.

## Configuration

Screenshot sampling is configured per study phase, in the phase's `screenshotSettings` (`null`:
no sampling in this phase):

```json
{
  "periodEnd": "20:00",
  "packageSettings": [{ "packageName": "com.instagram.android", "sample": 10 }]
}
```

- **`periodEnd`:** the rating time, in the device's time zone at study start.
- **`sample`:** N, the reservoir size for the app. Apps not listed are not sampled.

**Schedule:** when the study starts (`StartStudyScreen`), `planScreenshotPeriods` plans every
period of the study as absolute timestamps and saves them to the DataStore (`screenshotSchedule`):
- The first period starts at the study start and ends at the next rating time. Each further period
  starts at the previous one's end.
- A period takes the settings of the phase of the study day it starts on, by calendar date like
  `getCurrentStudyPhase()`, not through the `ChangePhaseReceiver` alarms. It keeps them until its
  rating time, so a phase change never changes N in the middle of a reservoir, which would make
  the sample non-uniform. Only the next period uses the new phase's settings and rating time.
- Days of a phase without screenshot settings (or with an invalid rating time), gaps between
  phases and the time after the last phase get no period. Planning continues at the next midnight.
- The period that starts on the last study day ends at its rating time on the day after.

The schedule is the only place that holds a period's settings: it is planned once and never
changes. After a process restart, the period's store continues with its state on disk. Reporting
and deleting periods look up the phase name in the schedule, so `ScreenshotStore.deleteAll` has to
run before the DataStore is erased.

**At runtime:** `UITreeConsumer` observes the schedule in the DataStore, so `:remote` picks it up
even if it was already running when the study started. While a period runs, a UI tree costs one
time comparison and one map lookup for the period's settings. Otherwise the schedule is searched
in memory. Nothing is read from disk for a UI tree, also in studies that don't sample screenshots.

**Known gap:** the timestamps are fixed at study start. If the participant changes time zones,
the rating time stays at the time of the original zone. Daylight saving time is handled correctly.

**Logs** for debugging:
- `=== Screenshot Schedule ===` (tag `ScreenshotSchedule`), once at study start: every period with
  start, end, phase and sample sizes.
- `Opened period: start=… end=… sampleSizes=… phase=…` (tag `ScreenshotPeriods`) when a period
  opens, or `Reopened period: …` when it continues after a process restart.

The debug view (`StudyDebugInfo`) shows all stored screenshots, grouped by app and period.

## Code

| File | Role |
|---|---|
| `service/accessibility/UITreeConsumer.kt` | sampling decision |
| `service/accessibility/ScreenshotSchedule.kt` | plans all periods at study start, schedule log |
| `service/accessibility/ScreenshotPeriods.kt` | finds and opens the running planned period |
| `service/accessibility/ScreenshotStore.kt` | reservoir state on disk, period summaries, read-only listing |
| `service/accessibility/ScreenshotCapturer.kt` | screenshot, downscaling, JPEG, stats |
| `res/xml/accessibility_service_config*.xml` | `canTakeScreenshot="true"` |
