# Architecture

## Runtime flow

```
mic / WAV
  └─ AudioCapture (16 kHz mono float)
      └─ SpeechAudio.trim + utterances (silence split)
          └─ DictationController
              ├─ AsrManager (local)
              │   ├─ WhisperEngine (sherpa-onnx OfflineRecognizer)
              │   ├─ VoskEngine (Vosk Recognizer)
              │   └─ HybridEngine (Whisper language detect → Vosk for Telugu)
              ├─ TranscriptionRouter (local-only chokepoint)
              │   └─ CloudTranscriber (Groq / OpenAI / Gemini)
              ├─ TextPipeline (punctuation, fillers, dictionary)
              ├─ HistoryStore (JSONL, raw + final + edits)
              └─ DictationResult → listener (MainActivity, IME, bubble)
```

## Components

| Path | Responsibility |
|---|---|
| `asr/` | Model install, Whisper, Vosk, hybrid routing, `AsrManager` singleton |
| `audio/` | `AudioCapture` (AudioRecord) and `Wav` read/write |
| `speech/` | VAD/trim/utterance splitting over RMS frames |
| `dictation/` | Session state machine and listener contract |
| `cloud/` | Multipart/JSON transcription clients and the privacy router |
| `text/` | Deterministic pipeline, dictionary, reversible edits, WER/CER |
| `data/` | Preferences, Keystore-backed keys, JSONL history |
| `service/` | IME keyboard, floating bubble FGS, accessibility insertion |
| `ui/` | Palette and view helpers |
| `diag/` | Self-tests, benchmarks, mic smoke test |

## State machine

`IDLE → PREPARING → RECORDING → TRANSCRIBING → IDLE`, with `ERROR` on failure. A monotonically increasing session token rejects late results from cancelled runs. Starting a session while another is busy is refused.

## Threading

- UI callbacks are posted to the main looper.
- Model loading, capture finalisation, ASR, network calls, and history writes run on a single-thread executor in the controller.
- The bubble owns a foreground service; the controller is a process singleton so the IME, bubble, and activity share one session.

## Text policy

1. Normalise whitespace.
2. Expand spoken punctuation (English/Telugu).
3. Remove fillers if enabled.
4. Apply the dictionary, recording every replacement.
5. Tidy spacing, sentence-case Latin starts, add terminal punctuation.
6. Store raw text, final text, language, engine, provider, latency, and edits.

No step translates between languages or invents words. Every application is reversible from the raw transcript.

## Build

`scripts/build.sh` performs:

1. Extract `classes.jar` and `jni/<abi>/*.so` from sherpa, Vosk, JNA AARs.
2. Strip `module-info.class` and multi-release entries.
3. `aapt2 compile` resources, then `aapt2 link` with `app/assets` (models included).
4. `javac` against `android.jar` plus dependency jars.
5. Jar app classes.
6. D8 `--release --min-api 26 --lib android.jar` → `classes.dex` (dx is unsupported; no lambda desugaring).
7. Add `classes.dex` and `lib/arm64-v8a/*.so` to the linked APK.
8. `zipalign`, sign, verify.

## Storage

- `files/models/` — unpacked models with a version marker.
- `files/history.jsonl` — append-only JSONL, max 300 entries.
- `files/audio/` — optional per-dictation WAVs.
- `shared_prefs/` — settings; the API key ciphertext lives here but the key is in Keystore.
