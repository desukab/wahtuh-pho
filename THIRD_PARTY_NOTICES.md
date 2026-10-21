# Third-party notices

Wahtuh Pho source code is licensed under Apache-2.0. It bundles or downloads:

| Component | Version | License | Source |
|---|---|---|---|
| Cactus Whistle speech model | `whistle.cact` (2026-10) | Apache-2.0 | https://huggingface.co/Cactus-Compute/whistle |
| Cactus Needle runtime (`libneedle.a`) | 2026-10 | Apache-2.0 | https://github.com/cactus-compute/needle |
| Press Start 2P font | 2012 | SIL Open Font License 1.1 | https://fonts.google.com/specimen/Press+Start+2P |
| VT323 font | 2011 | SIL Open Font License 1.1 | https://fonts.google.com/specimen/VT323 |
| D8 | build-tools 35 | Apache-2.0 | https://developer.android.com/tools/releases/build-tools |
| LibriSpeech sample | test fixture | CC BY 4.0 | https://www.openslr.org/12 |
| Telugu test clip | test fixture | generated with Google Translate TTS | local synthetic fixture |

The font license text is included at `app/assets/fonts/OFL.txt`. The Whistle model and Android arm64 runtime are fetched by `scripts/fetch-models.sh`; model files are not stored in git.

Wahtuh Pho is not affiliated with, endorsed by, or sponsored by Wispr Flow, Cactus Compute, or OpenAI.
