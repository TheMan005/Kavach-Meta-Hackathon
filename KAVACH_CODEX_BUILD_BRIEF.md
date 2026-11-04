# Kavach Android Prototype Build Brief

Prepared for Sampreet Byali on 2 November 2026. This file is an implementation specification and decision summary for Codex in VS Code. It is not application source code. Kavach is a working project name, with no claim of Meta or government endorsement.

## How to use this file

Save this file in the root of the project folder you open in VS Code. Give Codex this instruction:

> Read KAVACH_CODEX_BUILD_BRIEF.md and any repository instructions. Implement the Android prototype described here. Start by inspecting the workspace and available Android tooling, then make a short plan and begin coding. Continue through implementation, meaningful tests, and a debug build where the environment permits. Make routine implementation decisions yourself. Preserve unrelated work. Document genuine blockers without fabricating functionality or test results. Keep the code readable so I can review and improve it.

## Product objective and boundaries

Build an opt-in Android companion that helps a person recognize possible scams in text they deliberately share or paste. Prioritize Indian digital-arrest coercion: impersonated authority, threats, isolation, secrecy, and urgent payment demands. Explain the indicators using exact evidence from the supplied conversation.

The user's longer-term goal is native protection within WhatsApp, Instagram, and Facebook. The prototype must demonstrate a reusable local detection component and an honest path to that future integration. Public Meta messaging APIs are scoped to businesses, professional accounts, or Pages; they do not provide arbitrary access to personal inboxes. Integration into those personal-chat interfaces requires Meta cooperation.

Meta has already announced an optional on-device WhatsApp Scam Alert feature. Differentiate this prototype through Indian coercion patterns, evidence-linked explanations, careful uncertainty handling, and evaluated multilingual behavior. Do not claim to invent private scam detection.

The user explicitly authorizes Codex to write this project. The Meta-only restriction applies to the application's learned models and AI services. Android, Kotlin, Gradle, test libraries, and ordinary non-AI dependencies may be used. Do not introduce another vendor's LLM, OCR, speech-recognition, embedding, translation, or AI moderation service into the application.

## Implement this MVP

1. Native Android app with a minimal, aesthetic dark theme.
2. Explicit paste and Android text-sharing entry points.
3. An editable preview of exactly what will be analyzed.
4. Consent before each check; no analysis merely because a share Intent arrived.
5. Real local inference using Meta Llama 3.2 Instruct with ExecuTorch.
6. A result showing risk category, suspected pattern, verified evidence, limitations, and a practical next step.
7. English and Hindi as initial evaluation targets. Mark Hinglish as experimental until measured.
8. Private model import and model-status screens.
9. Clearly marked synthetic sample scenarios for demonstrating the interface.
10. Tests, reproducible build instructions, and a debug APK if the environment supports building one.

Do not implement live personal-call monitoring, notification reading, AccessibilityService scraping, contact-book collection, personal-inbox syncing, background clipboard monitoring, or automatic blocking/reporting. Do not present phone-number ownership, caller identity, deepfake detection, or official-document authentication as capabilities of this MVP. An unknown sender is a context flag, not proof of fraud; the share payload may not include sender metadata at all.

Keep speech, screenshots, cloud inference, business bots, and real platform integration outside the first milestone. Define extension points only when useful; avoid empty feature screens and speculative infrastructure.

## Initial technical decisions

Use Kotlin, Jetpack Compose, Material 3, coroutines, ViewModel state, and a small repository structure. Prefer simple constructor injection over a dependency-injection framework. A database and backend are unnecessary for the ephemeral MVP.

Target a modern arm64 Android phone for the demo. Android 12 and later is a reasonable initial support target; verify the actual minimum OS and ABI requirements of the selected runtime before pinning configuration. Do not inherit Android 7 compatibility from any unrelated project. Use a compatible stable Android Gradle Plugin, Gradle, Kotlin, JDK, compile SDK, and target SDK combination verified from official documentation and the installed environment. Pin versions and document the choice; do not invent version numbers or use dynamic dependency versions.

Start with Meta's quantized Llama 3.2 1B Instruct deployment path. Allow a compatible 3B variant later if the device has adequate memory and the evaluation justifies it. Use ExecuTorch's current official Android Llama example to establish the exact export format, runtime API, tokenizer, operators, and prompt template. Do not assume that a downloaded Hugging Face checkpoint is directly loadable by Android, or that all files named .pte are interchangeable.

ExecuTorch is a Meta-developed project within the PyTorch ecosystem, with industry contributors. Do not describe all of its dependencies as exclusively owned by Meta.

Before implementing the adapter, verify these sources:

- Meta Llama quantized model and deployment guidance: https://dev.meta.ai/llama/docs/model-cards-and-prompt-formats/llama3_2
- Official Llama model card: https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md
- ExecuTorch repository: https://github.com/pytorch/executorch
- ExecuTorch documentation: https://docs.pytorch.org/executorch/

Follow the official example links from those sources. Record the exact runtime version, model/export provenance, tokenizer format, and relevant upstream commit or example in the model setup guide.

## Architecture and implementation seams

Keep UI, inference, validation, and advice selection separable so I can improve the model without rewriting the app. Suggested package groups are ui, domain, inference, models, and input. Use one Android module initially unless a second module solves a concrete build problem.

The following names describe proposed application interfaces, not verified upstream SDK classes:

| Component | Responsibility |
| --- | --- |
| ShareInputHandler | Parse and validate incoming text Intent data |
| AssessmentViewModel | Own input, explicit consent, execution state, cancellation, and results |
| InferenceEngine | Accept bounded conversation text and return raw inference output with engine metadata |
| ExecuTorchLlamaEngine | Load the verified model/tokenizer, format the prompt, and execute local inference |
| AssessmentParser | Parse a strict structured response with bounded field sizes |
| EvidenceValidator | Verify quoted evidence and derive highlighting positions in application code |
| AdviceCatalog | Map validated indicator categories to reviewed static advice |
| ModelRepository | Manage model files, compatibility metadata, import, replacement, and deletion |
| DemoScenarioRepository | Supply explicitly scripted results for built-in synthetic scenarios only |

Use a state machine such as Input, ConsentRequired, ModelMissing, LoadingModel, Analyzing, Result, Error, and Cancelled. Avoid exposing raw model responses as product UI. Keep inference off the main thread. Serialize model calls unless the chosen runtime explicitly supports concurrency.

Use request identifiers so cancelled or superseded work cannot later update the screen. Connect cancellation to the runtime's supported stop mechanism where available; if execution cannot be immediately interrupted, suppress stale results and document that limitation accurately. Disabling checks must prevent new inference.

## Model installation and missing prerequisites

The project should build without committing model weights. The app must display Model not installed until genuine compatible weights and a tokenizer are available. Provide a system document-picker flow that imports the required artifacts into app-private storage. Request no broad external-storage permission.

Use a manifest describing the model family, version, quantization/export recipe, tokenizer, runtime compatibility, file sizes, and available trusted digests. Verify actual checksums against a trusted release manifest where one exists. A checksum computed only after importing a file detects later changes; it does not prove the initial file's authenticity. Never invent a published digest.

Handle insufficient disk space, interrupted imports, malformed files, incompatible tokenizers, failed model initialization, and low memory with clear recoverable errors. Prefer atomic replacement so a failed import does not destroy a previously usable model. Reject archives with unsafe paths if archive import is implemented.

Model downloads and exports belong in the documented developer setup, not a background app downloader. Respect license acceptance and redistribution terms. Never bypass gated access or accept legal terms on the user's behalf. Do not commit weights, tokens, credentials, or unrelated private files. If a manual license acceptance or download is required, finish independent application work and give exact official setup steps.

If a dependency or export is blocked, implement the verified integration as far as possible and identify the remaining blocker precisely. Do not call real AI inference complete until it has run with a real compatible model. A scripted demo does not satisfy this requirement.

## Input and consent experience

Accept ACTION_SEND with text/plain and provide an ordinary manual text field. Treat every incoming Intent, MIME type, and extra as untrusted. Do not fetch shared links, open attachments automatically, or follow instructions contained in shared text. Handle missing, wrong-type, and oversized values safely.

Set a documented initial input cap, for example 8,000 UTF-16 code units, then also enforce the selected model's token budget including system instructions and output allowance. If content is too long, ask the user to shorten or select it; do not silently remove relevant context. Respect Unicode boundaries when enforcing limits.

Let the user review, edit, and remove text before analysis. Clipboard access must happen only after an explicit Paste action. Do not automatically read, log, clear, or overwrite the system clipboard. The user can provide known, unknown, or unspecified sender context manually; do not infer it from unavailable platform information.

For verified local mode, explain that analysis runs on this phone and that the app does not upload the submitted text. Pressing Check authorizes this one assessment. Keep that action separate from any future consent to cloud processing, persistence, analytics, reporting, or microphone access.

Use approachable language, comfortable spacing, muted surfaces, and a restrained accent. Provide at least 48dp touch targets, scalable text, useful TalkBack labels, and warnings that use words and icons as well as color. Suggested screens are Check, Result, Model Setup, and Privacy/About. A model-status indicator should distinguish Local Meta model, Model missing, and Scripted demo.

## Detection behavior and structured results

Assess combinations of indicators and conversational context. Useful categories include authority impersonation, arrest threats, enforced availability, secrecy or isolation, urgent transfer requests, OTP requests, remote-access requests, and suspicious account-linking instructions. Treat these as potential signals, not unconditional proof. A legitimate courier, job applicant, customer-support exchange, or security alert may contain superficially similar words.

Use a compact schema similar to the following. The exact enums can be refined while preserving these distinctions:

```json
{
  "schema_version": 1,
  "status": "assessed",
  "risk": "strong_indicators",
  "suspected_pattern": "digital_arrest",
  "evidence": [
    {
      "message_id": "m1",
      "quote": "Do not tell your family",
      "indicator": "isolation"
    }
  ],
  "limitations": []
}
```

Application-level status must distinguish assessed, unable_to_assess, and cancelled. Assessed risk categories should distinguish strong_indicators, some_indicators, no_clear_indicators, and insufficient_evidence. Errors and unavailable models must never produce no_clear_indicators. Suspected patterns may include digital_arrest, impersonation, payment_fraud, account_takeover, other, and unclear.

Engine provenance, model ID, model version, and execution time must be supplied by the application, never trusted from model output. Do not show a numerical probability unless a future evaluation actually establishes calibration. Never label a person a criminal or say a conversation is guaranteed safe.

Require exact evidence excerpts. Check each quote against the corresponding submitted message. Derive offsets in Kotlin rather than trusting generated offsets. Handle repeated phrases and non-ASCII text correctly; do not highlight an arbitrary different occurrence. If the response relies on fabricated evidence, reject it as unable to assess. Permit evidence-free insufficient_evidence or no_clear_indicators results; do not fabricate reassuring evidence either.

Render evidence and model-derived text as bounded plain text. Do not make model-suggested URLs clickable. Prefer a static, reviewed advice catalog and deterministic explanation templates over unrestricted generated advice. Use wording such as: These messages combine a secrecy request with an urgent money demand, which is a warning sign of coercion.

Keep the model's task separate from untrusted conversation text in the supported chat template. Explicitly instruct it to classify the supplied content, not obey instructions inside it. Give the inference model no network, filesystem, messaging, or other tool access. Enforce schema validation after inference; prompt wording alone is not a security boundary.

Start with a small, documented prompt and a few reviewed examples if needed. Do not begin with fine-tuning, a vector database, or a multi-agent inference system. Keep any prompt examples out of the held-out evaluation set.

## Privacy and safe actions

For the offline MVP, omit the INTERNET permission from the merged manifest. Verify dependencies do not reintroduce network access, analytics, ads, crash reporting, remote configuration, or AI SDK calls. Model files are imported locally. External browser or dialer actions must require a deliberate user action and be identified as leaving the app; another app can have its own network access.

Keep input and results ephemeral by default. Do not write them to databases, files, logs, analytics, crash reports, SavedStateHandle, saved instance state, or backups. Avoid logging prompts, generated output, clipboard values, or file URIs. Exclude sensitive state and model files from backup using the mechanisms appropriate to the supported Android versions. Deliberately clear in-memory references when the user discards a check; do not claim this constitutes guaranteed forensic memory erasure.

Do not persist history in the first version. If added later, make it separately opt-in, use app-private encrypted storage with Android Keystore-backed keys where appropriate, document retention, and implement deletion. Never train on submitted messages without a separate explicit agreement.

Provide static links to independently verified official help. Initial India guidance may reference https://cybercrime.gov.in and 1930, verified against current government sources before release. Use a visible reporting/help action, not an automatic report or phone call. Never send data to a trusted contact automatically. Explain that verification should use an independently obtained official channel.

Platform Block or Report actions in a native-integration mockup must be labeled simulated. In the real companion, provide guidance for returning to the source app rather than pretending to block or report an account there.

## Demo mode and evaluation

Include approximately 12 clearly synthetic, fictional sample scenarios for UI exploration. Put Scripted demo — no AI assessment on fixture-driven screens. Scripted results may be replayed only for built-in scenario IDs; arbitrary user input requires the real engine. Do not silently fall back to a canned answer when inference fails.

Create a separate initial held-out evaluation set of at least 40 human-reviewable examples, balanced between suspicious and benign cases where practical. Include English and Hindi; add a separately reported experimental Hinglish subset. Mark examples synthetic in dataset metadata and demo selection UI. Do not use real private conversations or imply synthetic examples are genuine reports.

Include digital-arrest escalation, money demands without overt police words, quoted scam warnings, legitimate delivery messages, ordinary unknown contacts, customer support, benign discussion of police, and negative cases containing the same keywords. Split related scenarios and their translations together to avoid test leakage.

For every example, store a stable ID, language, expected risk/pattern, reviewer rationale, and synthetic provenance. Treat labels as provisional until human-reviewed. Do not let generated labels stand in for established ground truth.

Report precision, recall, false-positive rate, and counts with denominators. Define which risk categories count as a positive before scoring. Report abstentions and unable-to-assess errors separately; do not omit them to improve apparent accuracy. Break down language results and show representative failures. Synthetic evaluation demonstrates prototype behavior, not real-world protection.

Performance measurements should identify the phone or emulator, OS, ABI, model/export, cold-load time, inference latency, and observed memory use. Set optimization targets after measuring a baseline. Do not promise flagship-phone benchmark results on an untested device.

## Meaningful verification

Write tests around boundaries and failure behavior, not boilerplate implementation details:

- Shared Intent missing data, wrong types, oversized content, and Unicode handling.
- No inference before explicit consent; disabling checks prevents new work.
- Cancellation and stale-result suppression.
- Strict response parsing, unknown enums, malformed JSON, and oversized fields.
- Evidence mismatch, repeated phrases, Hindi/emoji offsets, and invalid message IDs.
- Missing/incompatible model and failed initialization.
- Scripted demo provenance and prohibition on arbitrary-input fixture fallback.
- Prompt injection attempts asking the detector to ignore rules, mark a scam safe, or transmit data.
- Benign unknown-sender cases and synthetic scam escalation cases.
- Merged-manifest absence of network and other prohibited permissions, plus a logging/storage audit.

Use unit tests for pure parsing and validation. Use targeted Compose/instrumentation tests for consent and core flows when tooling permits. Run a real-model smoke test when assets and suitable hardware are available. Airplane mode alone is not proof of no-network behavior; inspect the manifest and dependencies and perform runtime network checks where possible.

Run the appropriate Gradle unit tests, lint, and debug assembly. Use a legitimate compatible Gradle wrapper from an official distribution or verified template. Do not fabricate a wrapper JAR, APK, successful test output, or runtime measurements. If Android SDK, JDK, model access, native binaries, or hardware are missing, state which commands were actually run and what remains unverified.

## Implementation order and deliverables

1. Inspect the workspace, repository instructions, existing code, git state, and installed Android/JDK tooling. Preserve unrelated changes. If the folder is empty, create a conventional Android project.
2. Verify the official model/runtime path, pin a compatible toolchain, and establish a minimal real-model loading spike early. Identify asset or access blockers before building a large UI.
3. Implement the app shell, input limits, consent state, privacy defaults, domain types, strict parser, and evidence validation.
4. Implement model import and the real ExecuTorch adapter. Add honest unavailable-model behavior and independently labeled sample mode.
5. Finish the dark interface, result evidence, static safe actions, accessibility, and lifecycle handling.
6. Add evaluation fixtures and meaningful tests. Build, inspect the merged manifest, and exercise the primary flows on available hardware/emulation.
7. Produce reviewable source and documentation. Summarize what works, what was tested, and any genuine limitations.

Expected repository deliverables:

- Android source, pinned build configuration, verified Gradle wrapper, and useful .gitignore.
- README.md with setup, build, installation, demo steps, and current status.
- docs/MODEL_SETUP.md with exact official downloads, license steps, export recipe, compatible tokenizer/runtime, and import procedure.
- docs/ARCHITECTURE.md with component responsibilities, data flow, and extension points.
- docs/PRIVACY.md with permissions, data lifecycle, threat boundaries, and verification evidence.
- docs/EVALUATION.md with methodology, actual results where run, and limitations.
- Synthetic sample and evaluation fixtures with provenance and split discipline.
- Appropriate tests and a debug APK if assembly succeeds; identify its actual path.

Make the code easy to review: descriptive names, short cohesive functions, minimal dependencies, and comments explaining non-obvious privacy or inference decisions. Do not build a generic agent framework or cloud backend for this task. Do not publish, deploy, create a remote repository, or send messages as part of this implementation request.

If a required external prerequisite cannot be completed, finish independent work and leave a specific, minimal next action. Continue once that prerequisite is available; do not portray the complete prototype as working until actual inference and the main flow are verified.

## Optional later milestones

After the text MVP works, consider user-selected voice-note import using Meta Omnilingual ASR. Its current reference implementation is not automatically an Android deployment solution; benchmark and port it separately. A staged local voice demonstration can run on a development machine if that processing boundary is disclosed.

Screenshot understanding or supplied-audio transcription through Meta Model API may be explored in a separate opt-in online variant. Verify current Muse capabilities, account access, and privacy terms first. Keep API keys off the client and use synthetic inputs for demonstrations. Standard API services excluding model training do not imply zero retention; do not advertise cloud processing as on-device private inference. Keep the offline APK free of this path.

An actual WhatsApp Business, Instagram professional-account, or Messenger Page bot would be a separate explicitly disclosed service receiving content the user intentionally sends to it. It cannot monitor the user's other personal conversations. Native real-time personal-call protection requires cooperation and access inside the calling application; ordinary microphone consent is insufficient.

## Background sources and event context

These sources supported the planning discussion on 2 November 2026. Recheck current documentation when implementing; use primary sources, not remembered API signatures.

- Hackathon public event and schedule: https://dev.meta.ai/events/global-hackathon
- User-supplied attendee details page: https://cerebralvalley.ai/e/meta-ai-developer-hackathon/details
- Existing WhatsApp Scam Alert: https://engineering.fb.com/2026/08/12/security/how-were-building-scam-alert-whatsapp/
- ExecuTorch in Meta apps: https://engineering.fb.com/2025/07/28/android/executorch-on-device-ml-meta-family-of-apps/
- Android shared-data handling: https://developer.android.com/develop/ui/compose/sharing/receive
- Android audio-input restrictions: https://developer.android.com/media/platform/sharing-audio-input
- Meta model platform: https://dev.meta.ai/docs/overview
- Meta API data commitments: https://dev.meta.ai/legal/commitments
- Meta API retention controls: https://dev.meta.ai/help/policies-and-privacy/zero-data-retention
- Meta Omnilingual ASR: https://github.com/facebookresearch/omnilingual-asr
- Indian government digital-arrest advisory: https://www.pib.gov.in/Pressreleaseshare.aspx?PRID=2020570&lang=2&reg=48

The public event aligns with the Build for Your World theme. The participant-only details were not accessible in the planning session. Do not assume that this document verifies every submission requirement, model restriction, or video format. Verify the participant instructions before submission. Hackathon timing must not cause false claims about implementation or testing.
