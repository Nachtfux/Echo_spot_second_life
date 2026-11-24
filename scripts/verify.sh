#!/usr/bin/env bash
# verify.sh - phase "verify": a table of checks (✓/✗ + how to fix) and an optional live test
# that opens the assistant's voice mode through Spot Voice. Read-only except the live test.
# Sourced by install.sh; running it directly runs "./install.sh verify".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" verify "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

VERIFY_FAILS=0

# _verify_row <0 ok | 1 fail | 2 unsure | 3 not checked> <label> [fix hint]
_verify_row() {
  local mark
  case "$1" in
    0) mark="${C_GRN}✓${C_RST}" ;;
    1) mark="${C_RED}✗${C_RST}"; VERIFY_FAILS=$((VERIFY_FAILS + 1)) ;;
    2) mark="${C_YEL}?${C_RST}" ;;
    *) mark="${C_DIM}·${C_RST}" ;;
  esac
  printf '   %s %s\n' "$mark" "$2"
  if [ "$1" = 1 ] || [ "$1" = 2 ]; then
    [ -n "${3:-}" ] && printf '       %s-> %s%s\n' "$C_DIM" "$3" "$C_RST"
  fi
  return 0
}

# _verify_check <label> <fix> <command...>: run a check function, print the row.
_verify_check() {
  local label="$1" fix="$2"
  shift 2
  if is_dry; then "$@" >/dev/null; _verify_row 3 "$label ($(L dry-run Probelauf))"; return 0; fi
  if "$@"; then _verify_row 0 "$label"; else _verify_row 1 "$label" "$fix"; fi
}

_v_file_on_spot() { [ -n "$(adb_shq "" "ls $1 2>/dev/null")" ]; }
_v_a11y() {
  setting_has secure enabled_accessibility_services "$A11Y_COMPONENT" || return 1
  [ "$(adb_shq "" settings get secure accessibility_enabled)" = 1 ]
}
_v_home() {
  local out
  out="$(adb_shq "" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME)"
  case "$out" in *"$CLOCK_ACTIVITY"*|*"$CLOCK_PKG/$CLOCK_PKG.ClockActivity"*) return 0 ;; esac
  return 1
}
_v_dream() { [ "$(adb_shq "" settings get secure screensaver_components)" = "$DREAM_COMPONENT" ]; }
_v_service() {
  local out
  out="$(adb_shq "" dumpsys activity services "$VOICE_PKG")"
  case "$out" in *WakeWordService*) return 0 ;; esac
  return 1
}

# Heuristic: a logged-in assistant app keeps noticeably more data. Needs root; never fails verify.
_v_login() {
  local pkg="$1" name="$2" kb
  if is_dry; then adb_sh du -sk "/data/data/$pkg" >/dev/null; _verify_row 3 "$name $(L "logged in (heuristic, dry-run)" "angemeldet (Schätzung, Probelauf)")"; return 0; fi
  kb="$(adb_shq "" du -sk "/data/data/$pkg" 2>/dev/null | awk '{print $1}')"
  case "$kb" in
    ''|*[!0-9]*) _verify_row 2 "$name $(L "logged in? (cannot tell without root)" "angemeldet? (ohne Root nicht feststellbar)")" \
                   "$(L "open the app on the Spot and check" "die App auf dem Spot öffnen und nachsehen")" ;;
    *) if [ "$kb" -ge 10240 ]; then
         _verify_row 0 "$name $(L "probably logged in" "vermutlich angemeldet") ($((kb / 1024)) MB $(L "app data" "App-Daten"))"
       else
         _verify_row 2 "$name $(L "maybe not logged in" "evtl. nicht angemeldet") ($((kb / 1024)) MB $(L "app data" "App-Daten"))" \
           "$(L "./install.sh apps (login steps), Claude: ./install.sh open-link" "./install.sh apps (Anmeldeschritte), Claude: ./install.sh open-link")"
       fi ;;
  esac
}

_verify_live_test() {
  local name="$1" out start found=0
  step "$(L "Live test" "Live-Test")"
  msg "Spot Voice now opens $name voice mode as if you had said the wake word." \
      "Spot Voice öffnet jetzt den $name-Sprachmodus, als hättest du das Wake-Word gesagt."
  ask_yes_no "$(L "Run the live test now?" "Live-Test jetzt starten?")" Y || return 0
  adbx logcat -c
  adb_sh am start-foreground-service -n "$VOICE_SERVICE" -a ai.pocket.heyclaude.TRIGGER_NOW >/dev/null
  hint "$(L "Look at the Spot: $name opens and its voice mode starts." "Auf den Spot schauen: $name öffnet sich und startet den Sprachmodus.")"
  if is_dry; then adb_q "" logcat -d -s HeyClaude >/dev/null; _verify_row 3 "$(L "voice-mode button tapped (dry-run)" "Sprachmodus-Button getippt (Probelauf)")"; return 0; fi
  start=$SECONDS
  while [ $((SECONDS - start)) -lt 25 ]; do
    sleep 2
    out="$(adb_q "" logcat -d -s HeyClaude)"
    case "$out" in *"voice-mode button tapped"*|*"voice mode already open"*) found=1; break ;; esac
  done
  if [ "$found" = 1 ]; then
    _verify_row 0 "$(L "voice-mode button tapped - talk to $name now, or say the stop word to end" \
                      "Sprachmodus-Button getippt - jetzt mit $name sprechen oder zum Beenden das Stoppwort sagen")"
  else
    _verify_row 1 "$(L "voice-mode button was not tapped within 25 s" "Sprachmodus-Button wurde nicht innerhalb von 25 s getippt")" \
      "$(L "is $name logged in? Accessibility on? Logs: adb logcat -s HeyClaude; docs/$UI_LANG/09-troubleshooting.md" \
           "Ist $name angemeldet? Bedienungshilfe an? Logs: adb logcat -s HeyClaude; docs/$UI_LANG/09-troubleshooting.md")"
  fi
}

phase_verify() {
  local a="${CFG_ASSISTANT:-claude}" lang="${CFG_LANG:-$UI_LANG}" pkg name wake stop
  ensure_device || return 1
  ensure_root >/dev/null || true
  pkg="$(assistant_pkg "$a")"
  name="$(assistant_label "$a")"
  wake="$(assistant_wake_model "$a")"
  stop="$(assistant_stop_model "$a")"
  VERIFY_FAILS=0

  step "$(L "Checks" "Prüfungen")"
  _verify_check "$(L "Spot Voice installed" "Spot Voice installiert") ($VOICE_PKG)" "./install.sh voice" pkg_installed "$VOICE_PKG"
  _verify_check "$(L "Spot Clock installed" "Spot Clock installiert") ($CLOCK_PKG)" "./install.sh clock" pkg_installed "$CLOCK_PKG"
  _verify_check "$name $(L "installed" "installiert") ($pkg)" "./install.sh apps" pkg_installed "$pkg"
  if [ "$CFG_SPOTIFY" = 1 ]; then
    _verify_check "Spotify $(L "installed" "installiert")" "./install.sh apps --spotify" pkg_installed "$SPOTIFY_PKG"
  fi
  _verify_check "$(L "Accessibility service enabled" "Bedienungshilfe aktiviert")" \
    "$(L "./install.sh voice, or on the Spot: Settings > Accessibility > Spot Voice" "./install.sh voice, oder auf dem Spot: Einstellungen > Bedienungshilfen > Spot Voice")" _v_a11y
  _verify_check "$(L "Notification access for Spot Clock" "Benachrichtigungszugriff für Spot Clock")" \
    "$(L "./install.sh clock (only needed for the now-playing face)" "./install.sh clock (nur für die Musik-Anzeige nötig)")" \
    setting_has secure enabled_notification_listeners "$LISTENER_COMPONENT"
  _verify_check "$(L "Spot Clock is the home screen" "Spot Clock ist Startbildschirm")" "./install.sh clock" _v_home
  _verify_check "$(L "Spot Clock is the screensaver" "Spot Clock ist Bildschirmschoner")" "./install.sh clock" _v_dream
  if [ -f "$MODELS_DIR/$wake" ]; then
    _verify_check "$(L "Wake-word models on the Spot" "Wake-Word-Modelle auf dem Spot") ($wake, $stop)" "./install.sh voice" \
      _v_file_on_spot "$VOICE_FILES/$wake"
  else
    _verify_row 2 "$(L "No trained $wake - the wake word is \"Alexa\"" "Kein trainiertes $wake - das Wake-Word ist \"Alexa\"")" "./install.sh train"
  fi
  _verify_check "$(L "Vosk speech model on the Spot" "Vosk-Sprachmodell auf dem Spot") (vosk-$lang)" "./install.sh voice --force" \
    _v_file_on_spot "$VOICE_FILES/vosk-$lang/am/final.mdl"
  _verify_check "$(L "WakeWordService running" "WakeWordService läuft")" \
    "$(L "./install.sh voice, or open Spot Voice and tap \"Start listening\"" "./install.sh voice, oder Spot Voice öffnen und \"Zuhören starten\" tippen")" _v_service
  _v_login "$pkg" "$name"

  if [ "$VERIFY_FAILS" -gt 0 ]; then
    err "$(L "$VERIFY_FAILS check(s) failed - see the hints (->) above." "$VERIFY_FAILS Prüfung(en) fehlgeschlagen - siehe Hinweise (->) oben.")"
    see_docs 09-troubleshooting.md
    return 1
  fi
  _verify_live_test "$name"
  if [ "$VERIFY_FAILS" -gt 0 ]; then return 1; fi
  ok "$(L "All checks passed." "Alle Prüfungen bestanden.")"
}
