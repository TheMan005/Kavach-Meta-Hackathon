# Model setup and real inference status

**Real inference has not been run.** No model weights, accepted model license, compatible exported artifacts, or Android hardware were supplied. The default build deliberately locks real-text inference because the published native runner logs sensitive text. It can show the UI and 12 scripted demos without weights or keys. Do not present it as a working AI detector yet.

## Verified upstream contract (2 November 2026)

- Maven artifact: `org.pytorch:executorch-android:1.5.1`, confirmed in [Maven Central](https://repo.maven.apache.org/maven2/org/pytorch/executorch-android/1.5.1/). Includes arm64 runtime; this app restricts packaging to arm64-v8a and Android 12/API 31+, above upstream's API 23 minimum.
- Runtime tag: [v1.5.1](https://github.com/pytorch/executorch/tree/v1.5.1), commit `3b60683923245cf472b7323426920e15623ba361`.
- Actual API inspected in the published `executorch-android-1.5.1-sources.jar`: `org.pytorch.executorch.extension.llm.LlmModule(modelPath, tokenizerPath, temperature)`, `load(): Unit` (throws on failure), `generate(prompt, config, callback)`, `stop()`, `close()`. Do not copy older documentation that compares `load()` with zero.
- `LlmGenerationConfig.maxNewTokens` exists but this version's JNI adapter does not forward it. The app additionally bounds callback count/output size and passes `seqLen` no larger than the model context. Callback chunks may merge partial UTF-8 tokens; the context bound, not callback count, is the hard native token ceiling.
- Official [Android example](https://github.com/meta-pytorch/executorch-examples/blob/975c6d48b46b1ebc9e271c6d0740a4778e4a8ad6/llm/android/LlamaDemo/docs/delegates/xnnpack_README.md), commit `975c6d48b46b1ebc9e271c6d0740a4778e4a8ad6`, establishes XNNPACK QAT+LoRA INT4 EO8 export and Llama 3 tokenizer pairing. It is a separately versioned example; the recipe below is adapted to a larger context and must still be tested with this pinned runtime.
- [Meta quantized-model guidance](https://dev.meta.ai/llama/docs/model-cards-and-prompt-formats/llama3_2) and [model card](https://github.com/meta-llama/llama-models/blob/main/models/llama3_2/MODEL_CARD.md) define the model/license and Llama 3 chat template. Only Meta model artifacts are supported.

## 1. Obtain model access yourself

Request Llama 3.2 **1B Instruct QLoRA INT4 EO8** through the [official Meta model download flow](https://www.llama.com/llama-downloads/) or the model repository linked by Meta. Read and accept its license yourself. Download the original checkpoint (`.pth`), matching parameters, and original **Llama 3 tiktoken `tokenizer.model`**. Do not use an arbitrary `.pte`, a Hugging Face safetensors checkpoint directly, tokenizer.json, or a Llama 2 SentencePiece tokenizer.

The upstream example links community exports for convenience. Their existence does not establish compatibility with 1.5.1 or this app's 4096-token minimum. No publisher digest was verified here; no trusted digest is invented. Use an export from the pinned checkout and record the source checkpoint revision. A 3B import is intentionally rejected until evaluated.

## 2. Export locally

Use a separate development checkout and Python environment; exporting is not done on the phone. Follow the pinned repository's platform/Python prerequisites, initialize submodules, then install its requirements:

```sh
git clone --branch v1.5.1 --recursive https://github.com/pytorch/executorch.git
cd executorch
python3 -m venv .venv
. .venv/bin/activate
./install_executorch.sh
./examples/models/llama/install_requirements.sh
```

Adapted from the official QAT+LoRA example (paths are yours):

```sh
python -m extension.llm.export.export_llm \
  base.model_class=llama3_2 \
  base.checkpoint=/absolute/path/to/checkpoint.pth \
  base.params=/absolute/path/to/params.json \
  quantization.use_qat=True base.use_lora=16 \
  model.use_kv_cache=True model.use_sdpa_with_kv_cache=True \
  backend.xnnpack.enabled=True model.dtype_override=fp32 \
  backend.xnnpack.extended_ops=True \
  base.preq_mode=preq_8da4w_out_8da8w base.preq_group_size=32 \
  export.max_seq_length=4096 export.max_context_length=4096 \
  "base.preq_embedding_quantize='8,0'" \
  'base.metadata="{\"get_bos_id\":128000, \"get_eos_ids\":[128009,128001]}"' \
  export.output_name=model.pte
```

Use `8192` for both context parameters only if your phone has adequate memory. The export recipe has been checked against the pinned configuration names, but **has not been executed**. If the exporter emits separate external tensor files, produce a single-file export supported by this profile before import; this prototype does not import supplementary files. Do not rename or convert the original tokenizer contents. Llama 2's `llama2c` conversion is not appropriate here.

## 3. Build a private native runtime

Inspection of `extension/llm/runner/text_llm_runner.cpp` at the pinned commit found:

1. Generated pieces printed to stdout independently of `echo=false`.
2. The entire prompt logged if tokenization fails.

The stock artifact therefore cannot meet this app's no-conversation-logging promise. Real inference is gated by `BuildConfig.NATIVE_PRIVACY_REVIEWED`; the flag is false for the Maven dependency. There is no UI bypass.

From the Kavach directory, apply the provided narrowly scoped patch to the separate ExecuTorch checkout:

```sh
python3 tools/patch_executorch_privacy.py /absolute/path/to/executorch
```

It requires the exact upstream commit, removes token stdout printing, and adds `-DEXECUTORCH_ENABLE_LOGGING=OFF` to the Android library build. Review its diff. Install the SDK/NDK and native prerequisites described by the official example, then in that checkout:

```sh
export ANDROID_NDK=/absolute/path/to/android/ndk/27.1.12297006
export ANDROID_ABIS=arm64-v8a
export BUILD_AAR_DIR=/absolute/path/to/aar-out
mkdir -p "$BUILD_AAR_DIR"
sh scripts/build_android_library.sh
```

Upstream native build may need adjustment for your host; it has not been executed here. Use synthetic canary text to audit all logcat buffers/stdout on the device (including tokenizer failure and generation failure), confirm the merged manifest, and review the AAR dependencies. Then copy the reviewed AAR to `app/libs/executorch-private.aar` and rebuild Kavach. Its presence selects the local dependency and unlocks inference. This developer-controlled filename is a build convention, not cryptographic proof of privacy. Do not copy an unpatched Maven AAR into that path.

## 4. Prepare import manifest

```sh
python3 tools/model_manifest.py \
  --model /absolute/path/to/model.pte \
  --tokenizer /absolute/path/to/tokenizer.model \
  --context 4096 --output /absolute/path/to/manifest.json
```

Review the emitted metadata against your actual export. It records runtime, recipe, commit, file lengths and SHA-256. **Locally computed digests establish integrity only, not initial authenticity.** If an authoritative publisher provides digests, independently compare them before generating/importing this manifest.

Transfer the three files to your phone through a mechanism you choose. In Kavach → Model, select manifest, model and tokenizer using the system picker, then Import. A document provider may itself use a network; choose local files. No broad storage permission or background downloader is used. The app bounds sizes, checks available disk, hashes both artifacts, probes native initialization, then atomically switches the active-model pointer. A failure preserves the current model. Interrupted stages are cleaned on a subsequent successful import/delete. Private data is excluded from backup.

## 5. Prove inference before claiming completion

On an arm64 Android 12+ phone, with the patched runtime and approved artifacts:

1. Start with synthetic text; disable checks and verify no execution.
2. Share text; verify editable preview with no automatic execution.
3. Review & check → approve this assessment. Confirm real provenance and exact evidence.
4. Cancel while loading and generating; submit another excerpt and verify stale results cannot appear.
5. Test malformed model, mismatched tokenizer, insufficient storage/memory, and process restart.
6. Capture actual outputs for the held-out dataset, including failures; never substitute scripted results.
7. Record phone, OS, ABI, checkpoint/export hashes, cold load, generation time, and observed memory. App result elapsed time currently includes hashing/loading/generation; it is not a pure inference benchmark.

Loading/hashing may not stop immediately. Runtime `stop()` is called; request identifiers suppress stale completion. Each request closes its module to avoid retaining a conversation KV cache across checks. Clearing references is not guaranteed forensic memory erasure.
