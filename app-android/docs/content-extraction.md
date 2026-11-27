# On-device content extraction

Extracts posts, comments, profiles and other content items from logged UI trees on the phone, with
Gemma 4 E2B on LiteRT-LM. The pipeline is a port of the harness in ba-auswertung
(`scripts/python/content_extraction`, frozen configuration D / rows / v1, see PLAN.md and
DEPLOYMENT.md there). Results and transcripts are uploaded as sensor readings, so they can be
compared with the harness runs offline.

Status: the pipeline port, the study configuration and the upload retention exist. Model download,
extraction worker, onboarding notice and debug controls are open (`todo/content-extraction/`).

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

## Code

| File | Role |
|---|---|
| `contentextraction/Model.kt`, `Preprocess.kt`, `Segmentation.kt`, `Serialize.kt`, `Prompt.kt`, `Postprocess.kt` | port of the harness pipeline |
| `contentextraction/TreeLoader.kt` | snapshot node list (stored batch JSON) to tree; bounds as `Double` from the decimal text |
| `contentextraction/UploadGating.kt` | the flag and the derived upload requirement |
| `data/StudyRepository.kt` | `uploadConfigFor`: study upload config plus content extraction |
| `api/model/Study.kt` | `ContentExtractionConfig` |
| `scripts/export_content_extraction_golden.py` | golden files for the parity test, rendered hints |
| `backend/src/controllers/study.ts` | validation (`ContentExtractionBody`) |
