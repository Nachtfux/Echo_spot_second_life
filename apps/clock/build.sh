#!/usr/bin/env bash
# build.sh - builds Spot Clock without Gradle: aapt2 (resources) + javac (Java 8 syntax)
# + d8 (dex) + zipalign + apksigner. Output: build/spot-clock.apk
#
# Needs a JDK 17+ and the Android SDK (build-tools and a platform android-30 or newer).
# Works with macOS's bash 3.2.
#
# Environment (all optional):
#   ANDROID_HOME / ANDROID_SDK_ROOT  SDK location; otherwise ~/Library/Android/sdk or ~/Android/Sdk
#   JAVA_HOME                        a JDK 17+; otherwise the usual install locations are searched
#   VERSION_CODE / VERSION_NAME      default 10 / 1.0.0
#   ESL_KEYSTORE, ESL_KEYSTORE_PASS, ESL_KEY_ALIAS, ESL_KEY_PASS
#                                    release key. Only used when all four are set; otherwise the
#                                    standard debug key ~/.android/debug.keystore signs the APK
#                                    (created with keytool if it does not exist yet).
set -euo pipefail
cd "$(dirname "$0")"

die() { echo "build.sh: error: $*" >&2; exit 1; }
warn() { echo "build.sh: warning: $*" >&2; }

# Prints the major Java version of the given java binary (8, 11, 17, ...), or nothing.
java_major() {
  local v
  v="$("$1" -version 2>&1 | sed -n 's/.* version "\([^"]*\)".*/\1/p' | head -n1)"
  case "$v" in 1.*) v="${v#1.}" ;; esac
  v="${v%%[!0-9]*}"
  echo "$v"
}

# ---------------------------------------------------------------- JDK 17+ --
find_jdk() {
  local d m mac=""
  if [ -x /usr/libexec/java_home ]; then
    mac="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
  fi
  # Unmatched globs stay literal and fail the -x test below.
  for d in "${JAVA_HOME_17:-}" "${JAVA_HOME:-}" "$mac" \
      /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 \
      /opt/homebrew/opt/openjdk /usr/local/opt/openjdk \
      /usr/lib/jvm/java-17-openjdk* /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/temurin-* ; do
    [ -n "$d" ] && [ -x "$d/bin/javac" ] && [ -x "$d/bin/java" ] || continue
    m="$(java_major "$d/bin/java")"
    if [ -n "$m" ] && [ "$m" -ge 17 ]; then
      echo "$d"
      return 0
    fi
  done
  return 1
}

if JDK="$(find_jdk)"; then
  export JAVA_HOME="$JDK"
  export PATH="$JAVA_HOME/bin:$PATH"    # d8/apksigner run on this Java too (macOS's /usr/bin/java may be 8)
else
  m=""
  if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
    m="$(java_major "$(command -v java)")"
  fi
  [ -n "$m" ] && [ "$m" -ge 17 ] \
    || die "JDK 17+ not found. Install one (macOS: brew install openjdk@17, Debian/Ubuntu: apt install openjdk-17-jdk) or set JAVA_HOME."
fi

# ------------------------------------------------------------ Android SDK --
SDK=""
for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$d" ] && [ -d "$d/build-tools" ] && [ -d "$d/platforms" ]; then
    SDK="$d"
    break
  fi
done
[ -n "$SDK" ] || die "Android SDK not found. Set ANDROID_HOME (it needs build-tools and a platform android-30 or newer)."

# The loops below live in functions (bash 3.2 cannot parse a case pattern inside $(...))
# and use if, not &&, so a failed test in the last iteration cannot fail the pipeline.

# Prints the versions of the build-tools that have every tool we need.
list_build_tools() {
  local d
  for d in "$SDK"/build-tools/*; do
    if [ -x "$d/aapt2" ] && [ -x "$d/d8" ] && [ -x "$d/zipalign" ] && [ -x "$d/apksigner" ]; then
      echo "${d##*/}"
    fi
  done
}

# Prints the API levels of the numbered platforms android-30 and newer.
list_platforms() {
  local d n
  for d in "$SDK"/platforms/android-*; do
    n="${d##*/android-}"
    case "$n" in '' | *[!0-9]*) continue ;; esac
    if [ "$n" -ge 30 ] && [ -f "$d/android.jar" ]; then echo "$n"; fi
  done
}

# Newest build-tools (version sort without GNU sort -V) and newest platform.
BT_VER="$(list_build_tools | sort -t. -k1,1n -k2,2n -k3,3n | tail -n1)"
[ -n "$BT_VER" ] || die "no complete build-tools in $SDK/build-tools (install one with sdkmanager \"build-tools;34.0.0\")."
BT="$SDK/build-tools/$BT_VER"
API="$(list_platforms | sort -n | tail -n1)"
[ -n "$API" ] || die "no platform android-30 or newer in $SDK/platforms (install one with sdkmanager \"platforms;android-34\")."
PLATFORM="$SDK/platforms/android-$API/android.jar"

command -v zip >/dev/null 2>&1 || die "zip not found (Debian/Ubuntu: apt install zip)."

# ---------------------------------------------------------------- version --
VERSION_CODE="${VERSION_CODE:-10}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
case "$VERSION_CODE" in '' | *[!0-9]*) die "VERSION_CODE must be a positive integer, got '$VERSION_CODE'." ;; esac
[ -n "$VERSION_NAME" ] || die "VERSION_NAME must not be empty."

# ---------------------------------------------------------------- signing --
if [ -n "${ESL_KEYSTORE:-}" ] && [ -n "${ESL_KEYSTORE_PASS:-}" ] \
    && [ -n "${ESL_KEY_ALIAS:-}" ] && [ -n "${ESL_KEY_PASS:-}" ]; then
  [ -f "$ESL_KEYSTORE" ] || die "ESL_KEYSTORE does not exist: $ESL_KEYSTORE"
  export ESL_KEYSTORE_PASS ESL_KEY_PASS
  KS="$ESL_KEYSTORE"
  KS_ALIAS="$ESL_KEY_ALIAS"
  KS_PASS="env:ESL_KEYSTORE_PASS"   # apksigner reads the passwords from the environment,
  KEY_PASS="env:ESL_KEY_PASS"       # so they never show up in the process list
  SIGNED_WITH="release key '$ESL_KEY_ALIAS'"
else
  if [ -n "${ESL_KEYSTORE:-}${ESL_KEYSTORE_PASS:-}${ESL_KEY_ALIAS:-}${ESL_KEY_PASS:-}" ]; then
    warn "release signing needs ESL_KEYSTORE, ESL_KEYSTORE_PASS, ESL_KEY_ALIAS and ESL_KEY_PASS; using the debug key."
  fi
  KS="$HOME/.android/debug.keystore"
  if [ ! -f "$KS" ]; then
    echo "Creating the standard debug keystore $KS"
    mkdir -p "$HOME/.android"
    keytool -genkeypair -keystore "$KS" -storepass android -alias androiddebugkey -keypass android \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
  fi
  # The standard Android debug keystore uses the public default password "android".
  KS_ALIAS="androiddebugkey"
  KS_PASS="pass:android"
  KEY_PASS="pass:android"
  SIGNED_WITH="debug key"
fi

# ----------------------------------------------------- translation check --
# Every English string needs a German one (Android would silently fall back to English).
string_keys() { sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort -u; }
for f in res/values-*/strings.xml; do
  [ -f "$f" ] || continue
  missing="$(comm -23 <(string_keys res/values/strings.xml) <(string_keys "$f") | tr '\n' ' ')"
  [ -z "$missing" ] || warn "$f lacks: $missing"
done

# ------------------------------------------------------------------ build --
OUT=build
echo "Spot Clock $VERSION_NAME ($VERSION_CODE): build-tools $BT_VER, android-$API, Java $(java_major "$(command -v java)")"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

# --dir compiles every resource folder, including qualified ones such as values-de.
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 30 --target-sdk-version 30 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" "$OUT/res.zip"

# Java 8 syntax, no lambdas or method references: this android.jar has no LambdaMetafactory.
# shellcheck disable=SC2046  # paths are relative to this folder and contain no spaces
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -Xlint:-options -encoding UTF-8 \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
# shellcheck disable=SC2046
"$BT/d8" --min-api 30 --lib "$PLATFORM" --output "$OUT" $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" --key-pass "$KEY_PASS" \
  --out "$OUT/spot-clock.apk" "$OUT/aligned.apk"
rm -f "$OUT/spot-clock.apk.idsig"

LOCALES="$("$BT/aapt2" dump badging "$OUT/spot-clock.apk" | sed -n "s/^locales: //p")"
echo "-> $OUT/spot-clock.apk (signed with $SIGNED_WITH; locales: $LOCALES)"
