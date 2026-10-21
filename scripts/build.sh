#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
APP="$ROOT/app"

ANDROID_JAR="${ANDROID_JAR:-$HOME/asdk/android-35/android.jar}"
VENDOR="${WAHTUH_VENDOR:-$ROOT/vendor}"

SHERPA_AAR="${SHERPA_AAR:-$VENDOR/sherpa.aar}"
VOSK_AAR="${VOSK_AAR:-$VENDOR/vosk.aar}"
JNA_AAR="${JNA_AAR:-$VENDOR/jna.aar}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-$VENDOR/kotlin-stdlib.jar}"
D8_JAR="${D8_JAR:-$VENDOR/d8.jar}"

VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-0.1.0}"
ABI="${ABI:-arm64-v8a}"

for f in "$ANDROID_JAR" "$SHERPA_AAR" "$VOSK_AAR" "$JNA_AAR" "$KOTLIN_STDLIB"; do
  if [ ! -f "$f" ]; then
    echo "Missing dependency: $f" >&2
    echo "Run scripts/fetch-deps.sh first, or set SHERPA_AAR/VOSK_AAR/JNA_AAR/KOTLIN_STDLIB." >&2
    exit 1
  fi
done

if [ ! -f "$APP/assets/models/vosk-te/conf/model.conf" ] \
  || [ ! -f "$APP/assets/models/whisper-tiny/tiny-decoder.int8.onnx" ]; then
  echo "Bundled models are missing. Run scripts/fetch-models.sh first." >&2
  exit 1
fi

echo "==> clean"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/deps" "$BUILD/pkg/lib/$ABI"

echo "==> extract dependencies"
unzip -o -q "$SHERPA_AAR" "classes.jar" "jni/$ABI/*" -d "$BUILD/deps/sherpa"
unzip -o -q "$VOSK_AAR" "classes.jar" "jni/$ABI/*" -d "$BUILD/deps/vosk"
unzip -o -q "$JNA_AAR" "classes.jar" "jni/$ABI/*" -d "$BUILD/deps/jna"
cp "$KOTLIN_STDLIB" "$BUILD/deps/kotlin-stdlib.jar"
mv "$BUILD/deps/sherpa/classes.jar" "$BUILD/deps/sherpa-classes.jar"
mv "$BUILD/deps/vosk/classes.jar" "$BUILD/deps/vosk-classes.jar"
mv "$BUILD/deps/jna/classes.jar" "$BUILD/deps/jna-classes.jar"
for j in "$BUILD"/deps/*.jar; do
  zip -q -d "$j" 'META-INF/versions/*' >/dev/null 2>&1 || true
  zip -q -d "$j" 'module-info.class' >/dev/null 2>&1 || true
done

cp "$BUILD/deps/sherpa/jni/$ABI/libsherpa-onnx-jni.so" "$BUILD/pkg/lib/$ABI/"
cp "$BUILD/deps/sherpa/jni/$ABI/libonnxruntime.so" "$BUILD/pkg/lib/$ABI/"
cp "$BUILD/deps/vosk/jni/$ABI/libvosk.so" "$BUILD/pkg/lib/$ABI/"
cp "$BUILD/deps/jna/jni/$ABI/libjnidispatch.so" "$BUILD/pkg/lib/$ABI/"

echo "==> compile resources"
aapt2 compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> link resources and assets (bundled models included)"
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
  -classpath "$ANDROID_JAR:$BUILD/deps/sherpa-classes.jar:$BUILD/deps/kotlin-stdlib.jar:$BUILD/deps/vosk-classes.jar:$BUILD/deps/jna-classes.jar" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "==> dex"
( cd "$BUILD/classes" && jar cf "$BUILD/app-classes.jar" . )
rm -f "$BUILD/pkg/classes.dex"
if [ -f "$D8_JAR" ]; then
  # D8 desugars lambdas and method references. The legacy dx tool does not,
  # and Android 14+ blocks the hidden LambdaMetafactory path it emits.
  java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 26 \
    --lib "$ANDROID_JAR" --output "$BUILD/pkg" \
    "$BUILD/app-classes.jar" \
    "$BUILD/deps/sherpa-classes.jar" \
    "$BUILD/deps/kotlin-stdlib.jar" \
    "$BUILD/deps/vosk-classes.jar" \
    "$BUILD/deps/jna-classes.jar"
else
  echo "    WARNING: vendor/d8.jar missing; falling back to dx (lambdas may crash on Android 14+)" >&2
  dx --dex --min-sdk-version=26 --output="$BUILD/pkg/classes.dex" --no-warning \
    "$BUILD/classes" \
    "$BUILD/deps/sherpa-classes.jar" \
    "$BUILD/deps/kotlin-stdlib.jar" \
    "$BUILD/deps/vosk-classes.jar" \
    "$BUILD/deps/jna-classes.jar"
fi

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
