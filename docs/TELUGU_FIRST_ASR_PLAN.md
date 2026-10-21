# Telugu-first ASR: engineering gates

**Goal:** make Wahtuh Pho genuinely useful for Telugu and Telugu-English dictation in Telangana and Andhra Pradesh. Accuracy is the primary product metric, not a marketing claim.

## Define the target honestly

- WER = 1.0 (100%) is poor. WER = 0.01 (1%) is an exceptionally ambitious target for spontaneous speech, dialects, noise, names, and code-switching.
- Pursue WER ≤ 1% on a clearly defined, narrow evaluation set first; do not promise it until measured on a held-out, representative test set.
- Report CER too, especially for Telugu orthography. Every result must name the dataset, sample count, normalization, engine/model version, and whether it ran locally or in the cloud.

## Baseline from current repository evidence

- Whisper tiny/base/small performed unacceptably on the recorded Telugu FLEURS experiment (117–128% mean WER).
- Vosk Telugu is an interim offline fallback, not the quality endpoint.
- Android currently uses sherpa-onnx for Whisper tiny and Vosk for Telugu. The Rust workspace is separate and is not yet the Android inference path.
- Koyal is the priority candidate for a Telugu-specific local model. Do not assume a Hugging Face checkpoint can be loaded by sherpa-onnx: confirm architecture, export path, tokenizer, license, and Android-compatible ONNX support first.

## Target architecture

1. **Audio front end:** 16 kHz mono PCM, clipping/DC checks, voice activity detection, robust endpointing, and optional noise suppression. Preserve audio until transcription succeeds.
2. **Candidate engines:** Koyal Telugu-specific model; sherpa-onnx only with an actually compatible exported model; measured English recognizer; opt-in cloud adapters. Each engine returns raw text, language, model/version, duration, latency, and explicit errors.
3. **Routing:** respect user-selected language. Otherwise use conservative language/script detection. Do not route a Telugu sentence to English ASR merely because it contains an English name. Benchmark utterance- and chunk-level routing for Tenglish; never silently translate.
4. **Text restoration:** reversible Telugu-aware normalization, punctuation, number normalization, personal names, and local vocabulary. Keep raw ASR output unchanged. Score raw and cleaned text separately so dictionary fixes cannot hide recognition errors.
5. **Consensus only if measured:** compare candidates on validation data and calibrate confidence before using it. Do not blindly merge transcripts. Offer alternatives or user-approved cloud re-transcription when uncertain.
6. **Privacy:** offline by default. Cloud requires explicit opt-in, visible upload state, no silent fallback, bounded retention, and secure credentials.

## Evaluation protocol (release-blocking)

- Use public licensed corpora (including FLEURS) plus a consented field corpus from Telugu speakers across Telangana and Andhra Pradesh.
- First useful comparison: at least 100 clips each for Telugu, English, and Tenglish; expand across speakers, dialects, age groups, devices, code-switch ratios, and noisy environments.
- Split by speaker, not random clips. Lock the test set before tuning.
- Include spontaneous dictation: names, towns, amounts, dates, technical English terms, colloquial Telangana Telugu, coastal Andhra Telugu, Rayalaseema Telugu, and user-preferred Telugu or Latin script.
- Report micro WER, CER, exact sentence match, code-switch span accuracy, p50/p95 latency, peak memory, model size, failure rate, and cloud cost.
- Apply the same NFC + lowercase + punctuation/symbol removal + whitespace normalization to all candidates. Document number normalization separately; never normalize away meaningful word errors.
- Save per-clip raw hypotheses. Run every engine against the same audio. No cherry-picked examples or fabricated measurements.

## Execution order

1. **Benchmark harness:** use `tools/score_asr.py` to score JSONL run records against `docs/eval_manifest.json`. Only add Koyal results after actual inference.
2. **Koyal feasibility spike:** verify the model card, checkpoint format, license, tokenizer, and export/runtime support. Benchmark on the existing Telugu clips. If export is unsupported, document the conversion/runtime path or keep Koyal as a cloud/server challenger rather than falsely claiming on-device support.
3. **sherpa-onnx integration:** add a separate engine only after a compatible exported model and Android smoke test exist; keep current engines available as fallbacks.
4. **Cloud head-to-head:** compare providers on identical licensed/consented clips with cost caps and explicit key validation. Cloud is a choice, never a hidden fallback.
5. **Phone validation:** install a test APK on ARM64 Android; verify cold/warm latency, memory, airplane-mode behavior, microphone permissions, text insertion, and real Telugu/Tenglish speech.
6. **Ship gate:** no engine becomes default until held-out accuracy, latency, crashes, memory, and privacy behavior are acceptable.

## Commitment

WER ≤ 1% is an optimization target, not a guarantee. A system can score well on read benchmark speech and still fail real Telugu dictation; held-out speakers and natural code-switching are essential.
