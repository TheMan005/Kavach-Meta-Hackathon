# Experimental speakerphone microphone assistance

Kavach Live 0.6.0 adds **Live → Speakerphone**. It samples sound reaching the phone's microphone and sends it through your backend to **Muse Voice Transcribe 1.0**, then sends the machine transcript to **Muse Spark 1.3** for digital-arrest scam indicators. Both models run through **Meta Model API**; the same backend key is used. The example file contains placeholders; real keys stay in the private backend environment.

**Implemented, not device-validated:** microphone capture, consent, foreground notification, cancellation, backend transport and model adapters are implemented. Tests use synthetic PCM and mocked Meta responses. No actual call audio or real Meta transcription/assessment has been tested. This does not establish scam-detection accuracy.

## What Android permits

This is ordinary microphone capture using `AudioRecord` with source `MIC`. It is **not** WhatsApp/Instagram/telephone call-stream capture. Android may silence or deny the microphone when another app uses it, particularly during a call. A foreground microphone service allows eligible user-started microphone work to continue after switching apps; it does not override audio-input priority. See [Android audio-input sharing](https://developer.android.com/media/platform/sharing-audio-input) and [microphone foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).

Turn speakerphone on yourself. The app does not change call routing. Headphones/Bluetooth or low speaker volume may prevent it from hearing the other participant. It may capture only your own voice, or nearby people. An audible signal does not prove both sides are captured, and a silence threshold cannot distinguish all blocked-capture cases from a quiet room. No accessibility, privileged call-audio, root or recording bypass is used.

## Use it

1. Configure the backend with your key and a separate backend token using [Live setup](LIVE_VIDEO.md). Start the matching 0.6.0 backend and install `app/build/outputs/apk/live/debug/app-live-debug.apk`.
2. Connect once in **Settings**, then open **Live → Speakerphone**. It reuses the saved connection. Never enter your Meta API key in the phone.
3. Choose **Review microphone sharing**. Read the cloud upload, nearby-audio, participant-awareness, API-cost and capture-limit disclosures. Confirm only when participants know and agree to microphone sharing.
4. Grant microphone and notification access while Kavach is visible. Cancelling or denying does not start recording. Android's microphone privacy indicator and Kavach's persistent notification show microphone activity/status.
5. Test first using fictional English speech in a controlled setting. Then separately check whether the intended phone permits capture during the intended speakerphone call. Do not treat a working room-microphone test as proof of call compatibility.
6. Use **Stop microphone & clear**, the notification Stop action, or the Privacy pause switch. Closing the task, locking the phone, permission/notification revocation and the ten-minute session limit also stop the service.

**Screen and Speakerphone are separate sessions in this prototype.** Stop the active session to switch modes. There is no fused audio/video assessment.

## Sampling and results

- Capture ten seconds at 24 kHz, mono, signed little-endian PCM16: 480,000 bytes, in memory only.
- Check Android's recording-silenced flag during capture. Initialization/read errors stop with unavailable status. Silent/very quiet clips (RMS below 80 PCM units) are skipped locally with an explicit unassessed message; this is a heuristic, not voice activity detection.
- Stop the microphone while the backend processes the clip. There is no backlog. After each response, wait three seconds before the next clip. **Speech during processing/wait gaps is missed.** This is sequential batch assistance, not continuous realtime transcription.
- `/v1/audio` authenticates before decoding, validates exact PCM size and session/chunk IDs, and shares the bounded backend inference slot with screen checks.
- The backend wraps PCM in WAV and posts multipart `request` + `audio` to `https://api.meta.ai/v1/asr/transcribe` using `muse-voice-transcribe-1.0`, `PUSH_TO_TALK` and an English language hint. It does not identify speakers or request diarization.
- Non-empty transcripts up to 4,000 UTF-16 units are passed to `muse-spark-1.3` Standard via `/v1/responses`, with `store: false`, no tools/history and a bounded output budget. The ASR transcript is untrusted user data; its instructions must not be followed.
- The scam model must preserve the exact ASR transcript and quote evidence from it. Altered transcripts, invented quotes, incomplete generation and invalid output produce abstention, not “safe.” Evidence matching validates consistency, not what was actually spoken.
- The UI displays the latest clip's unverified transcript and warning/abstention. Generic notification warnings contain no transcript quotes. Results older than 90 seconds, from stopped sessions, or out of order are rejected/cleared. Local silence and network failure are explicitly unassessed.
- This first audio profile assesses **English speech only**. Muse Voice Transcribe supports more languages, including Hindi, but this application's multilingual scam-analysis path has not been implemented/evaluated. The validator explicitly rejects Devanagari and the prompt asks for other-language abstention; this is not comprehensive language detection.

## Privacy, costs and cancellation

The Live flavor adds `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, and a non-exported microphone foreground service. The offline flavor retains no microphone/network permissions. Microphone consent is separate from screen-sharing consent and is requested only for Speakerphone mode.

Audio goes **phone → your backend → Meta**; the transcript is then submitted to Meta's Muse Spark. No audio files or transcript history are intentionally written by the app/backend; samples are cleared after processing. This is not forensic erasure. Both participants' speech and nearby conversations may be present. Meta's processing/retention terms apply; `store: false` applies to the Spark Responses request and is not a transcription retention guarantee. Both ASR and reasoning usage can incur charges.

Stop invalidates results immediately and disconnects the local request. It cannot retract uploaded audio or guarantee cancellation of processing/charges already underway at the backend/Meta. There are no automatic retries or fallback models. Authentication, rate limits, network failures and invalid output never become a fabricated assessment.

Official API references checked on 3 November 2026: [Muse Voice Transcribe speech-to-text guide](https://dev.meta.ai/docs/speech-to-text), [Meta Responses protocol](https://dev.meta.ai/docs/protocols/responses).

## Device and actual-model checks still required

Verify microphone/notification deny/revoke, silence during calls, blocked initialization, speakerphone vs headset/Bluetooth, capture after switching apps, task removal, lock, expiry, and Stop during recording/upload/generation. Verify that no new chunks are uploaded after stopping and that late responses cannot alert. Test background speech, quiet speech, quoted warnings, benign conversation and adversarial spoken instructions. Measure transcription errors, false positives/negatives and end-to-end latency. No measured protection or latency claim is made from mocked tests.
