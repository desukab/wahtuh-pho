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
echo "dependencies ready in $VENDOR"
