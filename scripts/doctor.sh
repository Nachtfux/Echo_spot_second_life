#!/usr/bin/env bash
# doctor.sh - phase "doctor": checks this computer for everything the installer needs and
# tells you what is plugged in. Installs nothing without asking; read-only on the device.
# Sourced by install.sh; running it directly runs "./install.sh doctor".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" doctor "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

_doctor_tool() { # _doctor_tool <name> -> ok line or error line
  if have "$1"; then
    ok "$1: $(command -v "$1")"
    return 0
  fi
  err "$1 $(L "not found" "nicht gefunden")"
  return 1
}

# Offer to install adb + fastboot with the system package manager.
_doctor_offer_platform_tools() {
  local os
  os="$(uname -s)"
  if [ "$os" = Darwin ]; then
    if ! have brew; then
      hint "$(L "Install Homebrew first (https://brew.sh), then: brew install android-platform-tools" \
                "Zuerst Homebrew installieren (https://brew.sh), dann: brew install android-platform-tools")"
      return 1
    fi
    ask_yes_no "$(L "Install adb + fastboot now (brew install android-platform-tools)?" \
                    "adb + fastboot jetzt installieren (brew install android-platform-tools)?")" Y || return 1
    run brew install android-platform-tools
  elif have apt; then
    ask_yes_no "$(L "Install adb + fastboot now (sudo apt install adb fastboot)?" \
                    "adb + fastboot jetzt installieren (sudo apt install adb fastboot)?")" Y || return 1
    run sudo apt install -y adb fastboot
  else
    hint "$(L "Install the Android platform tools (adb, fastboot) with your package manager." \
              "Die Android Platform Tools (adb, fastboot) mit dem Paketmanager installieren.")"
    return 1
  fi
}

# What is connected right now (read-only), with a hint for the next step.
_doctor_devices() {
  local adbl fbl
  if is_dry; then
    trace adb devices -l
    trace fastboot devices
    return 0
  fi
  if ! have adb || ! have fastboot; then return 0; fi
  adbl="$(run_with_timeout 10 adb devices -l 2>/dev/null | tr -d '\r' | awk 'NR>1 && NF>=2')"
  fbl="$(run_with_timeout 10 fastboot devices 2>/dev/null | tr -d '\r')"
  if [ -n "$fbl" ]; then
    ok "$(L "A device is in fastboot mode" "Ein Gerät ist im Fastboot-Modus"):"
    printf '%s\n' "$fbl" | sed 's/^/     /'
    hint "$(L "Next: ./install.sh unlock" "Weiter: ./install.sh unlock")"
  elif [ -n "$adbl" ]; then
    ok "$(L "adb sees" "adb sieht"):"
    printf '%s\n' "$adbl" | sed 's/^/     /'
    case "$adbl" in
      *recovery*) hint "$(L "TWRP is running - next: ./install.sh backup" "TWRP läuft - weiter: ./install.sh backup")" ;;
      *unauthorized*) hint "$(L "Tap \"Allow\" in the USB debugging dialog on the Spot." "Im USB-Debugging-Dialog auf dem Spot \"Zulassen\" tippen.")" ;;
    esac
  else
    info "$(L "No Spot connected right now - that is fine for this check." "Gerade kein Spot verbunden - für diese Prüfung kein Problem.")"
  fi
}

phase_doctor() {
  local os missing=0 free_kb
  os="$(uname -s)"
  info "$(L "Computer" "Computer"): $os $(uname -r) ($(uname -m)), bash $BASH_VERSION"
  case "$os" in
    Darwin) ;;
    Linux) warn "$(L "Linux should work but is untested. Please report problems." "Linux sollte gehen, ist aber ungetestet. Probleme bitte melden.")" ;;
    *) err "$(L "Only macOS and Linux are supported (Windows: use amonet's own .bat files for the unlock)." \
                "Nur macOS und Linux werden unterstützt (Windows: für das Entsperren die .bat-Dateien von amonet nutzen).")"
       is_dry || return 1 ;;
  esac

  step "$(L "Required tools" "Benötigte Werkzeuge")"
  if ! have adb || ! have fastboot; then
    _doctor_tool adb || true
    _doctor_tool fastboot || true
    _doctor_offer_platform_tools || true
    is_dry && warn "$(L "(dry-run: the install above was only printed)" "(Probelauf: die Installation oben wurde nur angezeigt)")"
  fi
  _doctor_tool adb || missing=$((missing + 1))
  _doctor_tool fastboot || missing=$((missing + 1))
  if ! is_dry && have adb; then hint "$(adb --version 2>/dev/null | head -n 1)"; fi
  _doctor_tool curl || { missing=$((missing + 1)); hint "brew install curl  |  sudo apt install curl"; }
  _doctor_tool unzip || { missing=$((missing + 1)); hint "brew install unzip  |  sudo apt install unzip"; }
  if _doctor_tool python3; then
    if ! is_dry && ! python3 -I -c 'import json' >/dev/null 2>&1; then
      err "$(L "python3 does not work (on macOS: xcode-select --install)" "python3 funktioniert nicht (macOS: xcode-select --install)")"
      missing=$((missing + 1))
    fi
  else
    missing=$((missing + 1)); hint "brew install python  |  sudo apt install python3"
  fi
  if sha256_tool >/dev/null; then ok "sha256: $(sha256_tool)"; else
    err "$(L "Neither shasum nor sha256sum found" "Weder shasum noch sha256sum gefunden")"; missing=$((missing + 1))
  fi

  step "$(L "Optional tools" "Optionale Werkzeuge")"
  detect_timeout
  if [ "$TIMEOUT_BIN" != none ]; then ok "timeout: $(command -v "$TIMEOUT_BIN")"; else
    info "$(L "No GNU timeout - the installer uses its own (brew install coreutils adds gtimeout)." \
              "Kein GNU timeout - der Installer nutzt einen eigenen (brew install coreutils bringt gtimeout).")"
  fi
  if find_apksigner; then ok "apksigner: $APKSIGNER"; else
    info "$(L "apksigner not found - only needed for the signature check of Aurora Store and the install fallback in phase apps (Android SDK build-tools)." \
              "apksigner nicht gefunden - nur für die Signaturprüfung von Aurora Store und den Installations-Notweg in Phase apps nötig (Android SDK build-tools).")"
  fi
  if have scrcpy; then ok "scrcpy: $(command -v scrcpy)"; else
    info "$(L "scrcpy not found (optional: shows the Spot's screen on your computer, handy for typing logins) - brew install scrcpy" \
              "scrcpy nicht gefunden (optional: zeigt das Spot-Display am Computer, praktisch zum Eintippen von Logins) - brew install scrcpy")"
  fi

  step "$(L "Disk space and repository" "Speicherplatz und Repository")"
  free_kb="$(df -Pk "$ESL_ROOT" 2>/dev/null | awk 'NR==2 {print $4}')"
  if [ -n "$free_kb" ]; then
    if [ "$free_kb" -lt 5242880 ]; then
      warn "$(L "Only $((free_kb / 1024 / 1024)) GB free - about 5 GB are needed (LineageOS, backup, speech model)." \
                "Nur $((free_kb / 1024 / 1024)) GB frei - etwa 5 GB werden gebraucht (LineageOS, Sicherung, Sprachmodell).")"
    else
      ok "$(L "Free disk space" "Freier Speicher"): $((free_kb / 1024 / 1024)) GB"
    fi
  fi
  if [ -f "$MODELS_DIR/hey_claude.onnx" ] && [ -f "$MODELS_DIR/danke_claude.onnx" ]; then ok "$(L "Wake-word models" "Wake-Word-Modelle"): models/"; else
    warn "$(L "models/hey_claude.onnx or danke_claude.onnx missing - is the repository complete?" \
              "models/hey_claude.onnx oder danke_claude.onnx fehlt - ist das Repository vollständig?")"
  fi

  step "$(L "USB" "USB")"
  hint "$(L "Plug the Spot DIRECTLY into the computer (no USB hub, no dock, no monitor port) and use a micro-USB DATA cable - many cheap cables only charge." \
            "Den Spot DIREKT am Computer anschließen (kein USB-Hub, kein Dock, kein Monitor-Anschluss) und ein Micro-USB-DATENkabel nehmen - viele billige Kabel laden nur.")"
  hint "$(L "The Spot's original power supply must stay connected the whole time." \
            "Das Original-Netzteil des Spot bleibt die ganze Zeit eingesteckt.")"
  if [ "$os" = Linux ]; then
    hint "$(L "Linux: if adb says \"no permissions\", install the udev rules (e.g. sudo apt install android-sdk-platform-tools-common) and re-plug." \
              "Linux: meldet adb \"no permissions\", die udev-Regeln installieren (z. B. sudo apt install android-sdk-platform-tools-common) und neu einstecken.")"
  fi
  _doctor_devices
  see_docs 01-hardware.md wiring

  if [ "$missing" -gt 0 ]; then
    if is_dry; then
      warn "$(L "$missing required tool(s) missing (dry-run continues anyway)." "$missing benötigte(s) Werkzeug(e) fehlen (Probelauf macht trotzdem weiter).")"
      return 0
    fi
    err "$(L "$missing required tool(s) missing - install them (hints above) and run ./install.sh doctor again." \
             "$missing benötigte(s) Werkzeug(e) fehlen - installieren (Hinweise oben) und ./install.sh doctor erneut starten.")"
    return 1
  fi
  ok "$(L "This computer is ready." "Dieser Computer ist bereit.")"
}
