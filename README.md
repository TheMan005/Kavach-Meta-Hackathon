# Kavach

An Android prototype for reviewing potential digital-arrest scams and related coercion. **All AI processing uses Meta Model API through your backend; no model runs on the phone.**

| App feature | Meta model |
|---|---|
| **Check** — text you review and approve | Muse Spark 1.3 |
| **Live → Screen** — consented screen-image samples | Muse Spark 1.3 |
| **Live → Speakerphone** — experimental microphone clips | Muse Voice Transcribe 1.0 → Muse Spark 1.3 |

This is a prototype, not established scam protection. Screen checks see visible English text only. Speakerphone mode may be blocked/silent during WhatsApp/Instagram calls and has processing gaps; it does not capture the call stream directly. Text checking targets English/Hindi; Hinglish is experimental. No accuracy claim is made.

## Run on your phone

1. Install `app/build/outputs/apk/live/debug/app-live-debug.apk` (version 0.6.0, Android 12+).
2. Start the matching backend using the private `.env`:

```sh
set -a
source .env
set +a
python3 -m live_server.server
```

3. For USB development, enable USB debugging and run:

```sh
.tooling/sdk/platform-tools/adb reverse tcp:8765 tcp:8765
```

4. Open **Settings** in Kavach. Enter `http://127.0.0.1:8765` and your **KAVACH_LIVE_TOKEN**, then **Connect**. Keep **META_API_KEY on the server**. Settings stay in memory.
5. Open **Check**, review your text, choose **Review & check**, then confirm **Send to Meta & check**.

Restart an old backend before testing: version 0.6.0 adds `/v1/text`. No local model download, export, import or privacy-patched native runtime is needed. [Complete setup](docs/MODEL_SETUP.md).

## Credentials and processing

`.env.example` contains placeholders only. The private `.env` is Git-ignored and holds the backend API key and separate phone-to-backend token. The backend also accepts `MODEL_API_KEY` when `META_API_KEY` is unset. Missing/placeholders fail startup. The APK contains no Meta key.

Your approved text, screen images or microphone clips go **phone → your backend → Meta**. Each mode has explicit consent. Spark uses Standard mode and `store: false`, without tools/history or a fallback model. The app/backend do not intentionally save conversation history; Meta processing/retention terms apply. Stop cannot retract data already sent. API usage may incur charges.

The backend is an authenticated Python service using verified HTTPS to Meta. Install its dependencies with `pip install -r live_server/requirements.txt` in your Python environment. Remote phone-to-backend connections require trusted HTTPS; debug USB loopback HTTP is supported. No server is publicly deployed by this project.

## Build and test

Kotlin/Compose; JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, target SDK 35, minimum SDK 31. Project-local JDK/SDK are retained under ignored `.tooling`. ExecuTorch and local-model import are no longer part of the build.

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug :app:assembleLiveDebug :app:assembleOfflineDebug
python3 -m unittest discover -s live_server/tests -v
```

Use **Kavach Live** for every AI feature. The legacy-named `offline` flavor is now a network-free, scripted-sample-only build; it runs no learned model. Twelve fictional Samples remain explicitly labeled scripted demos, not Meta predictions. The 48-case synthetic text evaluation dataset is not a measured accuracy result.

[Verification record](docs/VERIFICATION.md) contains actual counts, APK hashes and unverified runtime/device behavior. Older verification records are historical.

## Documentation

- [Text/API setup](docs/MODEL_SETUP.md)
- [Screen assistance](docs/LIVE_VIDEO.md)
- [Speakerphone audio](docs/AUDIO.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy](docs/PRIVACY.md)
- [Evaluation](docs/EVALUATION.md)

The original [build brief](KAVACH_CODEX_BUILD_BRIEF.md) is retained for reference. Later user requirements changed inference to Meta API only. This independent project has no Meta or government endorsement.

## Simpler navigation

Use **Check**, **Live**, and **Settings**. Samples, privacy information and expandable setup help are under Settings. Configure the backend once there; screen and microphone checks reuse it.
