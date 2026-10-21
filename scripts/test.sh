#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_JAR="${ANDROID_JAR:-${ANDROID_HOME:-$HOME/asdk}/platforms/android-35/android.jar}"
if [ ! -f "$SDK_JAR" ]; then
  SDK_JAR="${ANDROID_JAR:-$HOME/asdk/android-35/android.jar}"
fi
[ -f "$SDK_JAR" ] || { echo "android.jar not found; set ANDROID_JAR"; exit 1; }
OUT="$ROOT/build/tests"
rm -rf "$OUT"
mkdir -p "$OUT/classes"
javac -encoding UTF-8 -Xlint:-options -cp "$SDK_JAR" -d "$OUT/classes" \
  $(find "$ROOT/app/src/com/wahtuh/pho/text" -name '*.java') \
  "$ROOT/tests/TextPipelineTest.java"
java -cp "$OUT/classes:$SDK_JAR" TextPipelineTest
