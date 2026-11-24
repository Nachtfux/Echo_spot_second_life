#!/usr/bin/env bash
# unlock.sh - phase "unlock": amonet-rook v2.0.0 "fastbrick" unlock, then wait for TWRP.
#
# The payload logic is the one tested on a real Spot (scripts/reference/unlock-rook-tested.sh,
# itself amonet's fastbrick.sh flash_payload()) with two portable changes: fastboot comes from
# PATH, and the 8 s time limit uses gtimeout/timeout or a bash fallback.
# `fastboot -S 1G` is required: fastbrick.img is 32 bytes over the Spot's max-download-size,
# so current fastboot refuses it otherwise (techo5 issue #394).
# The only write is `fastboot flash brick bin/fastbrick.img`; the exploit does the rest.
#
# Sourced by install.sh; running it directly runs "./install.sh unlock".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" unlock "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

UNLOCK_MAX_ATTEMPTS=15
AMONET_ZIP=""
AMONET_DIR=""   # the folder that contains bin/fastbrick.img

_unlock_ts() { date +%T; }

# Find the amonet zip in downloads/ (or offer to copy it from ~/Downloads).
_unlock_find_zip() {
  local f
  AMONET_ZIP=""
  for f in "$DOWNLOAD_DIR/$AMONET_ZIP_NAME" "$DOWNLOAD_DIR"/amonet*rook*.zip "$DOWNLOAD_DIR"/amonet*.zip; do
    if [ -f "$f" ]; then AMONET_ZIP="$f"; return 0; fi
  done
  for f in "$HOME/Downloads/$AMONET_ZIP_NAME" "$HOME/Downloads"/amonet*rook*.zip; do
    [ -f "$f" ] || continue
    if ask_yes_no "$(L "Found $f - copy it to downloads/?" "$f gefunden - nach downloads/ kopieren?")" Y; then
      run mkdir -p "$DOWNLOAD_DIR"
      run cp "$f" "$DOWNLOAD_DIR/"
      AMONET_ZIP="$DOWNLOAD_DIR/$(basename "$f")"
      return 0
    fi
  done
  return 1
}

_unlock_get_amonet() {
  local tries=0 extract img
  step "$(L "Step 1/5: get amonet-rook v2.0.0" "Schritt 1/5: amonet-rook v2.0.0 besorgen")"
  msg "The unlock uses amonet-rook v2.0.0 (by R0rt1z2 and contributors). The download needs a free XDA account, so you fetch it yourself:" \
      "Das Entsperren nutzt amonet-rook v2.0.0 (von R0rt1z2 und Mitwirkenden). Der Download braucht ein kostenloses XDA-Konto, deshalb lädst du ihn selbst:"
  howto "$(L "Open this page in your browser and log in to XDA:" "Diese Seite im Browser öffnen und bei XDA anmelden:") $AMONET_THREAD_URL" \
        "$(L "Download the attachment $AMONET_ZIP_NAME from the first post." "Den Anhang $AMONET_ZIP_NAME aus dem ersten Beitrag herunterladen.")" \
        "$(L "Put the zip into this folder (do not unpack it):" "Die ZIP-Datei in diesen Ordner legen (nicht entpacken):") $DOWNLOAD_DIR/"
  until _unlock_find_zip; do
    if is_dry; then
      AMONET_ZIP="$DOWNLOAD_DIR/$AMONET_ZIP_NAME"
      info "$(L "(dry-run: assuming $(rel "$AMONET_ZIP"))" "(Probelauf: nehme $(rel "$AMONET_ZIP") an)")"
      break
    fi
    tries=$((tries + 1))
    [ "$tries" -le 20 ] || return 1
    if [ "$ASSUME_YES" = 1 ]; then err "$(L "$AMONET_ZIP_NAME not found in downloads/." "$AMONET_ZIP_NAME nicht in downloads/ gefunden.")"; return 1; fi
    pause_enter "$(L "Press Enter when the file is in downloads/ ..." "Enter drücken, sobald die Datei in downloads/ liegt ...")"
  done
  ok "$(L "Found" "Gefunden"): $(rel "$AMONET_ZIP")"
  if ! check_sha256 "$AMONET_ZIP" "$AMONET_ZIP_SHA256"; then
    hint "$(L "This is not the tested amonet-rook v2.0.0 zip. Download it again; other versions are not supported by this script." \
              "Das ist nicht die getestete amonet-rook-v2.0.0-ZIP. Neu herunterladen; andere Versionen unterstützt dieses Skript nicht.")"
    return 1
  fi

  extract="$DOWNLOAD_DIR/$(basename "$AMONET_ZIP" .zip)"
  img=""
  if [ -d "$extract" ]; then img="$(find "$extract" -path '*/bin/fastbrick.img' -type f 2>/dev/null | head -n 1)"; fi
  if [ -z "$img" ]; then
    run unzip -q -o "$AMONET_ZIP" -d "$extract" || { err "$(L "unzip failed" "Entpacken fehlgeschlagen")"; return 1; }
    if is_dry; then img="$extract/amonet/bin/fastbrick.img"
    else img="$(find "$extract" -path '*/bin/fastbrick.img' -type f 2>/dev/null | head -n 1)"; fi
  fi
  if [ -z "$img" ]; then err "$(L "bin/fastbrick.img not found in the zip" "bin/fastbrick.img nicht in der ZIP gefunden")"; return 1; fi
  AMONET_DIR="$(dirname "$(dirname "$img")")"
  if ! is_dry; then
    if [ "$(file_size "$img")" != "$FASTBRICK_SIZE" ]; then
      err "$(L "fastbrick.img has an unexpected size ($(file_size "$img") instead of $FASTBRICK_SIZE bytes)." \
               "fastbrick.img hat eine unerwartete Größe ($(file_size "$img") statt $FASTBRICK_SIZE Bytes).")"
      return 1
    fi
  fi
  ok "$(L "Payload" "Payload"): $(rel "$img") ($FASTBRICK_SIZE bytes)"
}

_unlock_connect() {
  step "$(L "Step 2/5: put the Spot into fastboot mode" "Schritt 2/5: Spot in den Fastboot-Modus bringen")"
  howto "$(L "Unplug the Spot's power cable." "Das Netzkabel des Spot abziehen.")" \
        "$(L "Turn the Spot around. On the back, between the power socket and the 3.5 mm audio socket, there is a small glued rubber cover. Peel it off carefully (fingernail or plastic pick) - a micro-USB port is underneath." \
             "Den Spot umdrehen. Hinten, zwischen Netzbuchse und 3,5-mm-Audiobuchse, sitzt eine kleine geklebte Gummiabdeckung. Vorsichtig abziehen (Fingernagel oder Plastikplektrum) - darunter ist eine Micro-USB-Buchse.")" \
        "$(L "Connect a micro-USB data cable from that port directly to this computer (no hub)." \
             "Ein Micro-USB-Datenkabel von dieser Buchse direkt mit diesem Computer verbinden (kein Hub).")" \
        "$(L "Press and hold ALL THREE buttons on top (Volume -, Action/Mute, Volume +)." \
             "ALLE DREI Tasten oben gedrückt halten (Leiser, Aktion/Stumm, Lauter).")" \
        "$(L "While holding them, plug the power cable back in. Keep holding until the screen shows \"fastboot mode\", then let go." \
             "Tasten gedrückt halten und dabei das Netzkabel wieder einstecken. Halten, bis auf dem Display \"fastboot mode\" steht, dann loslassen.")"
  see_docs 02-unlock.md spot-back-port fastboot-buttons wiring
  info "$(L "Waiting for the Spot in fastboot mode (up to 10 minutes) ..." "Warte auf den Spot im Fastboot-Modus (bis zu 10 Minuten) ...")"
  if ! wait_fastboot 600; then
    err "$(L "No fastboot device found." "Kein Fastboot-Gerät gefunden.")"
    hint "$(L "Check the cable (data cable?), use a port directly on the computer, and try the three buttons again." \
              "Kabel prüfen (Datenkabel?), einen Anschluss direkt am Computer nehmen und die drei Tasten noch einmal versuchen.")"
    return 1
  fi
  ok "$(L "Fastboot device found." "Fastboot-Gerät gefunden.")"
}

# Read-only pre-checks, exactly as in the tested reference script.
_unlock_prechecks() {
  local n prod unl lk
  step "$(L "Step 3/5: safety checks (read-only)" "Schritt 3/5: Sicherheitsprüfungen (nur lesen)")"
  if is_dry; then
    fb_t 10 devices; n=1
    fb_t 15 getvar product; prod=ROOK
    fb_t 15 getvar unlock_status; unl=false
    fb_t 15 getvar lk_build_desc; lk="$ROOK_LK_BUILD (placeholder)"
  else
    n="$(fb_t 10 devices 2>/dev/null | grep -c fastboot)"
    prod="$(fb_t 15 getvar product 2>&1 | awk '/^product:/ {print $2; exit}')"
    unl="$(fb_t 15 getvar unlock_status 2>&1 | awk '/^unlock_status:/ {print tolower($2); exit}')"
    lk="$(fb_t 15 getvar lk_build_desc 2>&1 | awk -F': ' '/^lk_build_desc:/ {print $2; exit}')"
  fi
  if [ "$n" != 1 ]; then
    err "$(L "Expected exactly 1 fastboot device, found: $n. Unplug other phones/tablets." \
             "Genau 1 Fastboot-Gerät erwartet, gefunden: $n. Andere Handys/Tablets abstecken.")"
    return 1
  fi
  ok "$(L "exactly one fastboot device" "genau ein Fastboot-Gerät")"
  if [ "$prod" != ROOK ]; then
    err "$(L "product='$prod' (expected ROOK) - this is not an Echo Spot 1st gen. Nothing was changed." \
             "product='$prod' (erwartet ROOK) - das ist kein Echo Spot 1. Generation. Nichts wurde verändert.")"
    return 1
  fi
  ok "product = ROOK"
  if [ "$unl" = true ]; then
    ok "$(L "unlock_status = true: this Spot is already unlocked - the payload is NOT sent again." \
            "unlock_status = true: dieser Spot ist schon entsperrt - das Payload wird NICHT erneut gesendet.")"
    hint "$(L "To start TWRP: unplug power, hold Volume + while plugging it back in." \
              "TWRP starten: Strom abziehen, Lauter gedrückt halten und wieder einstecken.")"
    return 3
  fi
  if [ "$unl" != false ]; then
    err "$(L "unlock_status='$unl' (expected false). Stopping, nothing was changed." \
             "unlock_status='$unl' (erwartet false). Abbruch, nichts wurde verändert.")"
    return 1
  fi
  ok "unlock_status = false"
  case "$lk" in
    "$ROOK_LK_BUILD"*) ok "lk_build_desc = $lk (Fire OS 5.5.6.9)" ;;
    *)
      err "$(L "Unexpected bootloader version lk_build_desc='$lk' (tested: $ROOK_LK_BUILD = Fire OS 5.5.6.9). Stopping, nothing was changed." \
               "Unerwartete Bootloader-Version lk_build_desc='$lk' (getestet: $ROOK_LK_BUILD = Fire OS 5.5.6.9). Abbruch, nichts wurde verändert.")"
      return 1 ;;
  esac
}

# Send the payload (fastbrick.sh logic): repeat up to 15x with an 8 s limit; a timeout
# (exit 124) means the exploit is running.
_unlock_send_payload() {
  local attempt=1 success=0 out ec tbin
  step "$(L "Step 4/5: send the unlock payload" "Schritt 4/5: Entsperr-Payload senden")"
  msg "What happens now:" "Was jetzt passiert:"
  howto "$(L "The script sends amonet's payload with: fastboot -S 1G flash brick bin/fastbrick.img" \
             "Das Skript sendet das amonet-Payload mit: fastboot -S 1G flash brick bin/fastbrick.img")" \
        "$(L "The first attempt usually ends with \"restricted on locked hw\" - that is normal." \
             "Der erste Versuch endet meist mit \"restricted on locked hw\" - das ist normal.")" \
        "$(L "The second attempt usually hangs and times out after 8 s - that means the exploit is running." \
             "Der zweite Versuch hängt meist und läuft nach 8 s in ein Timeout - dann läuft der Exploit.")" \
        "$(L "About 30 seconds later TWRP (the recovery) appears on the screen." \
             "Etwa 30 Sekunden später erscheint TWRP (das Recovery) auf dem Display.")"
  if ! confirm_destructive "$(L "This unlocks the Spot's bootloader with an exploit. It was tested once on one Spot; it can fail and in the worst case brick the device. From now on do NOT unplug the cable or the power until TWRP shows (up to 6 minutes)." \
                                "Das entsperrt den Bootloader des Spot mit einem Exploit. Getestet wurde es einmal an einem Spot; es kann scheitern und im schlimmsten Fall das Gerät unbrauchbar machen. Ab jetzt Kabel und Strom NICHT abziehen, bis TWRP erscheint (bis zu 6 Minuten).")"; then
    info "$(L "Not confirmed - nothing was sent." "Nicht bestätigt - nichts wurde gesendet.")"
    return 1
  fi
  [ -n "$TIMEOUT_BIN" ] || detect_timeout
  tbin="$TIMEOUT_BIN"; [ "$tbin" = none ] && tbin="(bash) timeout"
  while [ "$attempt" -le "$UNLOCK_MAX_ATTEMPTS" ]; do
    info "[$(_unlock_ts)] $(L "Attempt" "Versuch") $attempt: $tbin 8 fastboot -S 1G flash brick bin/fastbrick.img"
    if is_dry; then
      trace cd "$(rel "$AMONET_DIR")"
      trace "$tbin" 8 fastboot -S 1G flash brick bin/fastbrick.img
      if [ "$attempt" = 1 ]; then out="FAILED (remote: 'restricted on locked hw')  (dry-run placeholder)"; ec=1
      else out="(dry-run placeholder: no answer)"; ec=124; fi
    else
      guard_write fastboot -S 1G flash brick bin/fastbrick.img
      out="$(cd "$AMONET_DIR" && run_with_timeout 8 fastboot -S 1G flash brick bin/fastbrick.img 2>&1)"; ec=$?
    fi
    printf '%s\n' "$out" | sed 's/^/      /'
    echo "      exit=$ec"
    case "$out" in
      *eMMC-RO*)
        err "$(L "The eMMC is permanently read-only (hardware defect). The device is unchanged." \
                 "Der eMMC-Speicher ist dauerhaft schreibgeschützt (Hardwaredefekt). Das Gerät ist unverändert.")"
        return 1 ;;
      *"Device mismatch"*)
        err "$(L "Device mismatch - wrong payload for this device." "Device mismatch - falsches Payload für dieses Gerät.")"
        return 1 ;;
      *unaligned*|*"greater than the maximum download size"*)
        err "$(L "fastboot refuses the image (-S had no effect). Update fastboot (brew upgrade android-platform-tools)." \
                 "fastboot lehnt das Image ab (-S wirkt nicht). fastboot aktualisieren (brew upgrade android-platform-tools).")"
        return 1 ;;
    esac
    if [ "$ec" -eq 124 ]; then
      ok "[$(_unlock_ts)] $(L "Timeout while sending -> the exploit is most likely running. No further attempts." \
                     "Timeout beim Senden -> der Exploit läuft vermutlich. Keine weiteren Versuche.")"
      success=1
      break
    fi
    is_dry || sleep 2
    attempt=$((attempt + 1))
  done
  if [ "$success" != 1 ]; then
    err "$(L "No success after $UNLOCK_MAX_ATTEMPTS attempts. Do NOT cut the power - look at the screen first." \
             "Nach $UNLOCK_MAX_ATTEMPTS Versuchen kein Erfolg. Strom NICHT trennen - erst aufs Display schauen.")"
    return 1
  fi
}

_unlock_wait_twrp() {
  local prod twrp
  step "$(L "Step 5/5: wait for TWRP" "Schritt 5/5: auf TWRP warten")"
  warn "$(L "Waiting up to 6 minutes for TWRP. Do NOT unplug anything!" "Warte bis zu 6 Minuten auf TWRP. NICHTS abstecken!")"
  hint "$(L "On the screen: the Spot restarts and shows the blue TWRP recovery." "Auf dem Display: der Spot startet neu und zeigt das blaue TWRP-Recovery.")"
  use_usb
  if ! wait_adb_state recovery 360; then
    err "[$(_unlock_ts)] $(L "No TWRP visible over adb after 6 minutes. Please look at the Spot's screen." \
                     "Nach 6 Minuten kein TWRP per adb sichtbar. Bitte aufs Display des Spot schauen.")"
    if ! is_dry; then hint "fastboot: $(run_with_timeout 10 fastboot devices 2>/dev/null | tr -d '\r')"; fi
    see_docs 09-troubleshooting.md
    return 1
  fi
  prod="$(adb_shq rook getprop ro.product.device)"
  twrp="$(adb_shq "3.7.0_9-0" getprop ro.twrp.version)"
  ok "[$(_unlock_ts)] $(L "TWRP is running" "TWRP läuft"): ro.product.device=$prod, twrp=$twrp"
  msg "The Spot is unlocked. Leave it in TWRP for the next phase (backup)." \
      "Der Spot ist entsperrt. Für die nächste Phase (backup) in TWRP lassen."
}

phase_unlock() {
  local rc
  use_usb
  have fastboot || is_dry || { err "$(L "fastboot not found - run ./install.sh doctor" "fastboot nicht gefunden - ./install.sh doctor ausführen")"; return 1; }
  if ! is_dry && have adb && [ "$(adb_state)" = recovery ]; then
    ok "$(L "TWRP is already running - the Spot is unlocked." "TWRP läuft bereits - der Spot ist entsperrt.")"
    return 0
  fi
  _unlock_get_amonet || return 1
  _unlock_connect || return 1
  _unlock_prechecks; rc=$?
  if [ "$rc" = 3 ]; then
    ask_yes_no "$(L "Mark the unlock as done and continue?" "Entsperren als erledigt markieren und weitermachen?")" Y
    return
  fi
  [ "$rc" = 0 ] || return 1
  _unlock_send_payload || return 1
  _unlock_wait_twrp
}
