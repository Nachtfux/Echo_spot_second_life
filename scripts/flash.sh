#!/usr/bin/env bash
# flash.sh - phase "flash": download LineageOS 18.1 (rook v0.3), verify it, and install it
# from TWRP: wipe cache/data/system -> push -> sha256 on the Spot -> twrp install -> reboot.
# Sourced by install.sh; running it directly runs "./install.sh flash".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" flash "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

_flash_have_backup() {
  local f
  is_done backup && return 0
  for f in "$BACKUP_DIR"/*/MANIFEST.sha256; do [ -s "$f" ] && return 0; done
  return 1
}

_flash_twrp() { # _flash_twrp <twrp args...>: run a TWRP command, show its output, fail on errors
  local out ec
  info "twrp $*"
  if is_dry; then adb_sh twrp "$@"; return 0; fi
  out="$(adb_sh twrp "$@" 2>&1 | tr -d '\r')"; ec=$?
  printf '%s\n' "$out" | sed 's/^/      /'
  if [ "$ec" != 0 ]; then return 1; fi
  # Only TWRP's own failure messages count; "Failed to mount ..." lines are normal after a wipe.
  case "$out" in
    *"Error installing zip"*|*"Updater process ended with ERROR"*|*"Installation aborted"*|*"Invalid zip file format"*) return 1 ;;
  esac
  return 0
}

phase_flash() {
  local zip="$DOWNLOAD_DIR/$LOS_ZIP" prod remote_sha
  use_usb
  is_dry || need_adb || return 1

  step "$(L "Download LineageOS 18.1 for rook (v0.3, ~1 GB)" "LineageOS 18.1 für rook laden (v0.3, ~1 GB)")"
  download_verified "$LOS_URL" "$zip" "$LOS_SHA256" || return 1

  step "$(L "Checks" "Prüfungen")"
  if ! is_dry && [ "$(adb_state)" != recovery ]; then
    msg "The Spot must be running TWRP:" "Auf dem Spot muss TWRP laufen:"
    howto "$(L "Unplug the Spot's power. Keep the USB cable connected." "Strom des Spot abziehen. USB-Kabel bleibt dran.")" \
          "$(L "Hold Volume + and plug the power back in. Let go when TWRP appears." \
               "Lauter gedrückt halten und Strom wieder einstecken. Loslassen, sobald TWRP erscheint.")"
    wait_adb_state recovery 600 || { err "$(L "TWRP not found." "TWRP nicht gefunden.")"; return 1; }
  fi
  ok "adb state = recovery (TWRP)"
  prod="$(adb_shq rook getprop ro.product.device)"
  if [ "$prod" != rook ]; then
    err "$(L "ro.product.device='$prod' (expected rook). Stopping, nothing was changed." \
             "ro.product.device='$prod' (erwartet rook). Abbruch, nichts wurde verändert.")"
    return 1
  fi
  ok "ro.product.device = rook"
  if ! _flash_have_backup; then
    warn "$(L "No backup found in backups/. Without it you cannot go back to Fire OS or repair a broken Spot." \
              "Keine Sicherung in backups/ gefunden. Ohne sie gibt es keinen Weg zurück zu Fire OS und keine Reparatur.")"
    if ! ask_yes_no "$(L "Continue WITHOUT a backup? (recommended: no, run ./install.sh backup first)" \
                         "OHNE Sicherung weitermachen? (empfohlen: nein, zuerst ./install.sh backup)")" N; then
      return 1
    fi
  else
    ok "$(L "Backup present" "Sicherung vorhanden")"
  fi

  if ! confirm_destructive "$(L "This ERASES Fire OS and everything on the Spot (wipe cache, data, system) and installs LineageOS 18.1." \
                                "Das LÖSCHT Fire OS und alles auf dem Spot (wipe cache, data, system) und installiert LineageOS 18.1.")"; then
    info "$(L "Not confirmed - nothing was changed." "Nicht bestätigt - nichts wurde verändert.")"
    return 1
  fi

  step "$(L "Wipe" "Löschen")"
  _flash_twrp wipe cache || { err "twrp wipe cache"; return 1; }
  _flash_twrp wipe data || { err "twrp wipe data"; return 1; }
  _flash_twrp wipe system || { err "twrp wipe system"; return 1; }

  step "$(L "Copy LineageOS to the Spot" "LineageOS auf den Spot kopieren")"
  adbx push "$zip" /sdcard/ || { err "adb push"; return 1; }
  remote_sha="$(adb_shq "$LOS_SHA256" sha256sum "/sdcard/$LOS_ZIP" | awk '{print $1}')"
  if [ "$remote_sha" != "$LOS_SHA256" ]; then
    err "$(L "sha256 on the Spot is wrong (${remote_sha:-?}) - the copy is damaged. Run ./install.sh flash again." \
             "sha256 auf dem Spot falsch (${remote_sha:-?}) - die Kopie ist beschädigt. ./install.sh flash erneut starten.")"
    return 1
  fi
  ok "$(L "sha256 on the Spot verified" "sha256 auf dem Spot geprüft")"

  step "$(L "Install LineageOS (about 2-4 minutes)" "LineageOS installieren (ca. 2-4 Minuten)")"
  hint "$(L "On the screen TWRP shows the installation progress. Do not unplug anything." \
            "Auf dem Display zeigt TWRP den Fortschritt. Nichts abstecken.")"
  if ! _flash_twrp install "/sdcard/$LOS_ZIP"; then
    err "$(L "The installation reported an error (see above). The Spot stays in TWRP - do not reboot it." \
             "Die Installation meldete einen Fehler (siehe oben). Der Spot bleibt in TWRP - nicht neu starten.")"
    see_docs 09-troubleshooting.md
    return 1
  fi
  quiet adb_sh rm -f "/sdcard/$LOS_ZIP" || true
  ok "$(L "LineageOS installed." "LineageOS installiert.")"

  adbx reboot
  step "$(L "First start" "Erster Start")"
  msg "The Spot now starts LineageOS for the first time. This takes about 5 minutes - the boot animation may run for a long time. Keep the power connected." \
      "Der Spot startet jetzt zum ersten Mal LineageOS. Das dauert ca. 5 Minuten - die Startanimation läuft evtl. lange. Strom angeschlossen lassen."
  hint "$(L "Next phase (setup) guides you through the first start." "Die nächste Phase (setup) führt durch die Ersteinrichtung.")"
}
