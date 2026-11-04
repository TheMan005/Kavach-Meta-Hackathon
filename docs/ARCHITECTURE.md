# Architecture — API-only inference

Android Kotlin/Compose presents editable input, explicit upload consent, evidence and static next-step advice. `KavachApplication` creates `ApiTextEngine`; no local runtime is linked or loaded. Historical local model sources are excluded under `legacy/local-model`.

`BackendSettings` holds an origin and server token in process memory. The Settings tab validates settings and makes an authenticated health request before saving; changing them cancels text and capture work. Screen and Speakerphone use these shared settings. The Meta API key remains in the Python backend environment.

## Text checks

`AssessmentViewModel` gates every request behind review and consent. `ApiTextEngine` POSTs bounded text/sender/request ID to `/v1/text` over HTTPS (debug loopback exception). It disables redirects, caps responses, checks model/provider/request ID and validates evidence against original input. Cancelling disconnects the active request and invalidates its generation; ViewModel request IDs suppress late results after edits, pause, cancel or new shares.

The backend authenticates before reading/processing payloads, validates exact request schema and UTF-16 limits, calls Muse Spark 1.3 through Meta Responses with untrusted text in the user role, and validates the assessment against the exact input. Hindi evidence is supported without translation. Refusals, incomplete output, incorrect models and fabricated evidence produce no verdict. Sender identity is not verified; unknown sender alone is not evidence.

## Screen and microphone

`src/live` provides MediaProjection and microphone foreground services, each with independent consent and visible Stop. Only one capture mode runs at a time. Screen `/v1/frames` uses Muse Spark on JPEG samples. Microphone `/v1/audio` validates ten-second PCM chunks, calls Muse Voice Transcribe, then Muse Spark on its exact transcript. They have no direct call-stream access or temporal conversation memory. See LIVE_VIDEO.md and AUDIO.md for budgets, gaps and Android limitations.

The backend has one inference slot across all three endpoints and bounded clients. Busy requests return unavailable rather than queue. No automatic retries/fallback models or external tool actions exist. Lifecycle startup/shutdown logs contain no input, prediction or credential data. Health reports configuration, not provider access.

`src/offline` supplies no-op capture bindings and no network permission. Its UI cannot start API checks and it has no local AI runtime; it is a scripted-sample-only artifact. Main shared code is common, but no inference executes in that flavor.
