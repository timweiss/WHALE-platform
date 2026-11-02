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
- **Period:** from one scheduled rating time to the next. The period's key is its end time. The
  first sampled screen after the rating time opens the next period, so no alarm is needed. A
  screenshot that finishes after the rating time is discarded.
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
sampling summary: periodEnd=<epoch ms> package=<app> itemsSeen=<t> itemsDropped=<n> samples=<filled slots> complete=<bool>
```

- **`itemsSeen` + `itemsDropped`** = the app's UI trees logged during the period. Every logged tree
  is in exactly one of the two counts. Dropped share = `itemsDropped / (itemsSeen + itemsDropped)`.
- **When it's written:** when the next period opens or the process restarts, and before a period
  is deleted. This means the line can arrive after the rating time.
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

Currently hard-coded in `UITreeConsumer`: Instagram and YouTube with N = 10 each, rating time 20:00
in the device's time zone. These still have to come from the study configuration.

The debug view (`StudyDebugInfo`) shows all stored screenshots, grouped by app and period.

## Code

| File | Role |
|---|---|
| `service/accessibility/UITreeConsumer.kt` | sampling decision, period switch, settings |
| `service/accessibility/ScreenshotStore.kt` | reservoir state on disk, period summaries, read-only listing |
| `service/accessibility/ScreenshotCapturer.kt` | screenshot, downscaling, JPEG, stats |
| `res/xml/accessibility_service_config*.xml` | `canTakeScreenshot="true"` |
