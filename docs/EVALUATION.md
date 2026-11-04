# Evaluation

## Dataset, not measured accuracy

`evaluation/held_out.jsonl` contains **48 synthetic cases**: 20 English, 20 Hindi, eight separately reported experimental Hinglish. Each language subset is balanced between suspicious and benign cases. Every row has a stable ID, group ID, language, expected risk/pattern, rationale, split and synthetic provenance. All labels are **provisional and awaiting independent human review**.

The English/Hindi translations of each scenario share a group ID and remain together in held-out evaluation. These cases are separate from the 12 UI fixtures in `app/src/main/assets/demos.json`; no held-out example is placed in the inference prompt. Do not tune on this held-out file. If cases become development examples, move their whole related group and create a new held-out set.

Coverage includes digital-arrest escalation, threats without police keywords, secrecy, money demands, OTP/account linking, remote access, quoted scam warnings, ordinary unknown contacts, legitimate deliveries and support, benign police discussions, job applicants and prompt injection. This is a small authored synthetic suite, not a representative sample of real fraud.

## Scoring actual runs

Positive risk classes are fixed before scoring: `strong_indicators` and `some_indicators`. Negative is `no_clear_indicators`; `insufficient_evidence` is an abstention. Missing predictions, cancellations and `unable_to_assess` are counted separately. In addition to recall on assessed cases, the scorer reports recall against **all positive cases**, so excluding errors/abstentions cannot silently improve apparent recall. All metrics include numerators and denominators. Zero denominators produce null, not zero or fabricated perfect scores.

Record one prediction per ID from an actual local model run:

```json
{"id":"eval-01-en","status":"assessed","risk":"strong_indicators","suspected_pattern":"digital_arrest"}
```

For failed runs, record `status: unable_to_assess`; do not replay sample results. Capture provenance, version, device and performance separately. Run:

```sh
python3 tools/evaluate.py /path/to/actual-predictions.jsonl > /path/to/report.json
```

The report has overall and language-specific precision, recall, false-positive rate, counts, coverage and mismatch rows for failure review. Pattern labels are stored for human review but pattern accuracy is not currently scored. The app intentionally has no conversation/result export mechanism; a developer harness for bulk device inference remains to be added after real-model smoke testing. Do not enable persistence in the product merely to gather an evaluation dataset.

## Actual results so far

No real-model run has been performed; **no precision, recall, false-positive rate, latency or memory figures are available**. See `VERIFICATION.md` for software-test results. Unit tests validate boundaries and scoring mathematics, not scam-detection accuracy. The scripted samples are UI demonstrations, not evidence of learned behavior.

Before any demo claiming local AI protection, run the setup smoke checks and all held-out examples on the actual phone. Report model/export hashes, cold-load duration, inference latency separately from loading, OS/ABI, peak observed memory, all abstentions/errors and representative false positives/negatives. Set speed targets only after recording a baseline. Native stop responsiveness and on-device logging must also be checked. Synthetic evaluation cannot establish real-world protection.

## Live video-call feature

The 48-case dataset above evaluates the text path only. Live protocol/HTTP/consent tests use synthetic fixtures and an explicit server test double; they do not measure vision-model accuracy. Real Meta Vision inference and device capture remain unverified. See [live setup and validation](LIVE_VIDEO.md) and [verification record](VERIFICATION.md).
