#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2034  # constants and globals here are used by install.sh and the phase files
#
# lib.sh - shared helpers for install.sh and scripts/<phase>.sh (sourced, never run).
#
# Must stay compatible with macOS /bin/bash 3.2:
#   - no associative arrays, no mapfile/readarray, no ${var,,} / ${var^^}, no |&, no &>>
#   - "${arr[@]}" on an empty array trips `set -u` in bash 3.2 -> ${arr[@]+"${arr[@]}"}
#   - no `case` written literally inside $( ... ) (bash 3.2 parser bug)
#   - use `wc -c` instead of GNU/BSD-specific `stat`, `df -Pk` for free space
#
# Conventions:
#   - every device/network/build action goes through adbx/adb_q/adb_t/fb_t/run/download,
#     which print "+ cmd" and do nothing when DRY_RUN=1
#   - messages are bilingual: L "English" "Deutsch" returns the text for UI_LANG
#   - helpers that compute a value set a global (APK_PATH, REPLY_VALUE, ...) instead of
#     printing it, so `die` never runs inside a subshell by accident

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  echo "lib.sh is sourced by install.sh - run ./install.sh instead." >&2
  exit 1
fi
ESL_LIB_LOADED=1

# Repo root (scripts/..), resolved from this file so the installer works from any cwd.
ESL_ROOT="${ESL_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)}"

# ------------------------------------------------------------- globals ----
# Set from the command line by install.sh (defaults here so every file knows them).
DRY_RUN=0          # 1 = print commands only, never touch device or network
ASSUME_YES=0       # 1 = accept defaults of non-destructive prompts
FORCE=0            # 1 = re-run phases already done / re-push data
FROM_SOURCE=0      # 1 = build the APKs instead of downloading the release
UI_LANG=en         # de|en
DEVICE_FROM_CLI=0  # 1 = --device given on this run (then USB phases honour it too)
CURRENT_PHASE=""
ADB_TARGET=""      # serial or ip:port passed to adb -s (empty = the only USB device)

# Persistent settings (.state/config.env). Prefixed with CFG_ so that LANG/TZ never clobber
# the environment variables of the same name.
CFG_KEYS="ASSISTANT LANG PLACE LAT LON DEVICE SPOTIFY TZ SENSITIVITY"
CFG_ASSISTANT=""   # claude|chatgpt
CFG_LANG=""        # de|en
CFG_PLACE=""       # weather place as resolved by Open-Meteo, "-" = no weather
CFG_LAT=""
CFG_LON=""
CFG_DEVICE=""      # serial or ip:5555 (set by the setup phase)
CFG_SPOTIFY=""     # 1|0
CFG_TZ=""          # e.g. Europe/Berlin
CFG_SENSITIVITY="" # high|normal|low (wake word), empty = normal

STATE_DIR="${ESL_STATE_DIR:-$ESL_ROOT/.state}"
CONFIG_FILE="$STATE_DIR/config.env"
DOWNLOAD_DIR="$ESL_ROOT/downloads"
BACKUP_DIR="$ESL_ROOT/backups"
DIST_DIR="$ESL_ROOT/dist"
MODELS_DIR="$ESL_ROOT/models"

# --------------------------------------------------- facts (docs/SPEC.md §2) ----
AMONET_THREAD_URL="https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-spot-2017-rook.4754878/"
AMONET_ZIP_SHA256="be856ba4c880fc2b8664d326d7b9cbe8e2f74d2cc82718cc4aa6a895c0e2418f"
AMONET_ZIP_NAME="amonet-rook-v2.0.0.zip"
FASTBRICK_SIZE=114294816
ROOK_LK_BUILD="c1c79aa-20220824_171625"

LOS_URL="https://github.com/amazon-oss/releases/releases/download/lineage-18.1-rook-v0.3/lineage-18.1-20251108-UNOFFICIAL-rook.zip"
LOS_SHA256="2755428c124df88ffd3bc6a9c36db16bbe3b7e639883fd1092dc839ac5ae9d13"
LOS_ZIP="lineage-18.1-20251108-UNOFFICIAL-rook.zip"

AURORA_URL="https://f-droid.org/repo/com.aurora.store_76.apk"
AURORA_SHA256="fd9c75d90d0f4a7c132b9b4a5a2cf1992a45e03b8d8ff988b7dcfbc0db2c4d11"
AURORA_SIGNER="5c83c7672b929955dc0a1db89a5e6ae4389e2eae7ec939956041694e5815f532"
AURORA_PKG="com.aurora.store"
SPOTIFY_PKG="com.spotify.music"

VOSK_DE_URL="https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
VOSK_DE_SHA256="b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43"
VOSK_EN_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
VOSK_EN_SHA256="30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"

GEOCODE_URL="https://geocoding-api.open-meteo.com/v1/search"
RELEASE_BASE="https://github.com/Nachtfux/Echo_spot_second_life/releases/latest/download"

VOICE_PKG="ai.pocket.heyclaude"
VOICE_SERVICE="ai.pocket.heyclaude/.WakeWordService"
VOICE_FILES="/sdcard/Android/data/ai.pocket.heyclaude/files"
A11Y_COMPONENT="ai.pocket.heyclaude/ai.pocket.heyclaude.launch.VoiceModeA11yService"
CLOCK_PKG="ai.pocket.spotclock"
CLOCK_ACTIVITY="ai.pocket.spotclock/.ClockActivity"
LISTENER_COMPONENT="ai.pocket.spotclock/ai.pocket.spotclock.NowPlayingService"
DREAM_COMPONENT="ai.pocket.spotclock/ai.pocket.spotclock.ClockDream"

# ---------------------------------------------------------------- colors ----
if [ -t 1 ] && [ "${NO_COLOR:-}" = "" ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_BLU=$'\033[34m'
  C_ORG=$'\033[38;5;173m'; C_BLD=$'\033[1m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
  C_RED=''; C_GRN=''; C_YEL=''; C_BLU=''; C_ORG=''; C_BLD=''; C_DIM=''; C_RST=''
fi

# -------------------------------------------------------------- language ----
# L "English" "Deutsch"   -> prints the text for UI_LANG (no newline)
# msg "English" "Deutsch" -> same, as a line
L() { if [ "$UI_LANG" = de ]; then printf '%s' "$2"; else printf '%s' "$1"; fi; }
msg() { printf '%s\n' "$(L "$1" "$2")"; }

# Language from an environment locale string (de_DE.UTF-8 -> de, everything else -> en).
lang_from_env() {
  local l="${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}"
  case "$l" in de*|DE*) printf 'de' ;; *) printf 'en' ;; esac
}

# ---------------------------------------------------------------- output ----
info()  { printf '%s\n' "${C_BLU}[..]${C_RST} $*"; }
ok()    { printf '%s\n' "${C_GRN}[ok]${C_RST} $*"; }
warn()  { printf '%s\n' "${C_YEL}[!!]${C_RST} $*" >&2; }
err()   { printf '%s\n' "${C_RED}[xx]${C_RST} $*" >&2; }
die()   { err "$*"; exit 1; }
step()  { printf '\n%s\n' "${C_ORG}${C_BLD}==> $*${C_RST}"; }
hint()  { printf '%s\n' "     $*"; }

# howto "first" "second" ...  -> numbered list of things the user does by hand
howto() {
  local i=1 line
  for line in "$@"; do
    printf '   %s%2d.%s %s\n' "$C_BLD" "$i" "$C_RST" "$line"
    i=$((i + 1))
  done
}

# Path of an illustration: prefers the *.<lang>.svg variant (text-bearing images).
img_ref() {
  if [ -f "$ESL_ROOT/docs/images/$1.$UI_LANG.svg" ]; then printf 'docs/images/%s.%s.svg' "$1" "$UI_LANG"
  elif [ -f "$ESL_ROOT/docs/images/$1.svg" ]; then printf 'docs/images/%s.svg' "$1"
  elif [ -f "$ESL_ROOT/docs/images/$1.png" ]; then printf 'docs/images/%s.png' "$1"
  else printf 'docs/images/%s.%s.svg' "$1" "$UI_LANG"; fi
}

# see_docs <chapter.md> [image-name ...]  -> "More help: docs/en/02-unlock.md · docs/images/..."
see_docs() {
  local s img
  s="$(L "More help" "Mehr Hilfe"): docs/$UI_LANG/$1"
  shift
  for img in "$@"; do s="$s  |  $(img_ref "$img")"; done
  printf '     %s%s%s\n' "$C_DIM" "$s" "$C_RST"
}

have() { command -v "$1" >/dev/null 2>&1; }
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
is_dry() { [ "$DRY_RUN" = 1 ]; }

# Path relative to the repo root, for messages.
rel() {
  case "$1" in
    "$ESL_ROOT"/*) printf '%s' "${1#"$ESL_ROOT"/}" ;;
    *) printf '%s' "$1" ;;
  esac
}

# --------------------------------------------------------------- quoting ----
# shq <string>: quote for a POSIX shell (local display or the Spot's `adb shell`).
shq() {
  case "$1" in
    '') printf "''" ;;
    *[!A-Za-z0-9_./:=+@%,-]*) printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")" ;;
    *) printf '%s' "$1" ;;
  esac
}

# trace <cmd...>: print "+ cmd" (dim, on stderr so it is visible inside $(...)).
trace() {
  local s="" a
  for a in "$@"; do
    case "$a" in
      \'*\') s="$s $a" ;;      # already quoted for the remote shell
      *) s="$s $(shq "$a")" ;;
    esac
  done
  printf '%s\n' "${C_DIM}+${s}${C_RST}" >&2
}

# run <cmd...>: run a local command, or only print it in dry-run mode.
run() {
  if is_dry; then trace "$@"; return 0; fi
  "$@"
}

# quiet <cmd...>: hide a command's output; in dry-run its "+ cmd" line stays visible.
quiet() {
  if is_dry; then "$@" >/dev/null; return; fi
  "$@" >/dev/null 2>&1
}

# ----------------------------------------------------------- prompts --------
REPLY_VALUE=""
ANSWER=""

_read_line() {
  ANSWER=""
  if ! IFS= read -r ANSWER; then
    printf '\n'
    die "$(L "No input (stdin closed) - stopping. Use --yes to accept default answers." \
             "Keine Eingabe (stdin geschlossen) - Abbruch. Mit --yes werden Standardantworten genommen.")"
  fi
}

# ask_yes_no "Question" Y|N  -> 0 = yes. --yes takes the default.
ask_yes_no() {
  local q="$1" def="${2:-N}" choices
  if [ "$def" = Y ]; then choices="$(L "[Y/n]" "[J/n]")"; else choices="$(L "[y/N]" "[j/N]")"; fi
  if [ "$ASSUME_YES" = 1 ]; then
    printf '%s\n' "${C_YEL}??${C_RST} $q $choices -> $(if [ "$def" = Y ]; then L Y J; else printf N; fi) (--yes)"
    [ "$def" = Y ]
    return
  fi
  while :; do
    printf '%s' "${C_YEL}??${C_RST} $q $choices "
    _read_line
    case "$(lower "$ANSWER")" in
      '') [ "$def" = Y ]; return ;;
      y|yes|j|ja) return 0 ;;
      n|no|nein) return 1 ;;
      *) warn "$(L "Please answer y or n." "Bitte j oder n eingeben.")" ;;
    esac
  done
}

# ask_value "Prompt" "default" -> REPLY_VALUE (default on Enter or with --yes)
ask_value() {
  local q="$1" def="${2:-}" shown=""
  [ -n "$def" ] && shown=" [$def]"
  if [ "$ASSUME_YES" = 1 ]; then
    printf '%s\n' "${C_YEL}??${C_RST} $q$shown -> ${def:-$(L "(empty)" "(leer)")} (--yes)"
    REPLY_VALUE="$def"
    return 0
  fi
  printf '%s' "${C_YEL}??${C_RST} $q$shown: "
  _read_line
  if [ -z "$ANSWER" ]; then REPLY_VALUE="$def"; else REPLY_VALUE="$ANSWER"; fi
}

# confirm_destructive "what will happen" -> 0 only if the user types YES (or JA).
# --yes never skips this; only --yes together with --dry-run auto-confirms (for testing).
confirm_destructive() {
  local word
  word="$(L YES JA)"
  printf '\n%s\n' "${C_RED}${C_BLD}!! $1${C_RST}"
  if [ "$ASSUME_YES" = 1 ] && is_dry; then
    printf '%s\n' "   $(L "Type $word to continue" "$word eintippen zum Fortfahren") -> $word (--dry-run --yes)"
    return 0
  fi
  printf '%s' "   $(L "Type $word to continue, anything else stops: " "$word eintippen zum Fortfahren, alles andere bricht ab: ")"
  _read_line
  [ "$ANSWER" = YES ] || [ "$ANSWER" = JA ]
}

# ask_phase <name> -> 0 run, 1 skip, 2 stop
ask_phase() {
  local q
  q="$(L "Run phase \"$1\" now? [Y/n/skip]" "Phase \"$1\" jetzt ausführen? [J/n/skip]")"
  if [ "$ASSUME_YES" = 1 ]; then printf '%s\n' "${C_YEL}??${C_RST} $q -> $(L Y J) (--yes)"; return 0; fi
  while :; do
    printf '%s\n' "     $(L "(Y/Enter = run it, n = stop here and continue later, s/skip = skip this phase)" \
                             "(J/Enter = ausführen, n = hier aufhören und später weitermachen, s/skip = Phase überspringen)")"
    printf '%s' "${C_YEL}??${C_RST} $q "
    _read_line
    case "$(lower "$ANSWER")" in
      ''|y|yes|j|ja) return 0 ;;
      s|skip) return 1 ;;
      n|no|nein|q|quit) return 2 ;;
      *) warn "$(L "Please answer y, n or skip." "Bitte j, n oder skip eingeben.")" ;;
    esac
  done
}

# pause_enter "message": wait for Enter (not with --yes).
pause_enter() {
  if [ "$ASSUME_YES" = 1 ]; then
    printf '%s\n' "${C_BLU}>>${C_RST} ${1:-$(L "Press Enter to continue" "Weiter mit Enter")} (--yes)"
    return 0
  fi
  printf '%s' "${C_BLU}>>${C_RST} ${1:-$(L "Press Enter to continue" "Weiter mit Enter")} "
  _read_line
}

# nap <secs>: sleep; returns 0 early if the user pressed Enter (interactive only).
nap() {
  local _nap_line
  if [ "$ASSUME_YES" = 0 ] && [ -t 0 ]; then
    if IFS= read -r -t "$1" _nap_line; then return 0; fi
    return 1
  fi
  sleep "$1"
  return 1
}

# --------------------------------------------------------------- timeout ----
TIMEOUT_BIN=""   # gtimeout | timeout | none (detected once)

detect_timeout() {
  local v
  if have gtimeout; then TIMEOUT_BIN=gtimeout; return 0; fi
  if have timeout; then
    v="$(timeout --version 2>/dev/null | head -n 1)"
    case "$v" in *GNU*|*coreutils*) TIMEOUT_BIN=timeout; return 0 ;; esac
  fi
  TIMEOUT_BIN=none
}

# run_with_timeout <secs> <binary> [args...] -> exit code 124 when the time ran out
# (same contract as GNU timeout; falls back to a small bash implementation that sends
# SIGTERM to the command itself - enough for adb/fastboot, which are single processes).
run_with_timeout() {
  local secs="$1" pid watcher ec
  shift
  [ -n "$TIMEOUT_BIN" ] || detect_timeout
  if [ "$TIMEOUT_BIN" != none ]; then
    "$TIMEOUT_BIN" "$secs" "$@"
    return $?
  fi
  "$@" &
  pid=$!
  ( sleep "$secs"; kill -TERM "$pid" 2>/dev/null ) >/dev/null 2>&1 &
  watcher=$!
  wait "$pid" 2>/dev/null; ec=$?
  kill "$watcher" 2>/dev/null
  wait "$watcher" 2>/dev/null
  [ "$ec" -eq 143 ] && ec=124
  return "$ec"
}

# --------------------------------------------------------------- hashing ----
sha256_tool() {
  if have shasum; then printf 'shasum -a 256'
  elif have sha256sum; then printf 'sha256sum'
  else return 1; fi
}

sha256_local() {
  if have shasum; then shasum -a 256 "$1" | awk '{print $1}'
  elif have sha256sum; then sha256sum "$1" | awk '{print $1}'
  else return 1; fi
}

file_size() { wc -c < "$1" | tr -d ' '; }

# ------------------------------------------------------ partition guard -----
# These partitions hold the boot chain. Writing to them can hard-brick the Spot
# (only recoverable by opening it). Nothing in this installer writes to ANY block
# device; this guard is a second, explicit safety net for every adb/fastboot call.
guard_write() {
  local cmd lc
  cmd=" $* "
  lc="$(lower "$cmd")"
  local blk_re='(of=|>[[:space:]]*)/dev/block'
  local prot_re='[[:space:]/](preloader|lk|lk_real|tee[0-9a-z_]*|mmcblk0boot[01])(\.img)?[[:space:]]'
  local write_re='([[:space:]](flash|erase|format|wipe)[[:space:]]|of=)'
  if [[ "$lc" =~ $blk_re ]]; then
    die "SAFETY GUARD: refusing to write to a block device:$cmd"
  fi
  if [[ "$lc" =~ $prot_re ]] && [[ "$lc" =~ $write_re ]]; then
    die "SAFETY GUARD: refusing to touch a boot-chain partition (preloader/lk/tee/boot0/boot1):$cmd"
  fi
  return 0
}

# -------------------------------------------------------------------- adb ----
# adbx <args...>: adb with -s "$ADB_TARGET" when set; prints only in dry-run.
adbx() {
  guard_write "$@"
  if [ -n "$ADB_TARGET" ]; then set -- -s "$ADB_TARGET" "$@"; fi
  if is_dry; then trace adb "$@"; return 0; fi
  adb "$@" </dev/null
}

# adb_q <dry-run placeholder> <args...>: adb output without CR (placeholder in dry-run).
adb_q() {
  local ph="$1"
  shift
  if is_dry; then adbx "$@"; printf '%s\n' "$ph"; return 0; fi
  adbx "$@" 2>/dev/null | tr -d '\r'
}

adb_sh() { adbx shell "$@"; }
# adb_shq <placeholder> <remote command...>
adb_shq() { local ph="$1"; shift; adb_q "$ph" shell "$@"; }

# adb_t <secs> <args...>: adb with a time limit (calls the binary, so GNU timeout works).
adb_t() {
  local secs="$1"
  shift
  guard_write "$@"
  if [ -n "$ADB_TARGET" ]; then set -- -s "$ADB_TARGET" "$@"; fi
  if is_dry; then trace adb "$@"; return 0; fi
  run_with_timeout "$secs" adb "$@" </dev/null
}

# fb_t <secs> <args...>: fastboot (from PATH) with a time limit.
fb_t() {
  local secs="$1"
  shift
  guard_write fastboot "$@"
  if is_dry; then trace fastboot "$@"; return 0; fi
  run_with_timeout "$secs" fastboot "$@" </dev/null
}

adb_count() {
  run_with_timeout 10 adb devices 2>/dev/null | tr -d '\r' | awk 'NR>1 && NF>=2 {n++} END {print n+0}'
}

# adb_state -> device | recovery | sideload | unauthorized | offline | multiple | none
adb_state() {
  local s
  if is_dry; then printf 'none'; return 0; fi
  if [ -z "$ADB_TARGET" ] && [ "$(adb_count)" -gt 1 ]; then printf 'multiple'; return 0; fi
  s="$(adb_t 10 get-state 2>/dev/null | tr -d '\r')"
  if [ -z "$s" ]; then
    s="$(run_with_timeout 10 adb devices 2>/dev/null | tr -d '\r' \
         | awk -v t="$ADB_TARGET" 'NR>1 && NF>=2 && (t == "" || $1 == t) {print $2; exit}')"
  fi
  printf '%s' "${s:-none}"
}

# wait_adb_state <device|recovery> <timeout secs>: polls; Enter re-checks at once.
wait_adb_state() {
  local want="$1" limit="$2" start st last=0 told_unauth=0 told_multi=0
  if is_dry; then trace adb ${ADB_TARGET:+-s "$ADB_TARGET"} wait-for-"$want"; return 0; fi
  start=$SECONDS
  while :; do
    st="$(adb_state)"
    [ "$st" = "$want" ] && return 0
    case "$st" in
      unauthorized)
        if [ "$told_unauth" = 0 ]; then
          warn "$(L "The Spot asks for permission: tap \"Allow\" in the \"Allow USB debugging?\" dialog on its screen (tick \"Always allow\")." \
                    "Der Spot fragt nach Erlaubnis: im Dialog \"USB-Debugging zulassen?\" auf dem Display \"Zulassen\" tippen (\"Immer zulassen\" anhaken).")"
          told_unauth=1
        fi ;;
      multiple)
        if [ "$told_multi" = 0 ]; then
          warn "$(L "More than one Android device is connected. Unplug the others or choose one with --device <serial>." \
                    "Mehr als ein Android-Gerät ist verbunden. Die anderen abstecken oder mit --device <Seriennummer> eines wählen.")"
          adb devices -l 2>/dev/null | sed 's/^/     /'
          told_multi=1
        fi ;;
    esac
    if [ $((SECONDS - start)) -ge "$limit" ]; then return 1; fi
    if [ $((SECONDS - last)) -ge 30 ]; then
      info "$(L "Waiting for the Spot ($want) - $((SECONDS - start)) s, now: $st. (Enter = check again)" \
                "Warte auf den Spot ($want) - $((SECONDS - start)) s, jetzt: $st. (Enter = sofort prüfen)")"
      last=$SECONDS
    fi
    nap 3 || true
  done
}

# wait_fastboot <timeout secs>
wait_fastboot() {
  local limit="$1" start last=0 out
  if is_dry; then trace fastboot devices; return 0; fi
  start=$SECONDS
  while :; do
    out="$(run_with_timeout 10 fastboot devices 2>/dev/null | tr -d '\r')"
    [ -n "$out" ] && return 0
    if [ $((SECONDS - start)) -ge "$limit" ]; then return 1; fi
    if [ $((SECONDS - last)) -ge 30 ]; then
      info "$(L "Waiting for the Spot in fastboot mode - $((SECONDS - start)) s. (Enter = check again)" \
                "Warte auf den Spot im Fastboot-Modus - $((SECONDS - start)) s. (Enter = sofort prüfen)")"
      last=$SECONDS
    fi
    nap 3 || true
  done
}

need_adb() {
  have adb && return 0
  err "$(L "adb not found. Run ./install.sh doctor - it can install it for you." \
           "adb nicht gefunden. ./install.sh doctor ausführen - es kann adb installieren.")"
  return 1
}

is_network_target() { case "$1" in *:*) return 0 ;; esac; return 1; }

# USB-only phases (unlock, backup, flash, start of setup): a stored Wi-Fi address does not
# apply there, unless --device was given on this run.
use_usb() {
  ADB_TARGET="$CFG_DEVICE"
  if is_network_target "$ADB_TARGET" && [ "$DEVICE_FROM_CLI" = 0 ]; then ADB_TARGET=""; fi
}

# ensure_device: the Spot booted into Android and reachable (Wi-Fi address from setup, else USB).
ensure_device() {
  ADB_TARGET="$CFG_DEVICE"
  if is_dry; then
    if is_network_target "$ADB_TARGET"; then trace adb connect "$ADB_TARGET"; fi
    return 0
  fi
  need_adb || return 1
  if is_network_target "$ADB_TARGET"; then
    [ "$(adb_state)" = device ] && return 0
    info "$(L "Connecting to the Spot over Wi-Fi ($ADB_TARGET) ..." "Verbinde mit dem Spot über WLAN ($ADB_TARGET) ...")"
    run_with_timeout 15 adb connect "$ADB_TARGET" >/dev/null 2>&1
    sleep 1
    if [ "$(adb_state)" = device ]; then ok "$(L "Connected" "Verbunden"): $ADB_TARGET"; return 0; fi
    warn "$(L "The Spot does not answer at $ADB_TARGET." "Der Spot antwortet nicht unter $ADB_TARGET.")"
    hint "$(L "adb over Wi-Fi switches off when the Spot reboots (or its IP address changed). Use the cable once:" \
              "adb über WLAN schaltet sich bei jedem Neustart des Spot ab (oder die IP-Adresse hat sich geändert). Einmal per Kabel:")"
    howto "$(L "Connect the micro-USB cable (hidden port on the back) to this computer." \
               "Das Micro-USB-Kabel (versteckter Anschluss hinten) mit diesem Computer verbinden.")" \
          "$(L "Wait until the Spot shows its clock or home screen." "Warten, bis der Spot Uhr oder Startbildschirm zeigt.")"
    ADB_TARGET=""
    wait_adb_state device 600 || { err "$(L "No Spot found on USB either." "Auch per USB kein Spot gefunden.")"; return 1; }
    if ask_yes_no "$(L "Switch adb over Wi-Fi on again (cable can be removed afterwards)?" \
                       "adb über WLAN wieder einschalten (danach kann das Kabel ab)?")" Y; then
      enable_wifi_adb || true
    fi
    return 0
  fi
  [ "$(adb_state)" = device ] && return 0
  info "$(L "Waiting for the Spot (Android running, USB debugging on, cable connected) ..." \
            "Warte auf den Spot (Android läuft, USB-Debugging an, Kabel verbunden) ...")"
  wait_adb_state device 600 || { err "$(L "No Spot found." "Kein Spot gefunden.")"; return 1; }
}

# ensure_root: adbd as root (needs "Rooted debugging" in Developer options).
ensure_root() {
  local uid out
  if is_dry; then adbx root; return 0; fi
  uid="$(adb_shq "" id -u)"
  [ "$uid" = 0 ] && return 0
  info "$(L "Switching adb to root mode ..." "Schalte adb in den Root-Modus ...")"
  out="$(adbx root 2>&1 | tr -d '\r')"
  sleep 3
  if is_network_target "$ADB_TARGET"; then run_with_timeout 15 adb connect "$ADB_TARGET" >/dev/null 2>&1; fi
  wait_adb_state device 60 || true
  uid="$(adb_shq "" id -u)"
  if [ "$uid" = 0 ]; then ok "$(L "adb runs as root" "adb läuft als root")"; return 0; fi
  warn "$(L "adb could not switch to root" "adb konnte nicht auf root umschalten"): ${out:-?}"
  howto "$(L "On the Spot: Settings > System > Advanced > Developer options." \
             "Auf dem Spot: Einstellungen > System > Erweitert > Entwickleroptionen.")" \
        "$(L "Switch on \"Rooted debugging\"." "\"Debugging als Root\" (Rooted debugging) einschalten.")"
  return 1
}

# enable_wifi_adb: adb tcpip 5555 and remember ip:5555 as DEVICE.
enable_wifi_adb() {
  local out ip prev="$ADB_TARGET"
  out="$(adb_shq "    inet 192.168.x.y/24 brd 192.168.x.255 scope global wlan0" ip -f inet addr show wlan0)"
  ip="$(printf '%s\n' "$out" | awk '/inet / {sub(/\/.*/, "", $2); print $2; exit}')"
  if [ -z "$ip" ]; then
    warn "$(L "The Spot has no Wi-Fi address yet." "Der Spot hat noch keine WLAN-Adresse.")"
    hint "$(L "Connect it: Settings > Network & internet > Wi-Fi." "Verbinden: Einstellungen > Netzwerk & Internet > WLAN.")"
    return 1
  fi
  quiet adbx tcpip 5555 || true
  is_dry || sleep 3
  ADB_TARGET="$ip:5555"
  if is_dry; then trace adb connect "$ADB_TARGET"; else run_with_timeout 15 adb connect "$ADB_TARGET" >/dev/null 2>&1; fi
  if ! wait_adb_state device 30; then
    warn "$(L "adb over Wi-Fi did not answer - staying on USB." "adb über WLAN antwortet nicht - bleibe bei USB.")"
    ADB_TARGET="$prev"
    return 1
  fi
  CFG_DEVICE="$ADB_TARGET"
  config_save
  ok "$(L "adb over Wi-Fi: $ADB_TARGET (saved). You can unplug the USB cable now - keep the power plugged in." \
          "adb über WLAN: $ADB_TARGET (gespeichert). Das USB-Kabel kann jetzt ab - das Netzteil bleibt dran.")"
  hint "$(L "After a reboot of the Spot this is off again; plug in the cable and run any ./install.sh command." \
            "Nach einem Neustart des Spot ist das wieder aus; Kabel anstecken und einen beliebigen ./install.sh-Befehl starten.")"
}

# pkg_installed <pkg> [dry-run answer: 1 = not installed (default), 0 = installed]
pkg_installed() {
  local out
  if is_dry; then trace adb ${ADB_TARGET:+-s "$ADB_TARGET"} shell pm list packages "$1"; return "${2:-1}"; fi
  out="$(adb_shq "" pm list packages "$1")"
  printf '%s\n' "$out" | grep -qx "package:$1"
}

# wait_package <pkg> <timeout secs> -> 0 installed, 1 timeout/skipped
wait_package() {
  local pkg="$1" limit="$2" start last=0
  if is_dry; then trace adb ${ADB_TARGET:+-s "$ADB_TARGET"} shell pm list packages "$pkg"; return 0; fi
  start=$SECONDS
  while :; do
    pkg_installed "$pkg" && return 0
    [ $((SECONDS - start)) -ge "$limit" ] && return 1
    if [ $((SECONDS - last)) -ge 30 ]; then
      info "$(L "Waiting for $pkg to be installed - $((SECONDS - start)) s (Enter = stop waiting)" \
                "Warte auf die Installation von $pkg - $((SECONDS - start)) s (Enter = nicht mehr warten)")"
      last=$SECONDS
    fi
    if nap 5; then pkg_installed "$pkg"; return; fi
  done
}

# setting_has <namespace> <key> <component>
setting_has() {
  local v
  v="$(adb_shq "" settings get "$1" "$2")"
  case ":$v:" in *":$3:"*) return 0 ;; esac
  return 1
}

# wait_setting <namespace> <key> <component> <timeout> -> 0 when set, 1 timeout/skipped
wait_setting() {
  local start last=0
  if is_dry; then trace adb ${ADB_TARGET:+-s "$ADB_TARGET"} shell settings get "$1" "$2"; return 0; fi
  start=$SECONDS
  while :; do
    setting_has "$1" "$2" "$3" && return 0
    [ $((SECONDS - start)) -ge "$4" ] && return 1
    if [ $((SECONDS - last)) -ge 30 ]; then
      info "$(L "Waiting until you switched it on - $((SECONDS - start)) s (Enter = continue without it)" \
                "Warte, bis du es eingeschaltet hast - $((SECONDS - start)) s (Enter = ohne weitermachen)")"
      last=$SECONDS
    fi
    if nap 3; then setting_has "$1" "$2" "$3"; return; fi
  done
}

# install_apk <apk> <package>: adb install -r, offers an uninstall on a signature clash.
install_apk() {
  local apk="$1" pkg="$2" out
  info "$(L "Installing" "Installiere") $(rel "$apk") ($pkg) ..."
  if is_dry; then adbx install -r "$apk"; return 0; fi
  out="$(adbx install -r "$apk" 2>&1 | tr -d '\r')"
  case "$out" in
    *Success*) ok "$pkg $(L "installed" "installiert")"; return 0 ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*"signatures do not match"*)
      warn "$(L "An older $pkg with a different signature is installed." "Ein älteres $pkg mit anderer Signatur ist installiert.")"
      if ask_yes_no "$(L "Uninstall it (its settings are lost) and install again?" \
                         "Deinstallieren (seine Einstellungen gehen verloren) und neu installieren?")" Y; then
        quiet adbx uninstall "$pkg"
        out="$(adbx install -r "$apk" 2>&1 | tr -d '\r')"
        case "$out" in *Success*) ok "$pkg $(L "installed" "installiert")"; return 0 ;; esac
      fi ;;
  esac
  err "$(L "Install failed" "Installation fehlgeschlagen"): $out"
  return 1
}

# --------------------------------------------------------- apksigner/java ----
APKSIGNER=""
JAVA17_HOME=""
ANDROID_SDK=""

# find_java: JDK 17 or newer -> JAVA17_HOME
find_java() {
  local c v major jh=""
  if [ -x /usr/libexec/java_home ]; then jh="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"; fi
  for c in "${JAVA_HOME:-}" "$jh" /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 \
           /opt/homebrew/opt/openjdk /usr/local/opt/openjdk \
           /usr/lib/jvm/java-17-openjdk* /usr/lib/jvm/temurin-17* /usr/lib/jvm/java-21-openjdk*; do
    [ -n "$c" ] && [ -x "$c/bin/java" ] || continue
    v="$("$c/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
    major="${v%%.*}"
    case "$major" in ''|*[!0-9]*) continue ;; esac
    if [ "$major" -ge 17 ]; then JAVA17_HOME="$c"; return 0; fi
  done
  return 1
}

# find_android_sdk -> ANDROID_SDK
find_android_sdk() {
  local s
  for s in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -n "$s" ] && [ -d "$s/build-tools" ] && [ -d "$s/platforms" ]; then ANDROID_SDK="$s"; return 0; fi
  done
  return 1
}

# find_apksigner -> APKSIGNER (PATH first, then the newest build-tools of the Android SDK)
find_apksigner() {
  local d
  if [ -n "$APKSIGNER" ]; then return 0; fi
  if have apksigner; then APKSIGNER="$(command -v apksigner)"; return 0; fi
  find_android_sdk || return 1
  for d in "$ANDROID_SDK"/build-tools/*/; do
    if [ -x "${d}apksigner" ]; then APKSIGNER="${d}apksigner"; fi
  done
  [ -n "$APKSIGNER" ]
}

# apk_signer <apk> -> SHA-256 digest of signer #1 (empty when it does not verify)
apk_signer() {
  local jh=""
  find_apksigner || return 1
  if find_java; then jh="$JAVA17_HOME"; fi
  if [ -n "$jh" ]; then
    JAVA_HOME="$jh" PATH="$jh/bin:$PATH" "$APKSIGNER" verify --print-certs "$1" 2>/dev/null
  else
    "$APKSIGNER" verify --print-certs "$1" 2>/dev/null
  fi | awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}'
}

# -------------------------------------------------------------- downloads ----
# download <url> <dest>: plain download (curl), dest written atomically.
download() {
  local url="$1" dest="$2"
  if is_dry; then trace curl -fL --retry 3 -o "$(rel "$dest")" "$url"; return 0; fi
  have curl || { err "$(L "curl not found" "curl nicht gefunden")"; return 1; }
  mkdir -p "$(dirname "$dest")"
  if ! curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 --progress-bar -o "$dest.part" "$url"; then
    rm -f "$dest.part"
    return 1
  fi
  mv -f "$dest.part" "$dest"
}

# check_sha256 <file> <expected> -> 0 if equal (prints result)
check_sha256() {
  local got
  if is_dry; then trace shasum -a 256 "$(rel "$1")"; return 0; fi
  got="$(sha256_local "$1")"
  if [ "$got" = "$2" ]; then ok "sha256 $(L "verified" "geprüft"): $(rel "$1")"; return 0; fi
  err "$(L "Checksum mismatch for" "Prüfsumme falsch bei") $(rel "$1")"
  hint "$(L "expected" "erwartet"): $2"
  hint "$(L "got     " "erhalten"): ${got:-?}"
  return 1
}

# download_verified <url> <dest> <sha256>: reuse when present and correct, else download + verify.
download_verified() {
  local url="$1" dest="$2" want="$3"
  if [ -f "$dest" ]; then
    if [ "$(sha256_local "$dest")" = "$want" ]; then
      ok "$(L "Already downloaded (sha256 ok)" "Bereits geladen (sha256 ok)"): $(rel "$dest")"
      return 0
    fi
    warn "$(L "$(rel "$dest") is incomplete or wrong - downloading it again." "$(rel "$dest") ist unvollständig oder falsch - lade neu.")"
  fi
  info "$(L "Downloading" "Lade") $(basename "$dest") ..."
  if ! download "$url" "$dest"; then
    err "$(L "Download failed" "Download fehlgeschlagen"): $url"
    hint "$(L "Check the internet connection and run the same command again." "Internetverbindung prüfen und denselben Befehl noch einmal starten.")"
    return 1
  fi
  if ! check_sha256 "$dest" "$want"; then
    is_dry || mv -f "$dest" "$dest.bad"
    hint "$(L "The file was renamed to $(rel "$dest").bad. Run the command again to download it fresh." \
              "Die Datei wurde in $(rel "$dest").bad umbenannt. Befehl erneut starten, um sie neu zu laden.")"
    return 1
  fi
}

# ---------------------------------------------------------- app releases ----
APK_PATH=""
RELEASE_SUMS_OK=0
APPS_BUILT=0

# obtain_apk <spot-voice|spot-clock> -> APK_PATH (latest GitHub release, or built with --from-source)
obtain_apk() {
  local name="$1" sums="$DOWNLOAD_DIR/release/SHA256SUMS" want
  APK_PATH=""
  if [ "$FROM_SOURCE" = 1 ]; then
    if [ "$APPS_BUILT" = 0 ]; then
      build_apps || return 1
      APPS_BUILT=1
    fi
    APK_PATH="$DIST_DIR/$name.apk"
    if ! is_dry && [ ! -f "$APK_PATH" ]; then err "$(rel "$APK_PATH") $(L "is missing after the build" "fehlt nach dem Bauen")"; return 1; fi
    return 0
  fi
  if [ "$RELEASE_SUMS_OK" = 0 ]; then
    info "$(L "Fetching the checksums of the latest release ..." "Lade die Prüfsummen des neuesten Releases ...")"
    if ! download "$RELEASE_BASE/SHA256SUMS" "$sums"; then
      err "$(L "Could not download the app release from GitHub" "Konnte das App-Release nicht von GitHub laden"): $RELEASE_BASE"
      hint "$(L "No internet, or no release published yet. You can build the apps yourself: add --from-source" \
                "Kein Internet oder noch kein Release veröffentlicht. Die Apps lassen sich selbst bauen: --from-source anhängen")"
      if [ -f "$DIST_DIR/$name.apk" ] && ask_yes_no "$(L "Use the locally built dist/$name.apk instead?" \
                                                         "Stattdessen das lokal gebaute dist/$name.apk nehmen?")" Y; then
        APK_PATH="$DIST_DIR/$name.apk"
        return 0
      fi
      return 1
    fi
    RELEASE_SUMS_OK=1
  fi
  if is_dry; then
    want="<sha256-from-SHA256SUMS>"
  else
    want="$(awk -v f="$name.apk" '$2 == f || $2 == "*" f {print $1; exit}' "$sums")"
  fi
  if [ -z "$want" ]; then err "$name.apk $(L "is not listed in SHA256SUMS of the release" "fehlt in SHA256SUMS des Releases")"; return 1; fi
  download_verified "$RELEASE_BASE/$name.apk" "$DOWNLOAD_DIR/release/$name.apk" "$want" || return 1
  APK_PATH="$DOWNLOAD_DIR/release/$name.apk"
}

# ------------------------------------------------------------- assistants ----
assistant_label() { if [ "$1" = chatgpt ]; then printf 'ChatGPT'; else printf 'Claude'; fi; }
assistant_pkg()   { if [ "$1" = chatgpt ]; then printf 'com.openai.chatgpt'; else printf 'com.anthropic.claude'; fi; }
assistant_wake_model() { if [ "$1" = chatgpt ]; then printf 'hey_chatgpt.onnx'; else printf 'hey_claude.onnx'; fi; }
assistant_stop_model() { if [ "$1" = chatgpt ]; then printf 'danke_chatgpt.onnx'; else printf 'danke_claude.onnx'; fi; }
# Wake/stop thresholds sent with SET_THRESHOLD. Measured with training/evaluate.py on 10.7 h of
# real-world audio (hey_claude v2): the number of consecutive 80 ms frames above the threshold
# matters far more than the threshold itself.
#   sensitivity  frames  recall (synthetic)  false wakes/h
#   high           3          63 %               0.93
#   normal         4          55 %               0.28
#   low            5          45 %               0.00
assistant_wake_threshold() { printf '0.6'; }
assistant_stop_threshold() { printf '0.5'; }
sensitivity_frames() {
  case "${1:-normal}" in high) printf '3' ;; low) printf '5' ;; *) printf '4' ;; esac
}

# ------------------------------------------------------------------ state ----
cfg_get() { local n="CFG_$1"; printf '%s' "${!n:-}"; }

# Values must stay sourceable and parseable: strip " \ $ ` CR LF.
clean_value() { printf '%s' "$1" | tr -d '\042\134\044\140\r\n'; }

config_load() {
  local line key val
  [ -f "$CONFIG_FILE" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    key="${line%%=*}"
    val="${line#*=}"
    val="${val#\"}"
    val="${val%\"}"
    case "$key" in ''|*[!A-Z_]*) continue ;; esac
    case " $CFG_KEYS " in *" $key "*) printf -v "CFG_$key" '%s' "$val" ;; esac
  done < "$CONFIG_FILE"
}

config_save() {
  local k v tmp summary=""
  for k in $CFG_KEYS; do
    v="$(clean_value "$(cfg_get "$k")")"
    printf -v "CFG_$k" '%s' "$v"
    summary="$summary $k=$v"
  done
  if is_dry; then
    printf '%s\n' "${C_DIM}+ (save $(rel "$CONFIG_FILE"):$summary)${C_RST}" >&2
    return 0
  fi
  mkdir -p "$STATE_DIR"
  tmp="$CONFIG_FILE.tmp.$$"
  {
    echo "# Echo Spot Second Life - settings written by install.sh (local only, never commit)"
    for k in $CFG_KEYS; do printf '%s="%s"\n' "$k" "$(cfg_get "$k")"; done
  } > "$tmp" && mv -f "$tmp" "$CONFIG_FILE"
}

RUN_DONE=" "   # phases finished during this run (also in dry-run, where nothing is written)

is_done() {
  [ -f "$STATE_DIR/done/$1" ] && return 0
  case "$RUN_DONE" in *" $1 "*) return 0 ;; esac
  return 1
}
done_when() {
  if [ -f "$STATE_DIR/done/$1" ]; then tr -d '\n' < "$STATE_DIR/done/$1"
  elif is_done "$1"; then L "this run" "in diesem Lauf"; fi
}

mark_done() {
  RUN_DONE="$RUN_DONE$1 "
  if is_dry; then printf '%s\n' "${C_DIM}+ (mark phase \"$1\" done in $(rel "$STATE_DIR")/done/)${C_RST}" >&2; return 0; fi
  mkdir -p "$STATE_DIR/done"
  date '+%Y-%m-%d %H:%M' > "$STATE_DIR/done/$1"
}
