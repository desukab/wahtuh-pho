#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${WAHTUH_CACHE:-$ROOT/build/downloads}"
OUT="$ROOT/app/jniLibs/arm64-v8a"
mkdir -p "$CACHE" "$OUT"

if [ ! -f "$CACHE/libneedle.a" ]; then
  curl -L --fail --retry 5 -o "$CACHE/libneedle.a" \
    https://huggingface.co/Cactus-Compute/needle3/resolve/main/android-arm64/libneedle.a
fi
cp "$ROOT/native/needle.h" "$CACHE/needle.h"

clang++ -shared -fPIC -O2 -std=c++17 \
  -I/data/data/com.termux/files/usr/include -I"$CACHE" \
  -o "$OUT/libwhistle_jni.so" \
  "$ROOT/native/whistle_jni.cpp" "$CACHE/libneedle.a" \
  -L/data/data/com.termux/files/usr/lib -lc++_shared
cp /data/data/com.termux/files/usr/lib/libc++_shared.so "$OUT/"
echo "native libraries ready in $OUT"
