#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/app/assets/models"
CACHE="${WAHTUH_CACHE:-$ROOT/build/downloads}"
mkdir -p "$MODELS/whisper-tiny" "$MODELS/vosk-te" "$CACHE"

fetch() {
  local url="$1" out="$2" sha="$3"
  if [ -f "$out" ] && echo "$sha  $out" | sha256sum -c - >/dev/null 2>&1; then
    echo "ok $(basename "$out")"
    return
  fi
  echo "fetch $(basename "$out")"
  curl -L --fail --retry 3 --retry-delay 2 -o "$out.part" "$url"
  echo "$sha  $out.part" | sha256sum -c -
  mv "$out.part" "$out"
}

HF="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"
fetch "$HF/tiny-encoder.int8.onnx" "$MODELS/whisper-tiny/tiny-encoder.int8.onnx" \
  "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"
fetch "$HF/tiny-decoder.int8.onnx" "$MODELS/whisper-tiny/tiny-decoder.int8.onnx" \
  "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"
fetch "$HF/tiny-tokens.txt" "$MODELS/whisper-tiny/tiny-tokens.txt" \
  "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"

VOSK_ZIP="$CACHE/vosk-model-small-te-0.42.zip"
fetch "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip" \
  "$VOSK_ZIP" "3e77b05becc7599a596ccebd8b22cb95eb4eca0cfca524827cdfbfd66af09497"
rm -rf "$CACHE/vosk-te"
unzip -q "$VOSK_ZIP" -d "$CACHE/vosk-te"
rm -rf "$MODELS/vosk-te"
cp -R "$CACHE/vosk-te/vosk-model-small-te-0.42" "$MODELS/vosk-te"
echo "models ready in $MODELS"
