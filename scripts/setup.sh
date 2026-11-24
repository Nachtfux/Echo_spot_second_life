#!/usr/bin/env bash
# setup.sh - phase "setup": guides the first start of LineageOS (setup wizard, Wi-Fi, developer
# options) and then configures the Spot over adb: language, time zone, display density,
# screen timeout, Aurora Store (verified) and adb over Wi-Fi.
# Sourced by install.sh; running it directly runs "./install.sh setup".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" setup "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# Time zone of this computer (macOS and Linux: /etc/localtime -> .../zoneinfo/Area/City).
host_timezone() {
  local t=""
  if [ -L /etc/localtime ]; then t="$(readlink /etc/localtime)"; t="${t##*zoneinfo/}"; fi
  if [ -z "$t" ] && [ -f /etc/timezone ]; then t="$(head -n 1 /etc/timezone)"; fi
  case "$t" in ''|/*) t="UTC" ;; esac
  printf '%s' "$t"
}

_setup_guide() {
  msg "On the Spot (the screen is small - take your time):" "Auf dem Spot (das Display ist klein - in Ruhe):"
  howto "$(L "Wait until LineageOS has started and shows the setup wizard (first start: about 5 minutes)." \
             "Warten, bis LineageOS gestartet ist und den Einrichtungsassistenten zeigt (erster Start: ca. 5 Minuten).")" \
        "$(L "Go through the setup wizard: language, connect to your Wi-Fi, date/time. Skip everything optional." \
             "Den Einrichtungsassistenten durchgehen: Sprache, mit dem WLAN verbinden, Datum/Uhrzeit. Alles Optionale überspringen.")" \
        "$(L "Open Settings > About tablet and tap \"Build number\" 7 times until it says \"You are now a developer\"." \
             "Einstellungen > Über das Tablet öffnen und 7-mal auf \"Build-Nummer\" tippen, bis \"Du bist jetzt Entwickler\" erscheint.")" \
        "$(L "Go to Settings > System > Advanced > Developer options and switch on \"USB debugging\" (LineageOS may call it \"Android debugging\") and \"Rooted debugging\"." \
             "Einstellungen > System > Erweitert > Entwickleroptionen: \"USB-Debugging\" (bei LineageOS evtl. \"Android-Debugging\") und \"Debugging als Root\" (Rooted debugging) einschalten.")" \
        "$(L "Keep the USB cable connected. When \"Allow USB debugging?\" appears, tick \"Always allow from this computer\" and tap \"Allow\"." \
             "USB-Kabel dranlassen. Erscheint \"USB-Debugging zulassen?\", \"Von diesem Computer immer zulassen\" anhaken und \"Zulassen\" tippen.")"
  see_docs 04-setup.md
}

# Wait until the Android framework answers again (after a zygote restart).
_setup_wait_framework() {
  local start out
  if is_dry; then adb_sh pm path android; return 0; fi
  start=$SECONDS
  sleep 5
  while [ $((SECONDS - start)) -lt 180 ]; do
    out="$(adb_shq "" pm path android)"
    case "$out" in *package:*) return 0 ;; esac
    sleep 3
  done
  return 1
}

_setup_root() {
  local tries=0
  until ensure_root; do
    tries=$((tries + 1))
    [ "$tries" -lt 4 ] || return 1
    if [ "$ASSUME_YES" = 1 ]; then return 1; fi
    pause_enter "$(L "Switch on \"Rooted debugging\", then press Enter to try again ..." \
                     "\"Debugging als Root\" einschalten, dann Enter drücken für einen neuen Versuch ...")"
  done
}

_setup_locale_timezone() {
  local loc tz
  if [ "${CFG_LANG:-en}" = de ]; then loc=de-DE; else loc=en-US; fi
  tz="${CFG_TZ:-$(host_timezone)}"
  while :; do
    ask_value "$(L "Time zone of the Spot" "Zeitzone des Spot")" "$tz"
    case "$REPLY_VALUE" in
      UTC|[A-Z][A-Za-z_]*/[A-Za-z0-9_+-]*) tz="$REPLY_VALUE"; break ;;
    esac
    warn "$(L "Use the form Area/City, e.g. Europe/Berlin or America/New_York." "Format Region/Stadt, z. B. Europe/Berlin oder America/New_York.")"
    [ "$ASSUME_YES" = 1 ] && { tz=UTC; break; }
  done
  CFG_TZ="$tz"
  config_save
  info "$(L "Language $loc, time zone $tz" "Sprache $loc, Zeitzone $tz")"
  adb_sh settings put global auto_time_zone 0
  adb_sh setprop persist.sys.timezone "$tz"
  adb_sh settings put system system_locales "$loc"
  adb_sh setprop persist.sys.locale "$loc"
  info "$(L "Waiting 6 s so the settings are written, then restarting the Android UI (the screen goes black for a moment) ..." \
            "Warte 6 s, damit die Einstellungen gespeichert sind, dann startet die Android-Oberfläche neu (Display kurz schwarz) ...")"
  is_dry || sleep 6
  adb_sh setprop ctl.restart zygote
  if _setup_wait_framework; then ok "$(L "Android UI is back" "Android-Oberfläche ist wieder da")"; else
    warn "$(L "Android takes long to come back - continuing anyway." "Android braucht lange - mache trotzdem weiter.")"
  fi
}

_setup_aurora() {
  local apk="$DOWNLOAD_DIR/com.aurora.store_76.apk" signer
  step "$(L "Aurora Store (app store without Google account)" "Aurora Store (App-Store ohne Google-Konto)")"
  download_verified "$AURORA_URL" "$apk" "$AURORA_SHA256" || return 1
  if is_dry; then
    trace apksigner verify --print-certs "$(rel "$apk")"
  elif find_apksigner; then
    signer="$(apk_signer "$apk")"
    if [ "$signer" != "$AURORA_SIGNER" ]; then
      err "$(L "Aurora Store signer is wrong" "Signatur von Aurora Store falsch"): ${signer:-?}"
      return 1
    fi
    ok "$(L "Aurora Store signer verified" "Signatur von Aurora Store geprüft")"
  else
    info "$(L "apksigner not found - signer check skipped (the sha256 check above already passed)." \
              "apksigner nicht gefunden - Signaturprüfung übersprungen (die sha256-Prüfung oben war ok).")"
  fi
  if pkg_installed "$AURORA_PKG"; then ok "$(L "Aurora Store is already installed" "Aurora Store ist schon installiert")"; else
    install_apk "$apk" "$AURORA_PKG" || return 1
  fi
  # Lets Aurora install apps without the extra "allow this source" screen.
  quiet adb_sh appops set "$AURORA_PKG" REQUEST_INSTALL_PACKAGES allow || true
}

phase_setup() {
  local prod los
  use_usb
  is_dry || need_adb || return 1
  _setup_guide
  info "$(L "Waiting for the Spot with USB debugging (up to 30 minutes) ..." "Warte auf den Spot mit USB-Debugging (bis zu 30 Minuten) ...")"
  if ! is_dry && [ "$(adb_state)" != device ]; then
    wait_adb_state device 1800 || { err "$(L "The Spot did not show up with USB debugging." "Der Spot ist nicht mit USB-Debugging aufgetaucht.")"; return 1; }
  fi
  prod="$(adb_shq rook getprop ro.product.device)"
  los="$(adb_shq "18.1-20251108-UNOFFICIAL-rook" getprop ro.lineage.version)"
  if [ "$prod" != rook ]; then
    err "$(L "ro.product.device='$prod' (expected rook)." "ro.product.device='$prod' (erwartet rook).")"
    return 1
  fi
  if [ -z "$los" ]; then
    err "$(L "This is not LineageOS (no ro.lineage.version). Run the flash phase first." \
             "Das ist kein LineageOS (kein ro.lineage.version). Zuerst die Phase flash ausführen.")"
    return 1
  fi
  ok "rook, LineageOS $los"

  if ! _setup_root; then
    err "$(L "Language and time zone need \"Rooted debugging\". Switch it on and run ./install.sh setup again." \
             "Sprache und Zeitzone brauchen \"Debugging als Root\". Einschalten und ./install.sh setup erneut starten.")"
    return 1
  fi

  step "$(L "Language, time zone, display" "Sprache, Zeitzone, Anzeige")"
  _setup_locale_timezone
  info "$(L "Display density 120 (makes phone apps usable on the round 480 px screen), screen off after 60 s" \
            "Anzeigedichte 120 (macht Handy-Apps auf dem runden 480-px-Display nutzbar), Bildschirm aus nach 60 s")"
  adb_sh wm density 120
  adb_sh settings put system screen_off_timeout 60000

  _setup_aurora || return 1

  step "$(L "Screen lock" "Bildschirmsperre")"
  msg "Recommended - otherwise the wake word cannot open the assistant while the Spot is locked:" \
      "Empfohlen - sonst kann das Wake-Word den Assistenten nicht öffnen, solange der Spot gesperrt ist:"
  howto "$(L "On the Spot: Settings > Security > Screen lock." "Auf dem Spot: Einstellungen > Sicherheit > Displaysperre.")" \
        "$(L "Choose \"None\"." "\"Keine\" wählen.")"

  step "$(L "adb over Wi-Fi" "adb über WLAN")"
  if ask_yes_no "$(L "Use adb over Wi-Fi from now on (then the USB cable can be removed)?" \
                     "Ab jetzt adb über WLAN nutzen (dann kann das USB-Kabel ab)?")" Y; then
    enable_wifi_adb || warn "$(L "Staying on USB." "Bleibe bei USB.")"
  fi
}
