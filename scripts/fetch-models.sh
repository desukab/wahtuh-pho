#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/app/assets/models"
mkdir -p "$MODELS"
URL="https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact"
SHA="b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb"
OUT="$MODELS/whistle.cact"
if [ -f "$OUT" ] && echo "$SHA  $OUT" | sha256sum -c - >/dev/null 2>&1; then
  echo "ok whistle.cact"
else
  echo "fetch whistle.cact"
  curl -L --fail --retry 5 -o "$OUT.part" "$URL"
  echo "$SHA  $OUT.part" | sha256sum -c -
  mv "$OUT.part" "$OUT"
fi
cat > "$MODELS/CHECKSUMS.txt" <<SUMS
$SHA  ./whistle.cact
SUMS
echo "model ready in $MODELS"
