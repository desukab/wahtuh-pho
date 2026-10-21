# Benchmarks

## Device

- vivo V2251, Android 15 (SDK 35), arm64-v8a
- 16 kHz mono, 16-bit PCM WAV
- Whistle `whistle.cact`, 16,919,407 bytes, run through the Cactus `needle` CLI with `--threads 2`

These are **CLI measurements**, not in-app latency. The app links the same runtime through JNI, but the JNI path has not been timed on this device yet.

## Clips

- English: LibriSpeech sample `0.wav`, 3.65 s. Reference: “AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS”.
- English: `en1.wav`, roughly 4 s, conversational sentence.
- Telugu: Google Translate TTS `te1.wav`, 4.73 s, synthetic female voice. Reference: “నమస్కారం ఈ రోజు వాతావరణం చాలా బాగుంది”.

`0.wav` and `te1.wav` are bundled under `app/assets/test/` for reproducibility.

## Results

| Clip | Language | Audio | Wall time | Result |
|---|---|---:|---:|---|
| `0.wav` | `en` | 3.65 s | 1.3–3.2 s warm | exact reference (0% WER) |
| `0.wav` | `en` | 3.65 s | ~8.9 s cold | exact reference, first run incl. model/cache load |
| `en1.wav` | `en` | ~4 s | warm | “Let's shed you the meeting for to morrow at ten thirty.” — one word substitution and spacing errors |
| `te1.wav` | unspecified | 4.73 s | warm | romanised garbage; Telugu is unsupported |

Whistle supports English, German, French, Spanish, Italian, Dutch, and Polish. Telugu was removed from the app after this test.

Command:

```bash
tmp/opencode/whistle/needle \
  --model tmp/opencode/whistle/whistle.cact \
  --audio tests/audio/0.wav \
  --audio-language en \
  --threads 2
```

## Interpretation

- One exact English clip is a wiring check, not an accuracy claim.
- The second English clip shows Whistle can make substitutions that a larger Whisper model avoided on the same audio.
- Accents, noisy rooms, longer dictation, and the other six languages still need field testing.
- Cold-start cost is dominated by loading the 16.9 MB model and warming caches; the app keeps the model loaded for the process lifetime.

## Text tests

`./scripts/test.sh` runs dependency-free pipeline tests: punctuation and spacing, dictionary substitutions, exact WER, and deletion WER. Current result: **5/5 checks passed**.
EOF