#!/usr/bin/env bash
# build-apps.sh - builds both apps from source into dist/:
#   apps/voice  (Gradle: ./gradlew assembleRelease) -> dist/spot-voice.apk
#   apps/clock  (plain build.sh)                    -> dist/spot-clock.apk
# and writes dist/SHA256SUMS (same format as the GitHub release).
#
# Needs JDK 17 (or newer) and the Android SDK (platforms android-34 + android-30, build-tools).
# Release signing: set ESL_KEYSTORE, ESL_KEYSTORE_PASS, ESL_KEY_ALIAS, ESL_KEY_PASS; without
# them the apps are signed with the local debug key.
#
# Usage: scripts/build-apps.sh [--dry-run] [--lang de|en]     (or: ./install.sh build)
# Sourced by install.sh (defines build_apps); runs build_apps when executed directly.

_build_main() {
  local a
  while [ $# -gt 0 ]; do
    a="$1"
    case "$a" in
      --dry-run|-n) DRY_RUN=1 ;;
      --lang) UI_LANG="${2:-en}"; shift ;;
      --lang=*) UI_LANG="${a#*=}" ;;
      -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}"; exit 0 ;;
      *) echo "Unknown option: $a" >&2; exit 2 ;;
    esac
    shift
  done
  case "$UI_LANG" in de|en) ;; *) UI_LANG=en ;; esac
  build_apps
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  set -u
  set -o pipefail
  # shellcheck source-path=SCRIPTDIR source=lib.sh
  . "$(cd "$(dirname "$0")" && pwd -P)/lib.sh"
  UI_LANG="$(lang_from_env)"
fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

_build_missing_toolchain() {
  howto "$(L "JDK 17 - macOS: brew install openjdk@17   Linux: sudo apt install openjdk-17-jdk" \
             "JDK 17 - macOS: brew install openjdk@17   Linux: sudo apt install openjdk-17-jdk")" \
        "$(L "Android SDK - easiest: install Android Studio (https://developer.android.com/studio) and add \"Android 14 (API 34)\", \"Android 11 (API 30)\" and the build tools in its SDK Manager." \
             "Android SDK - am einfachsten: Android Studio installieren (https://developer.android.com/studio) und im SDK Manager \"Android 14 (API 34)\", \"Android 11 (API 30)\" und die Build-Tools hinzufügen.")" \
        "$(L "Without Android Studio: brew install --cask android-commandlinetools, then: sdkmanager \"platforms;android-34\" \"platforms;android-30\" \"build-tools;36.0.0\"" \
             "Ohne Android Studio: brew install --cask android-commandlinetools, dann: sdkmanager \"platforms;android-34\" \"platforms;android-30\" \"build-tools;36.0.0\"")" \
        "$(L "If the SDK is not in ~/Library/Android/sdk (macOS) or ~/Android/Sdk (Linux), set ANDROID_HOME." \
             "Liegt das SDK nicht in ~/Library/Android/sdk (macOS) bzw. ~/Android/Sdk (Linux), ANDROID_HOME setzen.")"
}

build_apps() {
  local voice="$ESL_ROOT/apps/voice" clock="$ESL_ROOT/apps/clock" apk f gradlew
  step "$(L "Build the apps from source" "Apps aus dem Quellcode bauen")"
  if ! find_java; then
    err "$(L "JDK 17 (or newer) not found." "JDK 17 (oder neuer) nicht gefunden.")"
    _build_missing_toolchain
    return 1
  fi
  ok "JDK: $JAVA17_HOME"
  if ! find_android_sdk; then
    err "$(L "Android SDK not found." "Android SDK nicht gefunden.")"
    _build_missing_toolchain
    return 1
  fi
  ok "Android SDK: $ANDROID_SDK"
  [ -d "$ANDROID_SDK/platforms/android-34" ] || warn "$(L "platforms;android-34 missing (needed by apps/voice)" "platforms;android-34 fehlt (braucht apps/voice)")"
  [ -d "$voice" ] && [ -d "$clock" ] || { err "$(L "apps/voice or apps/clock missing" "apps/voice oder apps/clock fehlt")"; return 1; }

  run mkdir -p "$DIST_DIR"

  info "$(L "Spot Voice (Gradle; the first build downloads dependencies and takes a few minutes) ..." \
            "Spot Voice (Gradle; der erste Build lädt Abhängigkeiten und dauert ein paar Minuten) ...")"
  gradlew="./gradlew"
  [ -x "$voice/gradlew" ] || gradlew="sh ./gradlew"
  if is_dry; then
    trace cd "$(rel "$voice")"
    trace env JAVA_HOME="$JAVA17_HOME" ANDROID_HOME="$ANDROID_SDK" "$gradlew" --no-daemon assembleRelease
  else
    # shellcheck disable=SC2086  # $gradlew may be "sh ./gradlew"
    ( cd "$voice" && JAVA_HOME="$JAVA17_HOME" ANDROID_HOME="$ANDROID_SDK" PATH="$JAVA17_HOME/bin:$PATH" \
        $gradlew --no-daemon assembleRelease ) || { err "$(L "Gradle build failed" "Gradle-Build fehlgeschlagen")"; return 1; }
  fi
  apk=""
  if is_dry; then
    apk="$voice/app/build/outputs/apk/release/app-release.apk"
  else
    for f in "$voice"/app/build/outputs/apk/release/*.apk; do
      if [ -f "$f" ] && { [ -z "$apk" ] || [ "$f" -nt "$apk" ]; }; then apk="$f"; fi
    done
    [ -n "$apk" ] || { err "$(L "No APK in apps/voice/app/build/outputs/apk/release/" "Kein APK in apps/voice/app/build/outputs/apk/release/")"; return 1; }
  fi
  run cp "$apk" "$DIST_DIR/spot-voice.apk" || return 1
  ok "$(rel "$DIST_DIR")/spot-voice.apk"

  info "$(L "Spot Clock (build.sh) ..." "Spot Clock (build.sh) ...")"
  if is_dry; then
    trace cd "$(rel "$clock")"
    trace env JAVA_HOME_17="$JAVA17_HOME" ANDROID_HOME="$ANDROID_SDK" bash ./build.sh
  else
    ( cd "$clock" && JAVA_HOME_17="$JAVA17_HOME" ANDROID_HOME="$ANDROID_SDK" bash ./build.sh ) \
      || { err "$(L "Clock build failed" "Clock-Build fehlgeschlagen")"; return 1; }
    [ -f "$clock/build/spot-clock.apk" ] || { err "apps/clock/build/spot-clock.apk $(L "missing" "fehlt")"; return 1; }
  fi
  run cp "$clock/build/spot-clock.apk" "$DIST_DIR/spot-clock.apk" || return 1
  ok "$(rel "$DIST_DIR")/spot-clock.apk"

  if is_dry; then
    trace sh -c "cd dist && shasum -a 256 spot-voice.apk spot-clock.apk > SHA256SUMS"
  else
    (
      cd "$DIST_DIR" || exit 1
      for f in spot-voice.apk spot-clock.apk; do printf '%s  %s\n' "$(sha256_local "$f")" "$f"; done > SHA256SUMS
    ) || { err "SHA256SUMS"; return 1; }
    sed 's/^/     /' "$DIST_DIR/SHA256SUMS"
  fi
  ok "$(L "Built" "Gebaut"): dist/spot-voice.apk, dist/spot-clock.apk, dist/SHA256SUMS"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then _build_main "$@"; fi
