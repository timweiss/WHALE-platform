# On-device content extraction

Extracts posts, comments, profiles and other content items from logged UI trees on the phone, with
Gemma 4 E2B on LiteRT-LM. The pipeline is a port of the harness in ba-auswertung
(`scripts/python/content_extraction`, frozen configuration D / rows / v1, see PLAN.md and
DEPLOYMENT.md there). Results and transcripts are uploaded as sensor readings, so they can be
compared with the harness runs offline.

Status: complete in code, not yet run on a device (`todo/content-extraction/`).

## Configuration

Content extraction is configured per study, in `contentExtraction` (`null` or absent: off):

```json
{
  "model": { "sha256": "<64 lowercase hex digits>", "sizeBytes": 2588147712 },
  "packages": [
    { "packageName": "com.instagram.android", "hint": "App-specific notes:\n- ..." },
    { "packageName": "com.zhiliaoapp.musically", "hint": null }
  ],
  "sampleRate": 1
}
```

- **`model`:** the `.litertlm` file. The app downloads it from the study backend (with the
  participant's token, never from another host) and checks its SHA-256; the size is shown to the
  participant before the download.
- **`packages`:** the apps whose screens are extracted. Their UI trees must carry text, so each one
  should be in the phases' `uiTreeTextSettings.packages`; the backend does not check this.
- **`hint`:** the app's hint block exactly as the harness renders it. Export it with
  `app-android/scripts/export_content_extraction_golden.py` (`hints/<package>.txt`) and paste the
  file's content unchanged; the app inserts it verbatim, otherwise the prompt differs from the
  harness's. `null`: no hint block (untested in the harness for D).
- **`sampleRate`:** the share of a configured app's screens that is extracted, more than 0 and at
  most 1, default 1.

The backend validates the object when a study is created or updated (`PUT` replaces it, like every
field; without `contentExtraction` it is removed).

## Upload retention

While a study has content extraction, its UI trees stay on the phone until the extraction worker has
handled them, using the flag-gated upload (`sensor_upload_requirement`, `LogDataFlag`):

- Saving the study (`StudyRepository.saveStudy` → `uploadConfigFor`) adds the requirement `UITree`
  → `content_extraction_done` to the study's own `sensorUploadConfig`. A `UITree` row (a batch of up
  to four snapshots) uploads only once it has that flag.
- There is no `SensorUploadPolicy` for `UITree`: held-back trees are never deleted for a missing
  flag. The worker flags every row it handles, also rows without a configured app, not sampled
  screens and rows it could not process (with the reason in its run reading).
- While the model is still downloading, the worker waits and rows stay on the phone.
- The app saves the study at enrolment and does not refresh it later (like `sensorUploadConfig`), so
  changing `contentExtraction` in the backend only affects new enrolments.

## Model download

`ModelDownloadWorker` gets the model from the study backend (setup there: `backend/README.md`,
"Content extraction model").

- **When:** enqueued when the study starts (`StartStudyScreen`), if the study has
  `contentExtraction`, and again by every extraction run that finds no model, so a failed download
  is retried without anyone noticing it. Unique work `contentExtractionModelDownload` with `KEEP`:
  enqueuing again while one is pending does nothing, after a finished or failed one it starts a
  new attempt.
- **Where:** `noBackupFilesDir/content_extraction/`. While downloading the file is
  `<sha256>.litertlm.part`; it is renamed to `<sha256>.litertlm` only after its SHA-256 matched, so
  that name means "verified" (`ModelStore.isReady`).
- **Constraints:** Wi-Fi (unmetered) and storage not low; besides, the remaining bytes plus 256 MB
  must be free, otherwise it waits.
- **Resume:** a `.part` file is continued with a Range request (`206` appends, a `200` replaces it).
  A size in `Content-Range`/`Content-Length` that differs from the config fails before downloading.
- **Retry** (WorkManager backoff, the `.part` file stays): network errors, 429/5xx, not enough space,
  stopped worker. **Failure** (reason in the work's output `error`, nothing kept): 401/403/404, size
  or hash mismatch. A new enqueue then starts from scratch. Failures are counted in
  `content_extraction/download-failures.json` until a download succeeds; after 3 the extraction
  stops asking for the model (a wrong hash in the study config would otherwise mean a new 2.6 GB
  download every night).
- Runs as a foreground service (data sync, generic notification) when Android allows it; otherwise
  in JobScheduler's ~10-minute windows, continuing each time. Progress: the work's progress
  `downloadedBytes`/`totalBytes`.
- The model is not deleted at the study end: participants uninstall the app afterwards.

## Extraction worker

`ContentExtractionWorker` runs in its own process, `:extraction` (a `RemoteCoroutineWorker` hosted by
`ContentExtractionWorkerService`): a native crash or OOM of the model runtime only ends that process,
and CPU time and peak memory of a run cover only the extraction.

- **Scheduled:** at night only (00:00–06:00 local time, `ExtractionWindow`), every 15 minutes while
  the phone is charging, idle and the battery isn't low, so phones in use are never slowed down.
  Enqueued when the study starts. Outside the window a run ends right away, and a running one stops
  at 06:00. Not a foreground service: Android stops a run after about 10 minutes or when the phone
  is used, and the next run continues the backlog.
- **Study end:** the scheduled runs go on after the last sensing day until every UI tree is handled.
  Then the run enqueues one more upload (`FINAL_UPLOAD_AFTER_EXTRACTION`, not shown on the end
  screen) and cancels the scheduled extraction. Cancelling the participation stops the extraction
  and the download right away (`cancelContentExtraction`).
- **Manual:** `enqueueManualContentExtraction`, without constraints, as a foreground service, e.g. on
  battery to measure power (debug settings, ticket 06).
- **Model missing:** the run enqueues the download (`KEEP`: only if none is pending, so also after
  a failed one) and ends; the rows wait. After 3 failed downloads (`DownloadFailures`) the model
  counts as unavailable: the run flags every waiting row (outcome `model_unavailable`), so the trees
  upload without results.
- **One run at a time:** a lock file (`content_extraction/run.lock`), so a manual and a scheduled run
  never load two models.
- **Per row** (oldest first, a batch of up to four snapshots): snapshots of configured apps with
  `isSampled(snapshotId, sampleRate)` are extracted, one call per segment. The readings and the row's
  flag are written in one transaction; a run stopped in the middle of a row leaves the row for the
  next run.
- **Generation:** LiteRT-LM, GPU first and CPU if the GPU engine can't be created; greedy
  (temperature 0), thinking off, output constrained to the JSON schema, 8192 tokens of context,
  at most 4096 output tokens; a fresh conversation per call.
- **Engine can't start** (on GPU and CPU): the run records why and flags every waiting row, so they
  upload without results. The next run tries again.
- **Crashes:** before starting the engine and before each row the run writes
  `content_extraction/run-state.json`; a normal end or stop removes it. A file left behind means the
  process died there. The same row twice: it is flagged with `crashed` readings and skipped. The
  engine start twice: handled like "engine can't start".
- **Sampling** is reproducible offline: a snapshot is extracted if
  `(int.from_bytes(hashlib.sha256(snapshot_id.encode()).digest()[:8], "big") >> 11) / 2**53 < sampleRate`.

## Readings

`ContentExtraction`, one per extracted snapshot (`ContentExtractionReading`):

| Field | Meaning |
|---|---|
| `runId` | the run's `ContentExtractionRun` reading |
| `snapshotId`, `snapshotTimestamp`, `appPackage` | the UI tree snapshot (join with `UI_Tree_Skeletons`) |
| `outcome` | `extracted`, or `crashed` (the process died on this row twice; no calls) |
| `modelSha256`, `runtime`, `backend`, `rulesVersion` | what produced it (`litert-lm 0.20.0`, `gpu`/`cpu`, `v1`) |
| `segmentationMode` | `anchor` or `whole` |
| `calls[]` | per segment: `segment`, `templateHash`, the exact `prompt`, `rawOutput`, `error`, `wallMs`, `prefillTokens`, `decodeTokens`, `prefillTokensPerSecond`, `decodeTokensPerSecond`, `timeToFirstTokenSeconds` |
| `items[]` | postprocessed items of all calls, each with `_segment` (as in the harness's `predictions.json`) |
| `droppedItems` | items postprocessing removed |
| `wallMs` | the snapshot's time from tree to items |

`ContentExtractionRun`, one per run that started (or failed to start) the engine
(`ContentExtractionRunReading`): `trigger` (`scheduled`/`manual`), `outcome` (`done`, `stopped`,
`engine_failed`, `model_unavailable`), start and end time, `backend`, `gpuError` (why the CPU ran), `engineInitMs`,
`engineErrors`, counts (`rowsHandled`, `rowsFlaggedWithoutExtraction`, `rowsCrashed`,
`rowsUndecodable`, `snapshotsSeen`, `snapshotsOfConfiguredApps`, `snapshotsExtracted`, `calls`,
`callErrors`), `cpuMs` (CPU time of the process), `peakRssKb` (VmHWM; reset at the run start if
`peakRssReset`), `batteryStart`/`batteryEnd` (level, charge counter in µAh, charging), thermal
status at start and end, `stopReason` (WorkManager's, when the system stopped the run) and the
device (manufacturer, model, SoC, SDK, RAM).

Energy only means something for runs on battery (`charging` false at start and end): the charge
counter difference over the run. Scheduled runs happen while charging.

## Participants and lab members

- **Start screen:** in a study with `contentExtraction`, a notice before the start: part of the
  screen content is analysed on the phone, a model of `model.sizeBytes` is downloaded over Wi-Fi
  only, and the analysis runs at night while charging. Informational only: enrolling is opt-in
  already.
- **End screen:** while UI trees still wait for extraction after the study end, a note asks to keep
  WHALE installed and charge the phone overnight. Checked when the screen loads (not live: a live
  query would re-run on every sensor write).
- **Debug settings** (`StudyDebugInfo`, "Content extraction"): model status (ready, or how much is
  downloaded), the download work's state, progress, attempt and error, failed downloads, UI tree rows
  waiting, the manual run's state and the last run (`content_extraction/last-run.json`, as the
  reading itself is deleted once uploaded). Buttons:
  - **Download model now:** clears the failure count and (re)starts the download (`REPLACE`; the
    `.part` file is kept, so it resumes). Still Wi-Fi only.
  - **Run extraction now:** a manual run (foreground, no night window or constraints), e.g. on
    battery to measure power.
  - **Refresh extraction status:** reloads files and counts.

## Code

| File | Role |
|---|---|
| `contentextraction/Model.kt`, `Preprocess.kt`, `Segmentation.kt`, `Serialize.kt`, `Prompt.kt`, `Postprocess.kt` | port of the harness pipeline |
| `contentextraction/TreeLoader.kt` | snapshot node list (stored batch JSON) to tree; bounds as `Double` from the decimal text |
| `contentextraction/UploadGating.kt` | the flag and the derived upload requirement |
| `contentextraction/ModelDownload.kt` | `ModelStore` (files = state), `ModelDownloader` (resume, checks) |
| `workers/ModelDownloadWorker.kt` | the worker and `enqueueModelDownload` |
| `contentextraction/UiTreeBatch.kt` | stored UI tree batch to snapshots, sampling |
| `contentextraction/Engine.kt` | `ExtractionEngine`, `LiteRtExtractionEngine` (settings, GPU/CPU) |
| `contentextraction/ExtractionRunner.kt` | rows → readings + flag, crash markers, engine failure |
| `contentextraction/RoomExtractionStore.kt` | the runner's database access |
| `contentextraction/Readings.kt`, `Measurements.kt` | reading shapes, process/battery/thermal measurements |
| `workers/ContentExtractionWorker.kt` | the worker in `:extraction`, its service, scheduling, lock |
| `backend/src/controllers/contentExtraction.ts` | authenticated model endpoint |
| `data/StudyRepository.kt` | `uploadConfigFor`: study upload config plus content extraction |
| `api/model/Study.kt` | `ContentExtractionConfig` |
| `scripts/export_content_extraction_golden.py` | golden files for the parity test, rendered hints |
| `backend/src/controllers/study.ts` | validation (`ContentExtractionBody`) |
