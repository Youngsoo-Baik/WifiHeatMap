#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
PLATFORM_DIR="$SDK_DIR/platforms/android-34"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools/34.0.0"
BUILD_DIR="$PROJECT_DIR/build"
APP_DIR="$PROJECT_DIR/app/src/main"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ ! -f "$PLATFORM_DIR/android.jar" || ! -x "$BUILD_TOOLS_DIR/aapt2" ]]; then
  echo "Android SDK Platform 34와 Build Tools 34.0.0이 필요합니다." >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/out"

"$BUILD_TOOLS_DIR/aapt2" compile --dir "$APP_DIR/res" -o "$BUILD_DIR/resources.zip"
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.wifiheatmap" android:versionCode="3" android:versionName="0.3.0">#' \
  "$APP_DIR/AndroidManifest.xml" > "$BUILD_DIR/AndroidManifest.xml"
"$BUILD_TOOLS_DIR/aapt2" link \
  -I "$PLATFORM_DIR/android.jar" \
  --manifest "$BUILD_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/generated" \
  --min-sdk-version 23 \
  --target-sdk-version 34 \
  -o "$BUILD_DIR/base.apk" \
  "$BUILD_DIR/resources.zip"

find "$APP_DIR/java" "$BUILD_DIR/generated" -name '*.java' -print0 \
  | xargs -0 javac -source 8 -target 8 -Xlint:-options \
      -classpath "$PLATFORM_DIR/android.jar" \
      -d "$BUILD_DIR/classes"

find "$BUILD_DIR/classes" -name '*.class' > "$BUILD_DIR/classes.list"
"$BUILD_TOOLS_DIR/d8" \
  --lib "$PLATFORM_DIR/android.jar" \
  --min-api 23 \
  --output "$BUILD_DIR/dex" \
  @"$BUILD_DIR/classes.list"

cp "$BUILD_DIR/base.apk" "$BUILD_DIR/app-unsigned.apk"
zip -q -j "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/dex/classes.dex"
"$BUILD_TOOLS_DIR/zipalign" -f 4 "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/out/wifi-heatmap-mvp.apk"

KEYSTORE="$BUILD_DIR/debug.keystore"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1

"$BUILD_TOOLS_DIR/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  "$BUILD_DIR/out/wifi-heatmap-mvp.apk"

"$BUILD_TOOLS_DIR/apksigner" verify "$BUILD_DIR/out/wifi-heatmap-mvp.apk"
echo "완료: $BUILD_DIR/out/wifi-heatmap-mvp.apk"
