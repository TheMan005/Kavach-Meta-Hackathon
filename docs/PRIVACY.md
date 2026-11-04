# Privacy boundaries — API-only app

All AI processing uses Meta Model API through the configured backend. The Meta key is never embedded in the APK. Backend address/token stay in process memory. APK backup is disabled; no conversation, audio, image, transcript or result history is intentionally saved by the app/backend. This is not forensic erasure or a guarantee about Meta/OS/keyboard/proxy retention.

## Text

Paste/share opens editable input; it never uploads automatically. Each assessment requires a dialog showing the backend and Meta destination, API costs/retention and cancellation limits. Only reviewed text and optional sender context are submitted. Data already uploaded cannot be recalled. `store: false` is set on the Spark request; it is not a blanket zero-retention claim.

Text and results are discarded on Done/clear. Cancellation, edits, pause and changed backend settings suppress late responses. No caller identification, inbox access, automatic reporting, link fetching or payment actions occur. Advice is static; model instructions are never executed. Evidence must match input, but model conclusions may still be wrong.

## Live modes

Screen and Speakerphone have separate consent, foreground status and Stop controls. Screen uses MediaProjection. Speakerphone uses ordinary microphone access, which Android may silence during calls. The app has no privileged call-stream, accessibility, camera or overlay access. See [Screen boundaries](LIVE_VIDEO.md) and [audio boundaries](AUDIO.md).

Live permissions: Internet, foreground service, mediaProjection, microphone foreground service, RECORD_AUDIO and notifications. Services are non-exported. Offline retains no network/capture permissions and runs only scripted demos; no local learned model is present in either build.

## Backend

Authentication precedes processing. Requests/responses and concurrency are bounded. Meta URLs/models are fixed, HTTPS verifies certificates, redirects and automatic retries are disabled. Spark runs Standard mode; no Contributor mode or fallback provider. The backend does not log request bodies, keys or model output. Startup/shutdown messages are permitted and explicitly distinguish configured service from verified API access.

Source and merged-manifest audits are static checks, not proof of device/network behavior. Physical-phone tests, actual provider access and model quality need separate verification. See VERIFICATION.md. Keep `.env` private and out of version control.
