# Benchmarks

## Device

- vivo V2251, Android 15 (SDK 35), arm64-v8a
- 16 kHz mono, 16-bit PCM WAV
- Wahtuh Pho 0.1.0, D8 build, models unpacked in app-private storage

## Clips

- English: LibriSpeech sample `0.wav`, 6.63 s. Reference: “AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS”.
- Telugu: Google Translate TTS `te1.wav`, 4.73 s, synthetic female voice. Reference: “నమస్కారం ఈ రోజు వాతావరణం చాలా బాగుంది”.

Both clips are bundled under `app/assets/test/` for reproducibility. Telugu is **synthetic**, not human-verified field audio.

## Results

| Run | Engine | Wall | RTF | WER | CER |
|---|---|---:|---:|---:|---:|
| English | Whisper tiny int8 | 2525 ms | 0.381 | 0.0% | 0.0% |
| Telugu | Vosk small te 0.42 (cold) | 2080 ms | 0.439 | 0.0% | 0.0% |
| Telugu | Vosk small te 0.42 (warm) | 932 ms | 0.197 | 0.0% | 0.0% |
| Telugu | Hybrid auto (Whisper + Vosk) | 3133 ms | 0.663 | 0.0% | 0.0% |

Hybrid runs Whisper first to detect language, then Vosk when the utterance is Telugu, which explains the higher latency. For English it returns the Whisper result directly.

`WER 0.0%` on a clean synthetic clip means the pipeline is wired correctly; it does not predict noisy, accented, or conversational speech. Human Telugu and code-switching remain unmeasured.

## Reproduce

```bash
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez tests true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_en true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_te true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ez bench_auto true
adb shell am start -n com.wahtuh.pho/.diag.DiagnosticsActivity --ei mic 3
adb logcat -s WahtuhDiag:*
```

## Self-tests

`10/10 checks passed`: punctuation/filler cleanup, Telugu comma, English/Telugu dictionary, exact WER, deletion WER, WAV round-trip, silence split, silence trim, Keystore encryption.
