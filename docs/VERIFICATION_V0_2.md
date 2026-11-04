# Verification record — 2 November 2026

## Version 0.2.0: live video-call assistance

Final Android command completed successfully using the installed project JDK/SDK and cached Gradle dependencies:

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug \
  :app:assembleLiveDebug :app:assembleOfflineDebug --offline --console=plain
```

- **Live: 62 tests passed; offline: 56 tests passed.** No failures, errors or skips. Shared tests run in both variants; these are not 118 independent cases.
- Coverage includes existing text/consent/import checks plus strict live protocol validation, Meta-model allowlisting, English/Hindi handling, evidence consistency, endpoint/token validation, stale/session/frame rejection and expiration. Live-only tests cover disclosure cancellation, disabled protection, and image row-stride/buffer boundaries.
- **19 Python server tests passed**, covering schema/evidence validation and authenticated loopback HTTP with synthetic JPEGs, malformed/oversized requests, unavailable results and busy handling. The HTTP tests inject an explicit test engine; they do not run a learned model.
- **4 existing evaluation-tool tests passed.**
- **Lint: zero errors and two warnings per variant.** Remaining warnings are ChromeOsAbiSupport (arm64 phone target) and UsableSpace (conservative free-space check). Offline dependency lookup does not establish that pinned dependencies are current.
- Both merged-manifest/source audits passed. Offline retains only the AndroidX app-scoped signature permission. Live adds INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION and POST_NOTIFICATIONS, with one non-exported mediaProjection service. See `evidence/manifest-audit.txt`.
- Both APK signatures verified. APK metadata confirms version 0.2.0, min SDK 31 and target SDK 35.
- `adb devices` returned no connected devices. No physical-device install, capture, notification or lifecycle test was run.

## Artifacts

### Live

`app/build/outputs/apk/live/debug/app-live-debug.apk` (67,402,487 bytes).

SHA-256: `fa3b125598b1d0b432ee7b6ebc695f76ca1fd5de8127da6083435f06915968e2`

### Offline

`app/build/outputs/apk/offline/debug/app-offline-debug.apk` (67,303,384 bytes).

SHA-256: `f26a566dcf71e7ebdf6b951102a37c037c67cebd0d65d58c9a2e2510bc043e19`

Both artifacts are development builds, debug-signed, arm64-v8a, without model weights. The old unflavored `app/build/outputs/apk/debug/app-debug.apk`, if present, is the earlier 0.1.0 artifact and does not contain Live. Machine-readable results are in `evidence/verification.json`.

## Limits and prerequisites

The live feature implements consented screen sampling, transport, a self-hosted Meta Llama 3.2 11B Vision Instruct adapter, validation and warnings. **The actual vision model has not been loaded or run.** No weights or model licenses were obtained, and no GPU server was deployed. Runtime dependencies are pinned but the full GPU environment was not installed. Follow [live setup](LIVE_VIDEO.md) to provision your own model host.

Live assistance examines visible English text only. It does not capture WhatsApp/Instagram call audio, authenticate callers/documents, or detect deepfakes. Quoted evidence is model-read text, not independently verified OCR. Protected screens, screenshot prompt injection, hallucinations and missed spoken threats remain limitations. Tests establish software behavior, not scam-detection accuracy, latency, privacy on an actual phone or real-world protection.

Device validation still needs Android capture consent/revocation, app-only sharing, rotation, blank/protected screens, notifications, lock/unlock, stopping during an in-flight request, process/task removal and low-memory behavior. Actual-model validation needs synthetic visual cases, non-English abstention, injection cases and measured generation latency/error rates.

The original local-text ExecuTorch inference gate remains enabled: upstream native prompt/token logging requires the documented privacy patch before real text may be processed. Neither the patched native AAR nor actual text inference was verified. Samples remain labeled scripted examples. The separate 48-case synthetic text dataset is not an accuracy benchmark result.

## Earlier prototype verification

Before flavor separation, version 0.1.0 passed 39 Android host tests, 28 standalone JVM tests and four evaluation-tool tests; its instrumented consent APK compiled but was not run on a device. Those are historical results, not fresh device/runtime evidence for 0.2.0. The original dependency graph in `evidence/debug-runtime-dependencies.txt` describes that earlier build.

The project uses the retained Temurin JDK 17 and Android SDK under `.tooling`, selected by `tools/android.sh`. Google SDK license acceptance was explicitly authorized by the user. No model terms were accepted on the user’s behalf.
