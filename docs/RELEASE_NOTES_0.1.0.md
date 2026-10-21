# Wahtuh Pho 0.1.0

First working Android build. Sideload-only, debug-signed test APK.

## Highlights

- On-device hybrid dictation: Whisper tiny int8 for English, Vosk small Telugu for Telugu, auto routing per utterance.
- Floating bubble redesigned as a cream waveform pill with a live timer and recording/transcribing/error states.
- New Home dashboard (serif hero, primary dictation card, setup card, stats, recent transcripts), plus Dictionary and Settings tabs and a restyled History screen.
- Voice keyboard, accessibility insertion with clipboard fallback, encrypted cloud keys, local-only enforcement.
- Diagnostics: 10/10 self-tests; WER/CER benchmarks; live mic smoke test.

## Evidence

- Device: vivo V2251, Android 15 (SDK 35), arm64-v8a.
- English LibriSpeech clip: WER 0.0%, CER 0.0%, RTF 0.381.
- Synthetic Telugu clip: WER 0.0%, CER 0.0%, RTF 0.197 warm.
- Hybrid auto route on Telugu: WER 0.0%, RTF 0.663.

Telugu evidence is synthetic TTS, not human field audio. Whisper small and real code-switching are not measured.

## Install

```bash
adb install -r wahtuh-pho-0.1.0.apk
```

Android 8.0+, arm64. The app bundles ~222 MB of models; storage after first unpack is ~350 MB.

## Notes

- APK is debug-signed for testing; expect a new signature for a future release.
- No account, sync, snippets, or styles yet.
- SHA256 is in `docs/APK-SHA256.txt`.
