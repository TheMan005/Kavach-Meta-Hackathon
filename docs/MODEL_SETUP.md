# API setup — no local model installation

Version 0.6.0 runs every AI check through **Meta Model API**, using your backend's API key. The Android app no longer imports or runs Llama/ExecuTorch models. Historical sources under `legacy/local-model` are excluded from the Android build.

| Feature | Model | Backend endpoint |
|---|---|---|
| Check: reviewed text | Muse Spark 1.3 Standard | `/v1/text` |
| Live → Screen | Muse Spark 1.3 Standard | `/v1/frames` |
| Live → Speakerphone | Muse Voice Transcribe 1.0 → Muse Spark 1.3 | `/v1/audio` |

## Start the backend

Keep `META_API_KEY` and a separate random `KAVACH_LIVE_TOKEN` in the private project-root `.env`. The key is already configured in this workspace. Do not copy it into `.env.example` or the APK. The backend needs Internet access, Python 3.11+ and the dependencies in `live_server/requirements.txt`.

```sh
cd /home/gremlin/Documents/Projects/Meta_Hackathon
set -a
source .env
set +a
python3 -m live_server.server
```

If an older server is running, stop it with Ctrl+C first. The new server must support `/v1/text`. Its startup message confirms a listening socket, not Meta API access. It logs lifecycle status only, without request bodies or credentials.

## Configure the phone

Install the latest **Kavach Live** APK, then connect USB debugging and run:

```sh
.tooling/sdk/platform-tools/adb reverse tcp:8765 tcp:8765
```

1. Open **Settings** in the app.
2. Set **Backend address** to `http://127.0.0.1:8765` for USB forwarding. Remote servers require trusted HTTPS. Debug loopback forwarding still sends data off the phone.
3. Enter the `KAVACH_LIVE_TOKEN` from `.env` into **Server token**. This is not your Meta API key.
4. Tap **Connect**. Settings are kept in app memory, reset on process exit, and Connect verifies backend access without submitting content to Meta. Changing/clearing settings cancels active work.
5. Open **Check**, paste/type and review your excerpt, select optional sender context, and tap **Review & check**.
6. Read the upload disclosure, then **Send to Meta & check**. Text and sender context go through your backend to Muse Spark. Meta's processing/retention terms and API charges apply.

Text is capped at 8,000 UTF-16 units without truncation. English/Hindi are target languages; Hinglish is experimental and no accuracy has been established. Evidence must exactly match the reviewed text. Invalid/refused/incomplete output is unavailable, never a canned verdict. Paste/share never starts inference. Cancel, edit, pause and new input suppress late results; cancellation cannot recall uploaded text.

The backend uses fixed Meta endpoints/models, `store: false` for Spark, no tools/history and no fallback provider. No conversation history is intentionally saved by the app/backend; this is not a zero-retention guarantee for Meta or a forensic-erasure claim.

The `offline` build is now **scripted-sample-only** and runs no AI model. Use the `live` APK for all API features. The native runtime and local model import have been removed from the app.

For screen/microphone setup see [LIVE_VIDEO.md](LIVE_VIDEO.md) and [AUDIO.md](AUDIO.md). Current test and runtime status is in [VERIFICATION.md](VERIFICATION.md).

## Connection troubleshooting

Copy the value of KAVACH_LIVE_TOKEN from `.env`; pasted quotes, outer whitespace and the full `KAVACH_LIVE_TOKEN=...` line are accepted. META_API_KEY is not the phone token. The address and token have separate validation messages.

**Connect** shows progress, then either Connected or a specific error. Token rejected means the running server disagrees with the token; restart it after changing `.env`. Cannot reach backend means check the server, address and USB forwarding. On a USB-connected phone use `http://127.0.0.1:8765` and run `adb reverse tcp:8765 tcp:8765` on the computer. A successful connection validates backend access only; real model access is tested when you approve an assessment.
