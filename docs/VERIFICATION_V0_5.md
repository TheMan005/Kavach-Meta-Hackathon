# Verification record — 4 November 2026

## Version 0.5.0: API-only text, screen and microphone

The Check tab now submits reviewed text to Muse Spark through `/v1/text`. Backend settings replace local model installation. ExecuTorch dependency, local engine and model import have been removed from compiled app sources; historical implementation files are under `legacy/local-model`. The legacy-named offline flavor runs scripted samples only, with no local or cloud inference.

## Actual Meta API check

**One real request succeeded using the configured server key.** The input was a fictional benign family-dinner message, sent directly through the production backend adapter's `assess_text` method to Meta. The returned model was validated as `muse-spark-1.3`; its assessment passed schema/evidence checks and reported `no_clear_indicators`. No key, personal conversation or raw provider response was printed or saved. This verifies text API access and the adapter for one case, not accuracy, latency guarantees or the full phone-to-server connection. Actual image/audio inference remains unverified.

## Automated checks

- **78 Live Android tests and 70 offline Android tests passed**, with zero failures/errors/skips. Shared cases run in both variants. New tests cover cloud consent before any engine call, cancelled disclosure without inference, missing backend settings, request/model/provider binding, Hindi evidence, fabricated quotes, duplicate keys and response size. The UI tests use an explicit test engine and are not real-model runs.
- **51 Python backend tests passed**, including authenticated text HTTP routing, validation before inference, stateless Muse requests, exact original-text evidence, Hindi text, malformed/refused/incomplete model output and unchanged screen/audio/startup checks. Provider calls in the suite are mocked; HTTP transport uses loopback sockets.
- Both debug APKs built successfully. Lint: **zero errors and one ChromeOsAbiSupport warning per variant**. No baseline suppresses it.
- Both APK signatures verified. APK ZIP listings contain no ExecuTorch entries; Gradle no longer declares its dependency.
- Both source/merged-manifest audits passed. Offline retains no network or capture permission. Live retains its existing text/image network and microphone/mediaProjection permissions and two non-exported capture services.
- No phone instrumentation, installation or capture test was run. The source instrumentation consent test was updated, but not executed on hardware.

Commands:

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug \
  :app:assembleLiveDebug :app:assembleOfflineDebug --offline --console=plain
python3 -m unittest discover -s live_server/tests -v
```

Evidence: `evidence/api-only-android-build.txt`, `evidence/api-only-server-tests.txt`, `evidence/verification.json`, `evidence/manifest-audit.txt`. Earlier results in VERIFICATION_V0_4.md and other versioned records are historical.

## Artifacts

**Live:** `app/build/outputs/apk/live/debug/app-live-debug.apk`

SHA-256: `731d96ac59aabd6991061d298625486777a39266e9bd558a55561a29c41d04ce`

**Offline:** `app/build/outputs/apk/offline/debug/app-offline-debug.apk`

SHA-256: `34554d6927165cc7c0d0ad0edf73f05e1d469fbc72669b5641b7326ee0dd1e84`

Install the Live APK for all AI features. Restart the matching backend so it exposes `/v1/text`. Configure Backend in the phone using its origin and KAVACH_LIVE_TOKEN, then approve each text upload. The Meta key remains on the server. See [API setup](MODEL_SETUP.md).

## Practical limits

Text evidence consistency is not a guarantee of correct scam classification. English/Hindi are targets and Hinglish is experimental, without measured accuracy. API availability/rate limits, phone networking and cancellation under device lifecycle stress require further checking. Stop cannot retract data already sent. Meta retention terms apply; there is no zero-retention claim.

Screen mode remains English visual-text-only. Speakerphone mode remains experimental English microphone sampling with processing gaps and possible Android call-time silencing. Their physical capture and actual-model behavior remain unverified. Scripted Samples are not Meta predictions.
