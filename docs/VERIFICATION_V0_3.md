# Verification record — 3 November 2026

## Version 0.3.0: Muse Spark / Meta Model API

The Live flow now calls **muse-spark-1.3 Standard** through the official Meta Responses endpoint. The backend holds the API credential; Android discloses that images go through your backend to Meta. The previous local Llama vision adapter was replaced. No GPU or weights are needed for Live. The offline text path remains gated separately.

**No real Meta API request was made.** Credentials remain placeholders. Provider responses in tests are mocked; these results do not prove model accuracy or real-world scam protection. No physical-device tests were run in this update.

## Checks run

```sh
./tools/android.sh :app:testLiveDebugUnitTest :app:testOfflineDebugUnitTest \
  :app:lintLiveDebug :app:lintOfflineDebug \
  :app:assembleLiveDebug :app:assembleOfflineDebug --offline --console=plain
python3 -m unittest discover -s live_server/tests -v
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

- **64 Live Android tests and 58 offline Android tests passed**, with no failures or skips. Shared tests run in both variants; counts are not all distinct cases. The updated Compose test checks disclosure of Meta's API destination before consent; protocol tests reject legacy Llama and other-provider metadata.
- **27 server tests passed**, including the real adapter with mocked HTTPS, an inline JPEG sent to the fixed Meta endpoint, authorization, no Responses storage/history/tools, placeholder rejection, redirects/auth errors/rate limits without retries, timeout, response bounds, refusals/incomplete/ambiguous model output and authenticated phone-to-backend routing. Synthetic loopback HTTP tests do not contact Meta.
- **4 evaluation-tool tests passed.**
- Server startup with a placeholder Meta key failed with the expected setup message, before any API call.
- Both lint runs reported **zero errors and two warnings** (ChromeOsAbiSupport and UsableSpace). Offline build mode does not check whether newer dependencies exist.
- Both merged-manifest/source privacy audits passed. Offline has no Internet/capture permissions. Live retains only its existing Internet, foreground-service, mediaProjection and notification permissions, plus AndroidX's signature permission. No microphone permission was added.
- Both debug APK signatures verified; live package metadata reports `in.kavach.live`, version 0.3.0, min SDK 31, target SDK 35.

Build and server logs are under `evidence/muse-android-build.txt` and `evidence/muse-server-tests.txt`. Machine-readable counts and hashes are in `evidence/verification.json`. Earlier evidence is historical; see [0.2 verification](VERIFICATION_V0_2.md).

## Artifacts

**Live:** `app/build/outputs/apk/live/debug/app-live-debug.apk`

SHA-256: `c2a55561da87ce3f86ada124ca49cef4bb97314b08ddb68c162ba08841385970`

**Offline:** `app/build/outputs/apk/offline/debug/app-offline-debug.apk`

SHA-256: `27c7f4b0c8cdce28fe2919e6bee7974605d83a0cb1084998c3d792a2fb19ac53`

Both are debug-signed arm64 development builds. Use the matching 0.3.0 backend/app; old Llama responses are rejected. The earlier unflavored APK is obsolete.

## Remaining checks

Follow [Live setup](LIVE_VIDEO.md), add a real key, then run `python -m live_server.smoke` for one synthetic API request and its actual provider receipt. Verify a phone session with synthetic screen content before using personal data. Real access/billing, provider contract compatibility, model quality, latency, notification behavior and capture lifecycle still need validation. `GET /health` reports configuration, not successful upstream inference.

The API model's ability to generate text is not a guarantee of reliable scam detection. The live profile reads visible English text only, cannot hear calls, and treats quotes as unverified model-read text. A single successful API request would still not establish accuracy. Privacy controls do not establish zero provider retention. Stopping cannot retract an image already sent.

The earlier local-text ExecuTorch privacy gate remains enabled. Patched native runtime and actual local model inference remain unverified. Scripted Samples are explicitly demos, not Muse Spark predictions.
