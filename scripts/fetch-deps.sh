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

fetch "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar" \
  "$VENDOR/sherpa.aar" "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
fetch "https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar" \
  "$VENDOR/vosk.aar" "ab2f8b91ac8051561aa325546b35fed9a68b36b8121bac5c6fb927525c4adfad"
fetch "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.aar" \
  "$VENDOR/jna.aar" "7f053e3ec99e14dd71259c82c1c8a02738d64a13c31226b2acc170f3060951e0"
fetch "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.1.0/kotlin-stdlib-2.1.0.jar" \
  "$VENDOR/kotlin-stdlib.jar" "d6f91b7b0f306cca299fec74fb7c34e4874d6f5ec5b925a0b4de21901e119c3f"

BT_ZIP="$CACHE/build-tools_r35_linux.zip"
fetch "https://dl.google.com/android/repository/build-tools_r35_linux.zip" \
  "$BT_ZIP" "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88"
if [ ! -f "$VENDOR/d8.jar" ]; then
  rm -rf "$CACHE/bt35"
  mkdir -p "$CACHE/bt35"
  unzip -q "$BT_ZIP" -d "$CACHE/bt35"
  cp "$CACHE/bt35/android-15/lib/d8.jar" "$VENDOR/d8.jar"
fi
echo "dependencies ready in $VENDOR"
