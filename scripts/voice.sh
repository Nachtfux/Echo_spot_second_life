#!/usr/bin/env bash
# voice.sh - phase "voice": installs Spot Voice (ai.pocket.heyclaude), pushes the wake/stop
# models and the Vosk speech model, grants permissions, (after asking) enables the
# accessibility service and selects assistant, language and thresholds by broadcast.
# Sourced by install.sh; running it directly runs "./install.sh voice".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" voice "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# _voice_models <assistant>: push wake + stop model from models/ (fallback: built-in "Alexa").
_voice_models() {
  local a="$1" wake stop f missing=0
  wake="$(assistant_wake_model "$a")"
  stop="$(assistant_stop_model "$a")"
  step "$(L "Wake-word models" "Wake-Word-Modelle")"
  for f in "$wake" "$stop"; do
    if [ ! -f "$MODELS_DIR/$f" ]; then missing=1; continue; fi
    adbx push "$MODELS_DIR/$f" "$VOICE_FILES/$f" >/dev/null || { err "adb push $f"; return 1; }
    ok "$f -> $VOICE_FILES/"
  done
  if [ "$missing" = 1 ]; then
    warn "$(L "models/$wake or models/$stop is missing - Spot Voice uses its built-in wake word \"Alexa\" for $(assistant_label "$a") until a trained model exists." \
              "models/$wake oder models/$stop fehlt - Spot Voice nutzt für $(assistant_label "$a") das eingebaute Wake-Word \"Alexa\", bis ein trainiertes Modell da ist.")"
    hint "$(L "Train your own: ./install.sh train   (docs/$UI_LANG/08-wakeword-training.md)" \
              "Eigenes trainieren: ./install.sh train   (docs/$UI_LANG/08-wakeword-training.md)")"
  fi
}

# _voice_vosk <de|en>: download, unpack and push the small Vosk model (folder contents).
_voice_vosk() {
  local lang="$1" url sha zip dir remote="$VOICE_FILES/vosk-$1" present
  if [ "$lang" = de ]; then url="$VOSK_DE_URL"; sha="$VOSK_DE_SHA256"; else url="$VOSK_EN_URL"; sha="$VOSK_EN_SHA256"; fi
  zip="$DOWNLOAD_DIR/$(basename "$url")"
  dir="$DOWNLOAD_DIR/$(basename "$url" .zip)"
  step "$(L "Offline speech model for voice commands (Vosk, $lang)" "Offline-Sprachmodell für Sprachbefehle (Vosk, $lang)")"
  if ! is_dry && [ "$FORCE" = 0 ]; then
    present="$(adb_shq "" "ls $remote/am/final.mdl 2>/dev/null")"
    if [ -n "$present" ]; then ok "$(L "already on the Spot" "schon auf dem Spot"): $remote"; return 0; fi
  fi
  download_verified "$url" "$zip" "$sha" || return 1
  if is_dry || [ ! -f "$dir/am/final.mdl" ]; then
    run unzip -q -o "$zip" -d "$DOWNLOAD_DIR" || { err "$(L "unzip failed" "Entpacken fehlgeschlagen")"; return 1; }
  fi
  if ! is_dry && [ ! -f "$dir/am/final.mdl" ]; then
    err "$(L "Unexpected zip layout: $(rel "$dir")/am/final.mdl missing" "Unerwarteter ZIP-Inhalt: $(rel "$dir")/am/final.mdl fehlt")"
    return 1
  fi
  info "$(L "Copying the model to the Spot (about 50 MB) ..." "Kopiere das Modell auf den Spot (ca. 50 MB) ...")"
  quiet adb_sh rm -rf "$remote" || true
  adbx push "$dir" "$remote" >/dev/null || { err "adb push vosk"; return 1; }
  ok "$(rel "$dir")/ -> $remote/"
}

# _voice_a11y: enable the accessibility service after an explicit [y/N], else open settings.
_voice_a11y() {
  local cur new name
  name="$(assistant_label "${CFG_ASSISTANT:-claude}")"
  step "$(L "Accessibility service" "Bedienungshilfe")"
  if ! is_dry && setting_has secure enabled_accessibility_services "$A11Y_COMPONENT"; then
    ok "$(L "already enabled" "schon aktiviert")"
    return 0
  fi
  msg "Spot Voice needs its accessibility service to tap the voice-mode button inside the $name app for you." \
      "Spot Voice braucht seine Bedienungshilfe, um in der $name-App für dich auf den Sprachmodus-Button zu tippen."
  hint "$(L "An accessibility service can read the screen and tap. Spot Voice only acts inside the assistant app; the code is open (apps/voice/.../launch/VoiceModeA11yService.kt)." \
            "Eine Bedienungshilfe kann den Bildschirm lesen und tippen. Spot Voice handelt nur in der Assistenten-App; der Code ist offen (apps/voice/.../launch/VoiceModeA11yService.kt).")"
  if ask_yes_no "$(L "Enable the accessibility service for Spot Voice now via adb?" \
                     "Bedienungshilfe für Spot Voice jetzt per adb aktivieren?")" N; then
    cur="$(adb_shq "null" settings get secure enabled_accessibility_services)"
    case "$cur" in ''|null) new="$A11Y_COMPONENT" ;; *) new="$cur:$A11Y_COMPONENT" ;; esac
    adb_sh settings put secure enabled_accessibility_services "$(shq "$new")"
    adb_sh settings put secure accessibility_enabled 1
    ok "$(L "Accessibility service enabled" "Bedienungshilfe aktiviert")"
    return 0
  fi
  adb_sh am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null
  msg "The accessibility settings are open on the Spot:" "Auf dem Spot sind die Bedienungshilfe-Einstellungen offen:"
  howto "$(L "Tap \"Spot Voice Auto-Tap\" (under downloaded apps/services)." "Auf \"Spot Voice Auto-Tap\" tippen (unter heruntergeladene Apps/Dienste).")" \
        "$(L "Switch on \"Use Spot Voice Auto-Tap\" and confirm with \"Allow\"." "\"Spot Voice Auto-Tap verwenden\" einschalten und mit \"Zulassen\" bestätigen.")"
  if wait_setting secure enabled_accessibility_services "$A11Y_COMPONENT" 300; then
    ok "$(L "Accessibility service enabled" "Bedienungshilfe aktiviert")"
  else
    warn "$(L "Not enabled - the wake word cannot start voice mode until you switch it on (./install.sh verify checks it)." \
              "Nicht aktiviert - das Wake-Word kann den Sprachmodus erst starten, wenn es an ist (./install.sh verify prüft das).")"
  fi
}

phase_voice() {
  local a="${CFG_ASSISTANT:-claude}" lang="${CFG_LANG:-$UI_LANG}" out
  ensure_device || return 1
  ensure_root || warn "$(L "Without root some steps may fail (starting the service from adb)." \
                           "Ohne Root können einzelne Schritte scheitern (Dienst per adb starten).")"

  step "$(L "Install Spot Voice" "Spot Voice installieren")"
  obtain_apk spot-voice || return 1
  install_apk "$APK_PATH" "$VOICE_PKG" || return 1

  step "$(L "Permissions" "Berechtigungen")"
  adb_sh pm grant "$VOICE_PKG" android.permission.RECORD_AUDIO
  # "Display over other apps" lets Spot Voice bring the assistant to the front from the background.
  adb_sh appops set "$VOICE_PKG" SYSTEM_ALERT_WINDOW allow
  adb_sh dumpsys deviceidle whitelist "+$VOICE_PKG" >/dev/null
  ok "$(L "microphone, overlay, no battery optimisation" "Mikrofon, Überlagern, keine Akku-Optimierung")"

  # Start the service first: it creates the app's files folder and registers the broadcast
  # receivers (SET_ASSISTANT/SET_LANG/SET_THRESHOLD) used below.
  step "$(L "Start the listening service" "Zuhör-Dienst starten")"
  out="$(adb_shq "Starting service: Intent { cmp=$VOICE_SERVICE }" am start-foreground-service -n "$VOICE_SERVICE")"
  case "$out" in
    *Error*|*Exception*)
      warn "$out"
      adb_sh am start -n "$VOICE_PKG/.MainActivity" >/dev/null
      hint "$(L "Spot Voice is open on the Spot: tap \"Start listening\" there." "Spot Voice ist auf dem Spot offen: dort \"Zuhören starten\" tippen.")" ;;
  esac
  is_dry || sleep 3
  quiet adb_sh mkdir -p "$VOICE_FILES" || true

  _voice_models "$a" || return 1
  _voice_vosk "$lang" || return 1
  _voice_a11y

  step "$(L "Select assistant, language and thresholds" "Assistent, Sprache und Schwellen einstellen")"
  adb_sh am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p "$VOICE_PKG" --es assistant "$a" >/dev/null
  adb_sh am broadcast -a ai.pocket.heyclaude.SET_LANG -p "$VOICE_PKG" --es lang "$lang" >/dev/null
  adb_sh am broadcast -a ai.pocket.heyclaude.SET_THRESHOLD -p "$VOICE_PKG" \
    --ef wake "$(assistant_wake_threshold "$a")" --ef stop "$(assistant_stop_threshold "$a")" \
    --ei frames "$(sensitivity_frames "$CFG_SENSITIVITY")" >/dev/null
  ok "$(L "assistant" "Assistent")=$a, $(L "language" "Sprache")=$lang, wake=$(assistant_wake_threshold "$a")/$(sensitivity_frames "$CFG_SENSITIVITY") $(L "frames" "Fenster") (${CFG_SENSITIVITY:-normal}), stop=$(assistant_stop_threshold "$a")"
  if [ "$a" = chatgpt ]; then
    hint "$(L "ChatGPT thresholds are provisional (not tuned yet). Tuning: docs/$UI_LANG/05-voice-assistant.md" \
              "Die ChatGPT-Schwellen sind vorläufig (noch nicht abgestimmt). Abstimmen: docs/$UI_LANG/05-voice-assistant.md")"
  fi
  adb_sh am start-foreground-service -n "$VOICE_SERVICE" >/dev/null
  ok "$(L "Spot Voice is listening." "Spot Voice hört zu.")"
}
