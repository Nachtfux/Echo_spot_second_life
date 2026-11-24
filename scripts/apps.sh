#!/usr/bin/env bash
# apps.sh - phase "apps": installs the chosen assistant app (Claude or ChatGPT) and optionally
# Spotify through Aurora Store, with a split-APK fallback when Aurora's installer hangs, and
# explains the login.
# Sourced by install.sh; running it directly runs "./install.sh apps".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" apps "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

AURORA_CACHE="/data/data/com.aurora.store/cache/Downloads"

# Fallback (docs/SPEC.md §2): copy the APKs Aurora already downloaded, check that all splits
# have the same signer, then adb install-multiple. Needs adb root ("Rooted debugging").
_apps_fallback() {
  local pkg="$1" vc list dir f signer first="" bad=0
  local -a apks
  msg "Fallback: install the files Aurora already downloaded directly with adb." \
      "Notweg: die von Aurora schon geladenen Dateien direkt per adb installieren."
  ask_yes_no "$(L "Try the fallback now?" "Notweg jetzt versuchen?")" Y || return 1
  ensure_root || return 1
  if ! is_dry && ! find_apksigner; then
    err "$(L "The fallback needs apksigner (Android SDK build-tools) to check the signatures." \
             "Der Notweg braucht apksigner (Android SDK build-tools), um die Signaturen zu prüfen.")"
    return 1
  fi
  vc="$(adb_shq "123456" "ls $AURORA_CACHE/$pkg 2>/dev/null" | grep -E '^[0-9]+$' | sort -n | tail -n 1)"
  if [ -z "$vc" ]; then
    err "$(L "Aurora has not downloaded $pkg yet. Tap \"Install\" in Aurora, wait until the download finished, then run ./install.sh apps again." \
             "Aurora hat $pkg noch nicht heruntergeladen. In Aurora \"Installieren\" tippen, warten bis der Download fertig ist, dann ./install.sh apps erneut starten.")"
    return 1
  fi
  list="$(adb_shq "base.apk
split_config.armeabi_v7a.apk" "cd $AURORA_CACHE/$pkg/$vc && ls *.apk")"
  [ -n "$list" ] || { err "$(L "No APK files in Aurora's cache." "Keine APK-Dateien im Aurora-Cache.")"; return 1; }
  dir="$DOWNLOAD_DIR/aurora/$pkg/$vc"
  run mkdir -p "$dir"
  apks=()
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    adbx pull "$AURORA_CACHE/$pkg/$vc/$f" "$dir/$f" >/dev/null || { err "adb pull $f"; return 1; }
    apks+=("$dir/$f")
  done <<EOF
$list
EOF
  case "$list" in
    *armeabi_v7a*) ;;
    *) warn "$(L "No armeabi-v7a split found - the app may not run on the Spot (32-bit ARM)." \
                 "Kein armeabi-v7a-Split gefunden - die App läuft evtl. nicht auf dem Spot (32-Bit-ARM).")" ;;
  esac
  if is_dry; then
    trace apksigner verify --print-certs "$(rel "$dir")/*.apk"
  else
    for f in ${apks[@]+"${apks[@]}"}; do
      signer="$(apk_signer "$f")"
      if [ -z "$signer" ]; then err "$(L "does not verify" "Signatur ungültig"): $(basename "$f")"; bad=1; continue; fi
      if [ -z "$first" ]; then first="$signer"; elif [ "$signer" != "$first" ]; then bad=1; fi
    done
    if [ "$bad" = 1 ]; then
      err "$(L "The split APKs do not all have the same valid signer - not installing them." \
               "Die Split-APKs haben nicht alle dieselbe gültige Signatur - keine Installation.")"
      return 1
    fi
    ok "$(L "All ${#apks[@]} splits signed by" "Alle ${#apks[@]} Splits signiert von") $first"
  fi
  adbx install-multiple -r ${apks[@]+"${apks[@]}"} || { err "adb install-multiple"; return 1; }
  is_dry && return 0
  pkg_installed "$pkg"
}

# _apps_install <package> <name>: install through Aurora Store, the user taps "Install".
_apps_install() {
  local pkg="$1" name="$2"
  if pkg_installed "$pkg"; then ok "$name $(L "is already installed" "ist schon installiert")"; return 0; fi
  if ! pkg_installed "$AURORA_PKG" 0; then
    err "$(L "Aurora Store is missing - run ./install.sh setup first." "Aurora Store fehlt - zuerst ./install.sh setup ausführen.")"
    return 1
  fi
  step "$(L "Install $name" "$name installieren")"
  msg "The script now opens $name in Aurora Store on the Spot. Then:" \
      "Das Skript öffnet jetzt $name im Aurora Store auf dem Spot. Dann:"
  howto "$(L "First time in Aurora? Go through its welcome screens and choose the \"Anonymous\" login (no Google account needed)." \
             "Zum ersten Mal in Aurora? Durch die Startbildschirme gehen und den \"Anonym\"-Login wählen (kein Google-Konto nötig).")" \
        "$(L "If Aurora does not show $name by itself after that, run ./install.sh apps again." \
             "Zeigt Aurora danach $name nicht von selbst, ./install.sh apps erneut starten.")" \
        "$(L "Tap \"Install\" and confirm the Android dialog with \"Install\"." \
             "\"Installieren\" tippen und den Android-Dialog mit \"Installieren\" bestätigen.")" \
        "$(L "Wait - the script notices the finished installation by itself." \
             "Warten - das Skript merkt das Ende der Installation von selbst.")"
  adb_sh am start -a android.intent.action.VIEW -d "market://details?id=$pkg" -p "$AURORA_PKG" >/dev/null
  if wait_package "$pkg" 600; then ok "$name $(L "installed" "installiert")"; return 0; fi
  warn "$(L "$name did not get installed (Aurora's installer sometimes hangs on this ROM)." \
            "$name wurde nicht installiert (der Installer von Aurora hängt auf diesem ROM manchmal).")"
  _apps_fallback "$pkg" && { ok "$name $(L "installed" "installiert")"; return 0; }
  see_docs 09-troubleshooting.md
  return 1
}

_apps_login() {
  local a="$1" name pkg link_def=Y
  name="$(assistant_label "$a")"
  pkg="$(assistant_pkg "$a")"
  # A real --yes run must not stop at the hidden link prompt; open-link can be run later.
  if [ "$ASSUME_YES" = 1 ] && ! is_dry; then link_def=N; fi
  step "$(L "Log in to $name" "Bei $name anmelden")"
  quiet adb_sh monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 || true
  if [ "$a" = claude ]; then
    howto "$(L "On the Spot the Claude app is open. Choose to continue with e-mail and type your e-mail address." \
               "Auf dem Spot ist die Claude-App offen. Mit E-Mail fortfahren wählen und die E-Mail-Adresse eintippen.")" \
          "$(L "On your computer open the e-mail from Anthropic. Right-click the sign-in button and copy the link." \
               "Am Computer die E-Mail von Anthropic öffnen. Rechtsklick auf den Anmelde-Button und Link kopieren.")" \
          "$(L "The script asks for the link now (or later: ./install.sh open-link) and opens it on the Spot. The link is not stored." \
               "Das Skript fragt jetzt nach dem Link (oder später: ./install.sh open-link) und öffnet ihn auf dem Spot. Der Link wird nicht gespeichert.")" \
          "$(L "Allow the microphone when Claude asks for it." "Das Mikrofon erlauben, wenn Claude danach fragt.")"
    if ask_yes_no "$(L "Paste the login link now?" "Login-Link jetzt einfügen?")" "$link_def"; then
      cmd_open_link || warn "$(L "Not opened - you can run ./install.sh open-link later." "Nicht geöffnet - später geht ./install.sh open-link.")"
    fi
  else
    howto "$(L "On the Spot the ChatGPT app is open. Log in with your e-mail address." \
               "Auf dem Spot ist die ChatGPT-App offen. Mit der E-Mail-Adresse anmelden.")" \
          "$(L "Type the code from the e-mail on the Spot. (Sign-in with Google/Apple may not work without Google services.)" \
               "Den Code aus der E-Mail auf dem Spot eintippen. (Anmelden mit Google/Apple klappt ohne Google-Dienste evtl. nicht.)")" \
          "$(L "Typing on the tiny screen is fiddly: scrcpy shows the Spot on your computer so you can use your keyboard (brew install scrcpy, then: scrcpy${ADB_TARGET:+ -s $ADB_TARGET})." \
               "Tippen auf dem kleinen Display ist mühsam: scrcpy zeigt den Spot am Computer, dann geht die normale Tastatur (brew install scrcpy, dann: scrcpy${ADB_TARGET:+ -s $ADB_TARGET}).")" \
          "$(L "Allow the microphone when ChatGPT asks for it." "Das Mikrofon erlauben, wenn ChatGPT danach fragt.")"
  fi
  pause_enter "$(L "Press Enter when you are logged in ..." "Enter drücken, wenn du angemeldet bist ...")"
}

phase_apps() {
  local a="${CFG_ASSISTANT:-claude}" pkg name
  ensure_device || return 1
  pkg="$(assistant_pkg "$a")"
  name="$(assistant_label "$a")"
  if [ -z "$CFG_SPOTIFY" ]; then
    if ask_yes_no "$(L "Also install Spotify (for voice commands like \"play ...\"; needs a Spotify account)?" \
                       "Auch Spotify installieren (für Sprachbefehle wie \"spiele ...\"; braucht ein Spotify-Konto)?")" N; then
      CFG_SPOTIFY=1
    else
      CFG_SPOTIFY=0
    fi
    config_save
  fi
  _apps_install "$pkg" "$name" || return 1
  if ask_yes_no "$(L "Allow the microphone for $name now (otherwise the app asks on first use)?" \
                     "Mikrofon für $name jetzt erlauben (sonst fragt die App beim ersten Mal)?")" Y; then
    quiet adb_sh pm grant "$pkg" android.permission.RECORD_AUDIO || true
  fi
  if [ "$CFG_SPOTIFY" = 1 ]; then
    _apps_install "$SPOTIFY_PKG" Spotify || warn "$(L "Spotify not installed - voice commands for music will not work." \
                                                     "Spotify nicht installiert - Musik-Sprachbefehle gehen dann nicht.")"
  fi
  _apps_login "$a"
  [ "$CFG_SPOTIFY" = 1 ] && hint "$(L "Open Spotify once on the Spot and log in there too." "Spotify auf dem Spot einmal öffnen und dort auch anmelden.")"
  return 0
}
