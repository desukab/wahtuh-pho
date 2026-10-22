#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/app/assets/models"
mkdir -p "$MODELS"
URL="https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact"
SHA="b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb"
OUT="$MODELS/whistle.cact"
SILERO_URL="https://github.com/snakers4/silero-vad/raw/v5.1.2/src/silero_vad/data/silero_vad.onnx"
SILERO_SHA="2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f"
SILERO="$MODELS/silero_vad.onnx"
if [ -f "$OUT" ] && echo "$SHA  $OUT" | sha256sum -c - >/dev/null 2>&1; then
  echo "ok whistle.cact"
else
  echo "fetch whistle.cact"
  curl -L --fail --retry 5 -o "$OUT.part" "$URL"
  echo "$SHA  $OUT.part" | sha256sum -c -
  mv "$OUT.part" "$OUT"
fi
if [ -f "$SILERO" ] && echo "$SILERO_SHA  $SILERO" | sha256sum -c - >/dev/null 2>&1; then
  echo "ok silero_vad.onnx"
else
  echo "fetch silero_vad.onnx"
  curl -L --fail --retry 5 -o "$SILERO.part" "$SILERO_URL"
  echo "$SILERO_SHA  $SILERO.part" | sha256sum -c -
  mv "$SILERO.part" "$SILERO"
fi
STREAM_DIR="$MODELS/streaming"
STREAM_BASE="https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main"
mkdir -p "$STREAM_DIR"
stream_fetch() {
  local remote="$1" local="$2" sha="$3"
  local out="$STREAM_DIR/$local"
  if [ -f "$out" ] && echo "$sha  $out" | sha256sum -c - >/dev/null 2>&1; then
    echo "ok streaming/$local"
    return
  fi
  echo "fetch streaming/$local"
  curl -L --fail --retry 5 -o "$out.part" "$STREAM_BASE/$remote"
  echo "$sha  $out.part" | sha256sum -c -
  mv "$out.part" "$out"
}
stream_fetch "encoder-epoch-99-avg-1.int8.onnx" "encoder.int8.onnx" \
  "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3"
stream_fetch "decoder-epoch-99-avg-1.int8.onnx" "decoder.int8.onnx" \
  "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c"
stream_fetch "joiner-epoch-99-avg-1.int8.onnx" "joiner.int8.onnx" \
  "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e"
stream_fetch "tokens.txt" "tokens.txt" \
  "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"
cat > "$MODELS/CHECKSUMS.txt" <<SUMS
$SHA  ./whistle.cact
$SILERO_SHA  ./silero_vad.onnx
3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3  ./streaming/encoder.int8.onnx
21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c  ./streaming/decoder.int8.onnx
e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e  ./streaming/joiner.int8.onnx
49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb  ./streaming/tokens.txt
SUMS
echo "model ready in $MODELS"
