# Verification record — 3 November 2026

## Version 0.4.0: experimental speakerphone microphone assistance

Kavach Live now offers separate **Screen** and **Speakerphone** sessions. Audio uses an opt-in microphone foreground service and ten-second in-memory PCM clips, with Muse Voice Transcribe 1.0 followed by Muse Spark 1.3 through Meta Model API. There is no direct call-stream access or concurrent fused audio/video analysis.

**No actual microphone/call capture or real Meta API request was run.** Keys remain placeholders. Provider contract tests mock the responses, and synthetic PCM is not evidence of accurate transcription. Real-device Android arbitration, capture lifecycle, provider access and latency remain unverified.

## Checks run

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug \
  :app:assembleLiveDebug :app:assembleOfflineDebug --offline --console=plain
python3 -m unittest discover -s live_server/tests -v
```

- **74 Live Android tests and 66 offline Android tests passed**, no failures/errors/skips. Shared cases run in both variants. New cases check PCM little-endian encoding and exact size, silence, model/provider/session/chunk validation, fabricated evidence rejection, late-result suppression, separate microphone disclosure/cancellation and disabled start when checks are paused.
- **39 backend tests passed.** New audio cases check authentication before inference, exact PCM bounds, silence without upload, valid WAV/multipart construction, Meta-only ASR and analysis requests, blank/invalid/oversized transcripts, unchanged transcript evidence, unsupported-language abstention and sanitized provider failures. HTTP tests use loopback sockets; Meta calls are mocked.
- Both APK builds succeeded. Both lint runs reported **zero errors and two warnings** (ChromeOsAbiSupport and UsableSpace).
- Both debug APK signatures verified.
- Both source/merged-manifest audits passed. The Live flavor now has two non-exported services: mediaProjection and microphone. It adds RECORD_AUDIO and FOREGROUND_SERVICE_MICROPHONE. The offline build retains no Internet or microphone/capture permission. Static checks disallow privileged call-audio sources and restrict AudioRecord to the dedicated Live service.

Logs: `evidence/audio-android-build.txt`, `evidence/audio-server-tests.txt`, and `evidence/manifest-audit.txt`. Counts/hashes: `evidence/verification.json`. Earlier records are historical: [0.3](VERIFICATION_V0_3.md), [0.2](VERIFICATION_V0_2.md).

## Artifacts

**Live:** `app/build/outputs/apk/live/debug/app-live-debug.apk`

SHA-256: `8ddaab21f1448844011ec346c9cf1aecd207e6b69dfdca65d3192c40ff905ec9`

**Offline:** `app/build/outputs/apk/offline/debug/app-offline-debug.apk`

SHA-256: `3711c486dda34adea6411223aec1cc018fb3cf4e053e0a0200a62eff7eccbfe4`

Both are arm64 debug development builds; install the matching backend/app. The old unflavored APK does not include these features.

## Remaining validation

Follow [AUDIO.md](AUDIO.md) for setup, privacy boundaries, operational gaps and the device checklist. Test room speech first, then separately verify the intended phone/call app on speakerphone. Android may deny/silence microphone capture during calls. Quiet or missing audio remains unassessed, never safe. Microphone capture pauses while Meta processes each clip; speech in these gaps is missed. The first profile assesses English only.

Verify permission denial/revocation, phone lock, task removal, capture errors, simultaneous call microphone ownership, headset/Bluetooth routing, session expiry and Stop during recording/upload/generation. Check that late results do not alert and no new uploads occur after stop. Evaluate ASR errors, real-model evidence, false positives/negatives, prompt injection and latency before personal-data use.

Stopping cannot retract audio already uploaded to the backend/Meta. There is no zero-retention claim. The original offline-text privacy gate remains enabled, and its patched native runtime/model inference is still unverified. Scripted Samples are not model predictions.
