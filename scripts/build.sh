#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
APP="$ROOT/app"

ANDROID_JAR="${ANDROID_JAR:-$HOME/asdk/android-35/android.jar}"
VENDOR="${WAHTUH_VENDOR:-$ROOT/vendor}"
D8_JAR="${D8_JAR:-$VENDOR/d8.jar}"
ORT="${WAHTUH_ORT:-$VENDOR/onnxruntime}"
SHERPA="${WAHTUH_SHERPA:-$VENDOR/sherpa-onnx}"
VERSION_CODE="${VERSION_CODE:-6}"
VERSION_NAME="${VERSION_NAME:-0.4.0}"
ABI="${ABI:-arm64-v8a}"

[ -f "$ANDROID_JAR" ] || { echo "Missing $ANDROID_JAR" >&2; exit 1; }
[ -f "$D8_JAR" ] || { echo "Missing $D8_JAR; run scripts/fetch-deps.sh" >&2; exit 1; }
[ -f "$APP/assets/models/whistle.cact" ] || {
  echo "Whistle model missing. Run scripts/fetch-models.sh first." >&2
  exit 1
}
[ -f "$APP/jniLibs/$ABI/libwhistle_jni.so" ] || {
  echo "Native Whistle library missing. Run scripts/build-native.sh first." >&2
  exit 1
}
[ -f "$ORT/classes.jar" ] && [ -f "$ORT/$ABI/libonnxruntime.so" ] || {
  echo "ONNX Runtime missing. Run scripts/fetch-deps.sh first." >&2
  exit 1
}
[ -f "$APP/assets/models/silero_vad.onnx" ] || {
  echo "Silero VAD model missing. Run scripts/fetch-models.sh first." >&2
  exit 1
}
[ -f "$SHERPA/classes.jar" ] && [ -f "$SHERPA/$ABI/libsherpa-onnx-jni.so" ] || {
  echo "sherpa-onnx missing. Run scripts/fetch-deps.sh first." >&2
  exit 1
}
for f in encoder.int8.onnx decoder.int8.onnx joiner.int8.onnx tokens.txt; do
  [ -f "$APP/assets/models/streaming/$f" ] || {
    echo "Streaming model missing ($f). Run scripts/fetch-models.sh first." >&2
    exit 1
  }
done

echo "==> clean"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/pkg/lib/$ABI"

echo "==> native libraries"
cp "$APP/jniLibs/$ABI/"*.so "$BUILD/pkg/lib/$ABI/"
cp "$ORT/$ABI/"*.so "$BUILD/pkg/lib/$ABI/"
cp "$SHERPA/$ABI/"*.so "$BUILD/pkg/lib/$ABI/"

echo "==> compile resources"
aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> link resources and assets"
aapt2 link -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --java "$BUILD/gen" \
  --custom-package com.wahtuh.pho \
  --min-sdk-version 26 --target-sdk-version 35 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$BUILD/res.zip"

echo "==> compile java"
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 \
  -classpath "$ANDROID_JAR:$ORT/classes.jar:$SHERPA/classes.jar" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "==> dex"
( cd "$BUILD/classes" && jar cf "$BUILD/app-classes.jar" . )
rm -f "$BUILD/pkg/classes.dex"
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$ANDROID_JAR" --output "$BUILD/pkg" \
  "$BUILD/app-classes.jar" "$ORT/classes.jar" "$SHERPA/classes.jar"

echo "==> package"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
( cd "$BUILD/pkg" && zip -q -X "$BUILD/unsigned.apk" classes.dex )
( cd "$BUILD/pkg" && zip -q -X -r "$BUILD/unsigned.apk" lib )

echo "==> align"
zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

echo "==> sign"
KEYSTORE="${WAHTUH_KEYSTORE:-$HOME/.android/wahtuh-debug.jks}"
ALIAS="${WAHTUH_KEY_ALIAS:-wahtuh}"
STORE_PASS="${WAHTUH_STORE_PASS:-wahtuhdebug}"
KEY_PASS="${WAHTUH_KEY_PASS:-$STORE_PASS}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  echo "    creating keystore $KEYSTORE (debug signing key; use WAHTUH_KEYSTORE for releases)"
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Wahtuh Pho, OU=Open Source, O=Wahtuh Pho, L=Local, S=Local, C=IN" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$BUILD/wahtuh-pho-$VERSION_NAME.apk" "$BUILD/aligned.apk"
apksigner verify --print-certs "$BUILD/wahtuh-pho-$VERSION_NAME.apk" | head -6

echo
echo "APK: $BUILD/wahtuh-pho-$VERSION_NAME.apk"
ls -lh "$BUILD/wahtuh-pho-$VERSION_NAME.apk"
