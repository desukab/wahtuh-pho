# Third-party notices

Wahtuh Pho source code is licensed under Apache-2.0. It bundles or downloads:

| Component | Version | License | Source |
|---|---|---|---|
| sherpa-onnx | 1.13.8 | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx |
| Vosk Android | 0.3.75 | Apache-2.0 | https://github.com/alphacep/vosk-api |
| JNA | 5.18.1 | Apache-2.0 / LGPL-2.1 | https://github.com/java-native-access/jna |
| Kotlin stdlib | 2.1.0 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| Whisper tiny int8 model | sherpa-onnx conversion | MIT (OpenAI Whisper) | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny |
| Vosk small Telugu model | 0.42 | Apache-2.0 | https://alphacephei.com/vosk/models |
| D8 | build-tools 35 | Apache-2.0 | https://developer.android.com/tools/releases/build-tools |
| LibriSpeech sample | test fixture | CC BY 4.0 | https://www.openslr.org/12 |
| Telugu test clip | test fixture | generated with Google Translate TTS | local synthetic fixture |

The Telugu clip is included only to reproduce the benchmark and is not claimed as natural speech. Model files are fetched by `scripts/fetch-models.sh` and are not stored in git.

Wahtuh Pho is not affiliated with, endorsed by, or sponsored by Wispr Flow or OpenAI.
