#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR="${WAHTUH_VENDOR:-$ROOT/vendor}"
CACHE="${WAHTUH_CACHE:-$ROOT/build/downloads}"
mkdir -p "$VENDOR" "$CACHE"

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

BT_ZIP="$CACHE/build-tools_r35_linux.zip"
fetch "https://dl.google.com/android/repository/build-tools_r35_linux.zip" \
  "$BT_ZIP" "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88"
if [ ! -f "$VENDOR/d8.jar" ]; then
  rm -rf "$CACHE/bt35"
  mkdir -p "$CACHE/bt35"
  unzip -q "$BT_ZIP" -d "$CACHE/bt35"
  cp "$CACHE/bt35/android-15/lib/d8.jar" "$VENDOR/d8.jar"
fi
ORT_AAR="$CACHE/onnxruntime-android-1.20.0.aar"
fetch "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.20.0/onnxruntime-android-1.20.0.aar" \
  "$ORT_AAR" "07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027"
if [ ! -f "$VENDOR/onnxruntime/classes.jar" ] \
    || [ ! -f "$VENDOR/onnxruntime/arm64-v8a/libonnxruntime.so" ]; then
  rm -rf "$VENDOR/onnxruntime"
  mkdir -p "$VENDOR/onnxruntime"
  unzip -q "$ORT_AAR" "classes.jar" "jni/arm64-v8a/*" -d "$VENDOR/onnxruntime"
  mv "$VENDOR/onnxruntime/jni/arm64-v8a" "$VENDOR/onnxruntime/arm64-v8a"
  rmdir "$VENDOR/onnxruntime/jni"
fi
SHERPA_AAR="$CACHE/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
SHERPA_JAR="$CACHE/sherpa-onnx-jvm-1.13.8.jar"
fetch "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar" \
  "$SHERPA_AAR" "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"
fetch "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar" \
  "$SHERPA_JAR" "77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b"
if [ ! -f "$VENDOR/sherpa-onnx/classes.jar" ] \
    || [ ! -f "$VENDOR/sherpa-onnx/arm64-v8a/libsherpa-onnx-jni.so" ]; then
  rm -rf "$VENDOR/sherpa-onnx"
  mkdir -p "$VENDOR/sherpa-onnx"
  cp "$SHERPA_JAR" "$VENDOR/sherpa-onnx/classes.jar"
  unzip -q "$SHERPA_AAR" "jni/arm64-v8a/libsherpa-onnx-jni.so" -d "$VENDOR/sherpa-onnx"
  mv "$VENDOR/sherpa-onnx/jni/arm64-v8a" "$VENDOR/sherpa-onnx/arm64-v8a"
  rmdir "$VENDOR/sherpa-onnx/jni"
fi
echo "dependencies ready in $VENDOR"
