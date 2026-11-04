# Verification record — 5 November 2026

## Version 0.6.0: simpler UI and verified backend connection

Main navigation is now Check, Live and Settings. Samples and privacy controls are under Settings; setup help, result details and microphone transcripts expand on demand. Screen and Speakerphone reuse the central backend settings rather than asking for credentials again. Upload consent and Stop controls remain.

Settings now separates address and token validation. Token input accepts outer whitespace, matching quotes and copied KAVACH_LIVE_TOKEN assignments; it still rejects invalid characters/length, embedded header breaks and Meta API keys. Connect performs an authenticated, bounded GET /health without redirects before saving, with visible progress/success/errors. A connected backend is not proof of Meta API access. Unreachable backend and rejected token errors are distinct.

## Verification

- **87 Live Android tests and 76 offline Android tests passed**, no failures/errors/skips. Shared cases run in both variants. New tests cover token normalization and rejection, trimmed address, model/schema/response limits, actual loopback HTTP authentication/401/redirect behavior, connection-before-save, UI address-error separation and failed authentication without saving settings.
- Both APKs build; both lint runs report **zero errors and one ChromeOsAbiSupport warning**. Both signatures and merged-manifest/source privacy audits pass.
- Inspected the local .env token without displaying it: it matches the existing backend format. This does not prove the user's pasted value was identical.
- A direct local /health probe could not reach the configured loopback port at verification time. The failure is recorded as unreachable, not as an invalid token. No server was started or stopped and no key/token was printed.
- The Python backend was unchanged; its 51-test pass and one real synthetic text API success are historical results from 0.5.0, not rerun claims for this update. No new Meta request was made.
- No physical-phone UI or capture test was run.

Command:

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug \
  :app:assembleLiveDebug :app:assembleOfflineDebug --offline --console=plain
```

An initial test compilation used a JDK HTTP-server class unavailable on the Android test compile classpath. It was replaced with a standard ServerSocket fixture. The final command above passed. Evidence: `evidence/simple-ui-android-build.txt`, `evidence/verification.json`, `evidence/manifest-audit.txt`. Earlier records are historical.

## Artifacts

**Live:** `app/build/outputs/apk/live/debug/app-live-debug.apk`

SHA-256: `6207047caff17c5155fddd576c9f251087a64fb483bcb42eb2b33b67084c7408`

**Offline:** `app/build/outputs/apk/offline/debug/app-offline-debug.apk`

SHA-256: `df001db44d6a9ae397265842ad07ff5410a95eaa1de420679a21505b2419cd1c`

Use Kavach Live for AI checks. Start the backend, enable USB forwarding if using loopback, then Settings → Connect. See [setup](MODEL_SETUP.md). Settings remain process-memory only; no new credential persistence was added. Real-device capture, image/audio model inference and scam-detection accuracy remain unverified. Text API access previously succeeded on one synthetic case, not a benchmark.
