#!/bin/bash
# build.sh - builds the Spot clock APK without Gradle (aapt2 + javac + d8 + apksigner).
#
# Needs the Android SDK (build-tools, one platform) and a JDK 17.
# Output: build/spot-clock.apk, signed with the local debug key.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | tail -n1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* | tail -n1)/android.jar"
export JAVA_HOME="${JAVA_HOME_17:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"      # d8/apksigner need Java 11+, not macOS's old Java 8
JAVAC="$JAVA_HOME/bin/javac"
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 30 --target-sdk-version 30 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" "$OUT/res.zip"

# shellcheck disable=SC2046
"$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM" -Xlint:-options -encoding UTF-8 \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
# shellcheck disable=SC2046
"$BT/d8" --min-api 30 --lib "$PLATFORM" --output "$OUT" $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
# The standard Android debug keystore ships with the public default password "android".
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$OUT/spot-clock.apk" "$OUT/aligned.apk"
echo "-> $OUT/spot-clock.apk"
