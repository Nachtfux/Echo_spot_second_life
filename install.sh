#!/usr/bin/env bash
# install.sh - Echo Spot Second Life: the single entry point.
#
# Turns an Amazon Echo Spot (1st gen, "rook") into an open device: unlock -> backup ->
# LineageOS 18.1 -> Claude/ChatGPT voice mode by wake word, offline voice commands and
# a round clock. Run ./install.sh help for usage; docs/SPEC.md section 4 is the contract.
#
# bash 3.2 compatible (macOS /bin/bash). Works from any directory.
# shellcheck source-path=SCRIPTDIR
if [ -z "${BASH_VERSION:-}" ]; then
  echo "Please run it with bash: ./install.sh  (or: bash install.sh)" >&2
  exit 1
fi
set -u
set -o pipefail

# ------------------------------------------------------------- repo root ----
_src="${BASH_SOURCE[0]}"
while [ -L "$_src" ]; do
  _dir="$(cd -P "$(dirname "$_src")" && pwd)"
  _src="$(readlink "$_src")"
  case "$_src" in /*) ;; *) _src="$_dir/$_src" ;; esac
done
ESL_ROOT="$(cd -P "$(dirname "$_src")" && pwd)"
unset _src _dir

# shellcheck source=scripts/lib.sh
. "$ESL_ROOT/scripts/lib.sh"
# shellcheck source=scripts/doctor.sh
. "$ESL_ROOT/scripts/doctor.sh"
# shellcheck source=scripts/unlock.sh
. "$ESL_ROOT/scripts/unlock.sh"
# shellcheck source=scripts/backup.sh
. "$ESL_ROOT/scripts/backup.sh"
# shellcheck source=scripts/flash.sh
. "$ESL_ROOT/scripts/flash.sh"
# shellcheck source=scripts/setup.sh
. "$ESL_ROOT/scripts/setup.sh"
# shellcheck source=scripts/apps.sh
. "$ESL_ROOT/scripts/apps.sh"
# shellcheck source=scripts/voice.sh
. "$ESL_ROOT/scripts/voice.sh"
# shellcheck source=scripts/clock.sh
. "$ESL_ROOT/scripts/clock.sh"
# shellcheck source=scripts/verify.sh
. "$ESL_ROOT/scripts/verify.sh"
# shellcheck source=scripts/build-apps.sh
. "$ESL_ROOT/scripts/build-apps.sh"

ALL_PHASES="doctor unlock backup flash setup apps voice clock verify"

# ---------------------------------------------------------- phase table ----
phase_title() {
  case "$1" in
    doctor) L "check this computer (tools, USB)" "diesen Computer prüfen (Werkzeuge, USB)" ;;
    unlock) L "unlock the Spot with amonet - it then boots TWRP" "Spot mit amonet entsperren - danach startet TWRP" ;;
    backup) L "back up all partitions (in TWRP)" "alle Partitionen sichern (in TWRP)" ;;
    flash)  L "replace Fire OS with LineageOS 18.1" "Fire OS durch LineageOS 18.1 ersetzen" ;;
    setup)  L "first start, developer options, language, Aurora Store" "Ersteinrichtung, Entwickleroptionen, Sprache, Aurora Store" ;;
    apps)   L "install the assistant app (+ Spotify) and log in" "Assistenten-App (+ Spotify) installieren und anmelden" ;;
    voice)  L "install Spot Voice (wake word, voice commands)" "Spot Voice installieren (Wake-Word, Sprachbefehle)" ;;
    clock)  L "install Spot Clock (home screen, screensaver, weather)" "Spot Clock installieren (Startbildschirm, Bildschirmschoner, Wetter)" ;;
    verify) L "check everything, optional live test" "alles prüfen, optional Live-Test" ;;
  esac
}

phase_doc() {
  case "$1" in
    doctor) printf '01-hardware.md' ;;
    unlock) printf '02-unlock.md' ;;
    backup|flash) printf '03-lineageos.md' ;;
    setup) printf '04-setup.md' ;;
    apps|voice) printf '05-voice-assistant.md' ;;
    clock) printf '07-clock.md' ;;
    verify) printf '09-troubleshooting.md' ;;
  esac
}

# Phases that need your hands on the Spot (see docs/images/install-flow.*.svg).
phase_hands() { case "$1" in unlock|setup|apps) return 0 ;; esac; return 1; }

phase_index() {
  local i=0 p
  for p in $ALL_PHASES; do
    i=$((i + 1))
    if [ "$p" = "$1" ]; then printf '%s' "$i"; return 0; fi
  done
}

is_phase() { case " $ALL_PHASES " in *" $1 "*) return 0 ;; esac; return 1; }

# ------------------------------------------------------------------ help ----
usage() {
  if [ "$UI_LANG" = de ]; then
    cat <<'EOF'
Echo Spot Second Life - Installer

Aufruf:  ./install.sh [Phase ...] [Optionen]
         ./install.sh status | open-link | train [Argumente] | build | help

Phasen (in dieser Reihenfolge; ohne Angabe laufen alle, fertige werden übersprungen):
  doctor   diesen Computer prüfen (Werkzeuge, USB)
  unlock   Spot mit amonet entsperren, danach startet TWRP         [Handarbeit]
  backup   alle Partitionen nach backups/ sichern (in TWRP)
  flash    Fire OS löschen und LineageOS 18.1 installieren
  setup    Ersteinrichtung, Entwickleroptionen, Sprache, Aurora     [Handarbeit]
  apps     Claude oder ChatGPT (+ Spotify) installieren, anmelden   [Handarbeit]
  voice    Spot Voice installieren: Wake-Word, Offline-Sprachbefehle
  clock    Spot Clock installieren: runde Uhr, Bildschirmschoner, Wetter
  verify   alles prüfen, optional Live-Test

Befehle:
  status      Einstellungen, fertige Phasen und verbundenes Gerät anzeigen
  open-link   Login-Link (Magic Link aus der E-Mail) auf dem Spot öffnen;
              Eingabe unsichtbar, wird nie gespeichert
  train       eigenes Wake-Word trainieren (startet training/train.sh mit den restlichen Argumenten)
  build       beide Apps aus dem Quellcode nach dist/ bauen (braucht JDK 17 + Android SDK)
  help        diese Hilfe

Optionen:
  --assistant claude|chatgpt   welchen Assistenten das Wake-Word startet
  --lang de|en                 Sprache des Spot, der Sprachbefehle und dieses Skripts
  --place "<Ort>"              Wetter-Ort für die Uhr (Stadt oder Postleitzahl)
  --device <Seriennr|IP:Port>  welches Gerät (Standard: USB, nach setup die WLAN-Adresse)
  --spotify | --no-spotify     Spotify mitinstallieren (sonst wird gefragt)
  --sensitivity high|normal|low  wie leicht das Wake-Word auslöst (Standard normal;
                               high = hört besser, aber öfter Fehlauslösungen)
  --from-source                Apps selbst bauen statt das Release zu laden
  --dry-run                    nur anzeigen, was passieren würde (kein Gerät, keine Downloads)
  --yes                        Standardantwort bei normalen Fragen nehmen
                               (gefährliche Schritte brauchen trotzdem ein getipptes JA)
  --force                      bereits erledigte Phasen erneut ausführen

Beispiele:
  ./install.sh                                 komplette Installation, macht dort weiter, wo sie aufgehört hat
  ./install.sh --dry-run --yes                 alles ohne Gerät durchspielen
  ./install.sh apps voice --assistant chatgpt  auf ChatGPT umstellen
  ./install.sh clock --place "Berlin"          Wetter-Ort ändern
  ./install.sh verify                          alles prüfen

Einstellungen: .state/config.env    Anleitung: docs/de/ (englisch: --lang en)
EOF
  else
    cat <<'EOF'
Echo Spot Second Life - installer

Usage:  ./install.sh [phase ...] [options]
        ./install.sh status | open-link | train [args] | build | help

Phases (in this order; without a phase all run, finished ones are skipped):
  doctor   check this computer (tools, USB)
  unlock   unlock the Spot with amonet; it then boots TWRP          [hands needed]
  backup   back up all partitions to backups/ (in TWRP)
  flash    wipe Fire OS and install LineageOS 18.1
  setup    first start, developer options, language, Aurora Store   [hands needed]
  apps     install Claude or ChatGPT (+ Spotify) and log in         [hands needed]
  voice    install Spot Voice: wake word, offline voice commands
  clock    install Spot Clock: round clock, screensaver, weather
  verify   check everything, optional live test

Commands:
  status      show settings, finished phases and the connected device
  open-link   open a login link (e-mail magic link) on the Spot;
              the input is hidden and never stored
  train       train your own wake word (runs training/train.sh with the remaining arguments)
  build       build both apps from source into dist/ (needs JDK 17 + Android SDK)
  help        show this help

Options:
  --assistant claude|chatgpt   which assistant the wake word starts
  --lang de|en                 language of the Spot, the voice commands and this installer
  --place "<city>"             weather place for the clock (city or postal code)
  --device <serial|ip:port>    which device to use (default: USB, after setup the Wi-Fi address)
  --spotify | --no-spotify     also install Spotify (asked if not given)
  --sensitivity high|normal|low  how easily the wake word triggers (default normal;
                               high = hears you better, but more false wakes)
  --from-source                build the apps yourself instead of downloading the release
  --dry-run                    only print what would be done (no device, no downloads)
  --yes                        take the default answer of normal questions
                               (destructive steps still need you to type YES)
  --force                      run phases again that are already done

Examples:
  ./install.sh                                 full installation, continues where it stopped
  ./install.sh --dry-run --yes                 preview everything without a device
  ./install.sh apps voice --assistant chatgpt  switch to ChatGPT
  ./install.sh clock --place "Berlin"          change the weather place
  ./install.sh verify                          check everything

Settings: .state/config.env    Docs: docs/en/ (German: --lang de)
EOF
  fi
}

# --------------------------------------------------------------- options ----
OPT_ASSISTANT=""; OPT_LANG=""; OPT_PLACE=""; OPT_DEVICE=""; OPT_SPOTIFY=""; OPT_SENS=""
COMMAND=""
PHASE_ARGS=""
TRAIN_ARGS=()

bad_usage() {
  err "$1"
  hint "$(L "See: ./install.sh help" "Siehe: ./install.sh help")"
  exit 2
}

need_value() { [ "$2" -ge 2 ] || bad_usage "$(L "Option $1 needs a value." "Option $1 braucht einen Wert.")"; }

parse_args() {
  local a
  while [ $# -gt 0 ]; do
    a="$1"
    case "$a" in
      --assistant) need_value "$a" $#; OPT_ASSISTANT="$2"; shift ;;
      --assistant=*) OPT_ASSISTANT="${a#*=}" ;;
      --lang) need_value "$a" $#; OPT_LANG="$2"; shift ;;
      --lang=*) OPT_LANG="${a#*=}" ;;
      --place) need_value "$a" $#; OPT_PLACE="$2"; shift ;;
      --place=*) OPT_PLACE="${a#*=}" ;;
      --device) need_value "$a" $#; OPT_DEVICE="$2"; shift ;;
      --device=*) OPT_DEVICE="${a#*=}" ;;
      --sensitivity) need_value "$a" $#; OPT_SENS="$2"; shift ;;
      --sensitivity=*) OPT_SENS="${a#*=}" ;;
      --spotify) OPT_SPOTIFY=1 ;;
      --no-spotify) OPT_SPOTIFY=0 ;;
      --from-source) FROM_SOURCE=1 ;;
      --dry-run|-n) DRY_RUN=1 ;;
      --yes|-y) ASSUME_YES=1 ;;
      --force|-f) FORCE=1 ;;
      -h|--help|help) COMMAND="${COMMAND:-help}" ;;
      status|open-link|build)
        [ -z "$COMMAND" ] || bad_usage "$(L "Only one command at a time." "Nur ein Befehl auf einmal.")"
        COMMAND="$a" ;;
      train)
        [ -z "$COMMAND" ] || bad_usage "$(L "Only one command at a time." "Nur ein Befehl auf einmal.")"
        COMMAND=train
        shift
        TRAIN_ARGS=("$@")
        break ;;
      -*) bad_usage "$(L "Unknown option: $a" "Unbekannte Option: $a")" ;;
      *)
        is_phase "$a" || bad_usage "$(L "Unknown phase or command: $a" "Unbekannte Phase oder Befehl: $a")"
        PHASE_ARGS="$PHASE_ARGS $a" ;;
    esac
    shift
  done
  if [ -n "$COMMAND" ] && [ -n "$PHASE_ARGS" ] && [ "$COMMAND" != help ]; then
    bad_usage "$(L "Give either phases or a command, not both." "Entweder Phasen oder einen Befehl angeben, nicht beides.")"
  fi
}

validate_and_apply_options() {
  if [ -n "$OPT_ASSISTANT" ]; then
    OPT_ASSISTANT="$(lower "$OPT_ASSISTANT")"
    case "$OPT_ASSISTANT" in claude|chatgpt) CFG_ASSISTANT="$OPT_ASSISTANT" ;;
      *) bad_usage "--assistant: claude | chatgpt" ;; esac
  fi
  if [ -n "$OPT_LANG" ]; then
    OPT_LANG="$(lower "$OPT_LANG")"
    case "$OPT_LANG" in de|en) CFG_LANG="$OPT_LANG" ;;
      *) bad_usage "--lang: de | en" ;; esac
  fi
  if [ -n "$OPT_PLACE" ]; then
    CFG_PLACE="$(clean_value "$OPT_PLACE")"
    CFG_LAT=""; CFG_LON=""   # geocoded again in the clock phase
  fi
  if [ -n "$OPT_DEVICE" ]; then CFG_DEVICE="$(clean_value "$OPT_DEVICE")"; DEVICE_FROM_CLI=1; fi
  if [ -n "$OPT_SPOTIFY" ]; then CFG_SPOTIFY="$OPT_SPOTIFY"; fi
  if [ -n "$OPT_SENS" ]; then
    OPT_SENS="$(lower "$OPT_SENS")"
    case "$OPT_SENS" in high|normal|low) CFG_SENSITIVITY="$OPT_SENS" ;;
      *) bad_usage "--sensitivity: high | normal | low" ;; esac
  fi
}

# ------------------------------------------------------- first-run questions ----
phases_need() { # phases_need <list> <phase...> -> 0 if any of them is in the list
  local list=" $1 " p
  shift
  for p in "$@"; do case "$list" in *" $p "*) return 0 ;; esac; done
  return 1
}

ask_missing_config() {
  local list="$1" changed=0 def
  if [ -z "$CFG_LANG" ] && phases_need "$list" setup apps voice clock verify; then
    def="$(lang_from_env)"
    while :; do
      ask_value "Language for the Spot, its voice commands and this installer / Sprache für den Spot, die Sprachbefehle und dieses Skript (de/en)" "$def"
      case "$(lower "$REPLY_VALUE")" in de|en) CFG_LANG="$(lower "$REPLY_VALUE")"; break ;; esac
      warn "de / en"
    done
    UI_LANG="$CFG_LANG"
    changed=1
  fi
  if [ -z "$CFG_ASSISTANT" ] && phases_need "$list" apps voice verify; then
    msg "Which assistant should the wake word start?" "Welchen Assistenten soll das Wake-Word starten?"
    hint "claude  = $(L "Claude voice mode (wake word \"Hey Claude\")" "Claude-Sprachmodus (Wake-Word \"Hey Claude\")")"
    hint "chatgpt = $(L "ChatGPT voice mode (wake word \"Hey ChatGPT\")" "ChatGPT-Sprachmodus (Wake-Word \"Hey ChatGPT\")")"
    while :; do
      ask_value "$(L "Assistant (claude/chatgpt)" "Assistent (claude/chatgpt)")" "claude"
      case "$(lower "$REPLY_VALUE")" in claude|chatgpt) CFG_ASSISTANT="$(lower "$REPLY_VALUE")"; break ;; esac
      warn "claude / chatgpt"
    done
    changed=1
  fi
  if [ -z "$CFG_PLACE" ] && phases_need "$list" clock; then
    ask_value "$(L "Weather place for the clock (city or postal code, empty = no weather)" \
                   "Wetter-Ort für die Uhr (Stadt oder Postleitzahl, leer = kein Wetter)")" ""
    if [ -n "$REPLY_VALUE" ]; then CFG_PLACE="$(clean_value "$REPLY_VALUE")"; else CFG_PLACE="-"; fi
    CFG_LAT=""; CFG_LON=""
    changed=1
  fi
  if [ "$changed" = 1 ] || [ -n "$OPT_ASSISTANT$OPT_LANG$OPT_PLACE$OPT_DEVICE$OPT_SPOTIFY$OPT_SENS" ]; then config_save; fi
}

# ---------------------------------------------------------------- banner ----
banner() {
  local mode dev place asst
  printf '\n%s\n' "${C_ORG}${C_BLD}Echo Spot Second Life${C_RST}  $(L "installer" "Installer")"
  if is_dry; then
    mode="$(L "DRY RUN - commands are only printed (+ ...), nothing touches a device or the network" \
              "PROBELAUF - Befehle werden nur angezeigt (+ ...), kein Gerät und kein Netzwerk wird berührt")"
    printf '%s\n' "${C_YEL}$mode${C_RST}"
  fi
  dev="${CFG_DEVICE:-USB}"
  place="$CFG_PLACE"
  [ "$place" = "-" ] && place="$(L "none" "keiner")"
  asst="?"
  [ -n "$CFG_ASSISTANT" ] && asst="$(assistant_label "$CFG_ASSISTANT")"
  printf '%s\n' "${C_DIM}$(L "Assistant" "Assistent"): $asst  |  $(L "Language" "Sprache"): ${CFG_LANG:-?}  |  $(L "Weather" "Wetter"): ${place:-?}  |  $(L "Device" "Gerät"): $dev${C_RST}"
}

overview() {
  local p i mark hands
  step "$(L "The 9 phases" "Die 9 Phasen")"
  for p in $ALL_PHASES; do
    i="$(phase_index "$p")"
    if is_done "$p"; then mark="${C_GRN}✓${C_RST}"; else mark=" "; fi
    hands=""
    phase_hands "$p" && hands="  ${C_YEL}[$(L "hands needed" "Handarbeit")]${C_RST}"
    printf '   %s %d %-7s %s%s\n' "$mark" "$i" "$p" "$(phase_title "$p")" "$hands"
  done
  printf '     %s%s: %s  |  %s  |  %s%s\n' "$C_DIM" "$(L "More help" "Mehr Hilfe")" "$(L README.md README.de.md)" \
    "$(img_ref install-flow)" "$(img_ref wiring)" "$C_RST"
  hint "$(L "Stop at any time with Ctrl+C (except while unlocking); ./install.sh continues where it stopped." \
            "Jederzeit mit Strg+C abbrechen (nicht während des Entsperrens); ./install.sh macht dort weiter.")"
}

phase_header() {
  local p="$1"
  step "$(L "Phase" "Phase") $(phase_index "$p")/9: $p - $(phase_title "$p")"
  see_docs "$(phase_doc "$p")"
}

# ------------------------------------------------------------ run phases ----
run_phases() {
  local list="$1" explicit="$2" p rc
  for p in $list; do
    if [ "$explicit" = 0 ] && [ "$FORCE" = 0 ] && is_done "$p"; then
      ok "$(L "Phase $(phase_index "$p")/9 \"$p\" already done ($(done_when "$p")) - skipped (--force runs it again)" \
              "Phase $(phase_index "$p")/9 \"$p\" schon erledigt ($(done_when "$p")) - übersprungen (--force führt sie erneut aus)")"
      continue
    fi
    if [ "$explicit" = 0 ]; then
      printf '\n'
      info "$(L "Next" "Als Nächstes"): $(phase_index "$p")/9 $p - $(phase_title "$p")"
      ask_phase "$p"; rc=$?
      if [ "$rc" = 1 ]; then info "$(L "Skipped \"$p\"." "\"$p\" übersprungen.")"; continue; fi
      if [ "$rc" = 2 ]; then
        info "$(L "Stopped. Run ./install.sh again to continue with \"$p\"." "Angehalten. ./install.sh erneut starten, um mit \"$p\" weiterzumachen.")"
        exit 0
      fi
    fi
    CURRENT_PHASE="$p"
    phase_header "$p"
    if "phase_$p"; then
      mark_done "$p"
      ok "$(L "Phase \"$p\" done." "Phase \"$p\" erledigt.")"
    else
      err "$(L "Phase \"$p\" did not complete." "Phase \"$p\" wurde nicht abgeschlossen.")"
      hint "$(L "Fix the problem shown above, then run: ./install.sh $p" "Das Problem oben beheben, dann: ./install.sh $p")"
      hint "$(L "(or just ./install.sh - finished phases are skipped)" "(oder einfach ./install.sh - erledigte Phasen werden übersprungen)")"
      see_docs 09-troubleshooting.md
      exit 1
    fi
    CURRENT_PHASE=""
  done
}

final_summary() {
  local a wake
  a="${CFG_ASSISTANT:-claude}"
  wake="Hey $(assistant_label "$a")"
  if [ "$a" = chatgpt ] && [ ! -f "$MODELS_DIR/hey_chatgpt.onnx" ]; then wake="Alexa"; fi
  step "$(L "Finished" "Fertig")"
  howto "$(L "Say \"$wake\" - the Spot opens $(assistant_label "$a") voice mode. Then just talk." \
             "Sag \"$wake\" - der Spot öffnet den Sprachmodus von $(assistant_label "$a"). Dann einfach sprechen.")" \
        "$(L "To end the conversation say e.g. \"Thank you $(assistant_label "$a")\" or stay silent for about 10 seconds." \
             "Zum Beenden z. B. \"Danke $(assistant_label "$a")\" sagen oder ca. 10 Sekunden still sein.")" \
        "$(L "Offline voice commands (music, radio, timer, alarm): docs/$UI_LANG/06-voice-commands.md" \
             "Offline-Sprachbefehle (Musik, Radio, Timer, Wecker): docs/$UI_LANG/06-voice-commands.md")" \
        "$(L "Switch the assistant later: ./install.sh apps voice --assistant chatgpt|claude" \
             "Assistent später wechseln: ./install.sh apps voice --assistant chatgpt|claude")"
  see_docs 05-voice-assistant.md voice-session architecture
}

# -------------------------------------------------------------- commands ----
cmd_status() {
  local p mark last place
  step "$(L "Settings" "Einstellungen") ($(rel "$CONFIG_FILE"))"
  [ -f "$CONFIG_FILE" ] || hint "$(L "(not created yet - the first ./install.sh run asks for the values)" "(noch nicht angelegt - der erste ./install.sh-Lauf fragt die Werte ab)")"
  place="${CFG_PLACE:--}"
  [ "$place" = "-" ] && place="$(L "(none)" "(keiner)")"
  [ -n "$CFG_LAT" ] && place="$place ($CFG_LAT, $CFG_LON)"
  printf '   %-12s %s\n' "$(L "Assistant" "Assistent")" "${CFG_ASSISTANT:--}" \
                         "$(L "Language" "Sprache")" "${CFG_LANG:--}" \
                         "$(L "Weather" "Wetter")" "$place" \
                         "$(L "Device" "Gerät")" "${CFG_DEVICE:-USB}" \
                         "Spotify" "${CFG_SPOTIFY:--}" \
                         "$(L "Time zone" "Zeitzone")" "${CFG_TZ:--}" \
                         "$(L "Wake word" "Wake-Word")" "${CFG_SENSITIVITY:-normal}"
  step "$(L "Phases" "Phasen")"
  for p in $ALL_PHASES; do
    if is_done "$p"; then mark="${C_GRN}✓${C_RST}"; else mark="${C_DIM}·${C_RST}"; fi
    printf '   %s %-7s %s\n' "$mark" "$p" "$(done_when "$p")"
  done
  step "$(L "Backups" "Sicherungen") ($(rel "$BACKUP_DIR"))"
  last=""
  for p in "$BACKUP_DIR"/*/MANIFEST.sha256; do [ -f "$p" ] && last="$(dirname "$p")"; done
  if [ -n "$last" ]; then
    hint "$(rel "$last") ($(grep -c . "$last/MANIFEST.sha256") $(L "files" "Dateien"))"
  else
    hint "$(L "none yet" "noch keine")"
  fi
  step "$(L "Connected devices" "Verbundene Geräte")"
  if is_dry; then
    trace adb devices -l
    trace fastboot devices
  else
    if have adb; then run_with_timeout 10 adb devices -l 2>/dev/null | sed 's/^/   /'; else hint "adb: -"; fi
    if have fastboot; then run_with_timeout 10 fastboot devices 2>/dev/null | sed 's/^/   fastboot: /'; fi
  fi
}

cmd_open_link() {
  local link remote
  step "$(L "Open a login link on the Spot" "Login-Link auf dem Spot öffnen")"
  ensure_device || return 1
  howto "$(L "On your computer, open the sign-in e-mail. Right-click the sign-in button and choose \"Copy link\"." \
             "Am Computer die Anmelde-E-Mail öffnen. Rechtsklick auf den Anmelde-Button, \"Link kopieren\" wählen.")" \
        "$(L "Paste it here and press Enter. You will not see the text - that is intended. It is not saved anywhere." \
             "Hier einfügen und Enter drücken. Der Text bleibt unsichtbar - das ist Absicht. Er wird nirgends gespeichert.")"
  if is_dry; then
    link="https://example.invalid/<login-link>"
    printf '%s\n' "${C_DIM}+ (read -s link)${C_RST}" >&2
  else
    printf '%s' "${C_YEL}??${C_RST} $(L "Link" "Link"): "
    IFS= read -r -s link || link=""
    printf '\n'
  fi
  case "$link" in
    https://*|http://*) ;;
    *) err "$(L "That does not look like a link (must start with https://)." "Das sieht nicht nach einem Link aus (muss mit https:// beginnen).")"; return 1 ;;
  esac
  remote="am start -a android.intent.action.VIEW -d $(shq "$link")"
  if is_dry; then
    trace adb ${ADB_TARGET:+-s "$ADB_TARGET"} shell am start -a android.intent.action.VIEW -d "<link hidden>"
  else
    # The command goes to the Spot's shell via stdin, so the link never shows up in the
    # process list of this computer.
    if [ -n "$ADB_TARGET" ]; then
      printf '%s\n' "$remote" | adb -s "$ADB_TARGET" shell >/dev/null 2>&1
    else
      printf '%s\n' "$remote" | adb shell >/dev/null 2>&1
    fi
  fi
  link=""; remote=""
  ok "$(L "Opened on the Spot. Look at its screen: the app should open and finish the sign-in." \
          "Auf dem Spot geöffnet. Aufs Display schauen: die App sollte sich öffnen und die Anmeldung abschließen.")"
}

cmd_train() {
  local t="$ESL_ROOT/training/train.sh"
  if [ ! -f "$t" ]; then
    if is_dry; then warn "$(rel "$t") $(L "not found" "nicht gefunden")"; trace bash "$(rel "$t")" ${TRAIN_ARGS[@]+"${TRAIN_ARGS[@]}"}; return 0; fi
    err "$(rel "$t") $(L "not found." "nicht gefunden.")"
    see_docs 08-wakeword-training.md
    return 1
  fi
  if is_dry; then trace bash "$(rel "$t")" ${TRAIN_ARGS[@]+"${TRAIN_ARGS[@]}"}; return 0; fi
  exec bash "$t" ${TRAIN_ARGS[@]+"${TRAIN_ARGS[@]}"}
}

on_interrupt() {
  printf '\n'
  if [ "$CURRENT_PHASE" = unlock ]; then
    warn "$(L "Interrupted during the unlock. Do NOT unplug the Spot while its screen is busy." \
              "Während des Entsperrens abgebrochen. Den Spot NICHT abstecken, solange das Display arbeitet.")"
  fi
  warn "$(L "Interrupted. Run ./install.sh again to continue." "Abgebrochen. ./install.sh erneut starten, um weiterzumachen.")"
  exit 130
}

# ------------------------------------------------------------------ main ----
main() {
  local list p explicit=0 prev_arg=""
  UI_LANG="$(lang_from_env)"
  for p in "$@"; do   # --lang early, so even option errors use the chosen language
    case "$p" in --lang=de|--lang=en) UI_LANG="${p#--lang=}" ;; esac
    case "$prev_arg" in --lang) case "$p" in de|en) UI_LANG="$p" ;; esac ;; esac
    prev_arg="$p"
  done
  parse_args "$@"
  config_load
  if [ -n "$CFG_LANG" ] && [ -z "$OPT_LANG" ]; then UI_LANG="$CFG_LANG"; fi
  validate_and_apply_options
  if [ -n "$CFG_LANG" ]; then UI_LANG="$CFG_LANG"; fi
  trap on_interrupt INT

  case "$COMMAND" in
    help) usage; exit 0 ;;
    status) cmd_status; exit $? ;;
    open-link) cmd_open_link; exit $? ;;
    train) cmd_train; exit $? ;;
    build) build_apps; exit $? ;;
  esac

  # Phases in canonical order (whatever order they were typed in).
  list=""
  if [ -n "$PHASE_ARGS" ]; then
    explicit=1
    for p in $ALL_PHASES; do case " $PHASE_ARGS " in *" $p "*) list="$list $p" ;; esac; done
  else
    list="$ALL_PHASES"
  fi

  banner
  ask_missing_config "$list"
  [ "$explicit" = 0 ] && overview
  run_phases "$list" "$explicit"
  if [ "$explicit" = 0 ] || phases_need "$list" verify; then final_summary; fi
}

main "$@"
