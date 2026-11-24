#!/usr/bin/env bash
# clock.sh - phase "clock": installs Spot Clock (ai.pocket.spotclock), makes it the home screen
# and the screensaver, (after asking) allows its notification listener for the "now playing"
# face, and sets the weather place (geocoded with Open-Meteo, no API key).
# Sourced by install.sh; running it directly runs "./install.sh clock".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" clock "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

GEO_LAT=""; GEO_LON=""; GEO_NAME=""; GEO_DESC=""

# Parse the Open-Meteo answer on stdin -> one line per candidate:
# "lat<TAB>lon<TAB>name<TAB>description" (exit 3: no hit). GEO_FILTER (text after a comma in the
# query, e.g. "Neustadt, Holstein") keeps only candidates whose region/district/country matches.
_clock_parse_geo() {
  if have python3; then
    GEO_FILTER="${GEO_FILTER:-}" python3 -I -c '
import json, os, sys
try:
    d = json.load(sys.stdin)
except ValueError:
    sys.exit(2)
want = os.environ.get("GEO_FILTER", "").strip().lower()
clean = lambda s: (s or "").replace("\t", " ").replace("\n", " ")
hits = 0
for r in d.get("results") or []:
    parts = [r.get(k) for k in ("name", "admin3", "admin2", "admin1", "country")]
    desc = ", ".join(dict.fromkeys(p for p in parts if p))
    if want and want not in desc.lower() and want not in " ".join(r.get("postcodes") or []):
        continue
    print("%.4f\t%.4f\t%s\t%s" % (float(r["latitude"]), float(r["longitude"]), clean(r.get("name")), clean(desc)))
    hits += 1
sys.exit(0 if hits else 3)
'
    return
  fi
  # Fallback without python: the first result's fields (Open-Meteo returns one-line JSON).
  local j lat lon name country
  j="$(cat)"
  case "$j" in *'"results"'*) ;; *) return 3 ;; esac
  j="${j#*\"results\"}"
  lat="$(printf '%s' "$j" | sed -n 's/^[^}]*"latitude":\([-0-9.]*\).*/\1/p')"
  lon="$(printf '%s' "$j" | sed -n 's/^[^}]*"longitude":\([-0-9.]*\).*/\1/p')"
  name="$(printf '%s' "$j" | sed -n 's/^[^}]*"name":"\([^"]*\)".*/\1/p')"
  country="$(printf '%s' "$j" | sed -n 's/^[^}]*"country":"\([^"]*\)".*/\1/p')"
  [ -n "$lat" ] && [ -n "$lon" ] || return 3
  printf '%s\t%s\t%s\t%s\n' "$lat" "$lon" "$name" "$name${country:+, $country}"
}

# _clock_geocode <query> -> GEO_* (0 ok, 3 not found, 1 network error, 4 user rejected all)
# "<name>, <region>" narrows the search; several hits are listed for the user to pick from.
_clock_geocode() {
  local q="$1" name json parsed rc n i pick line
  name="${q%%,*}"
  GEO_FILTER=""
  case "$q" in *,*) GEO_FILTER="$(printf '%s' "${q#*,}" | sed 's/^ *//; s/ *$//')" ;; esac
  if is_dry; then
    trace curl -fsS --get --data-urlencode "name=$name" -d count=10 -d "language=$UI_LANG" -d format=json "$GEOCODE_URL"
    GEO_LAT=52.52; GEO_LON=13.40; GEO_NAME="$name"; GEO_DESC="$q (dry-run placeholder)"
    return 0
  fi
  json="$(curl -fsS --max-time 20 --get --data-urlencode "name=$name" -d count=10 -d "language=$UI_LANG" -d format=json "$GEOCODE_URL")" || return 1
  parsed="$(printf '%s' "$json" | _clock_parse_geo)"; rc=$?
  [ "$rc" = 0 ] && [ -n "$parsed" ] || return 3
  n="$(printf '%s\n' "$parsed" | grep -c .)"
  pick=1
  if [ "$n" -gt 1 ]; then
    msg "Several places are called \"$name\":" "Es gibt mehrere Orte namens \"$name\":"
    i=0
    while IFS= read -r line; do
      i=$((i + 1))
      printf '   %2d) %s\n' "$i" "$(printf '%s' "$line" | cut -f4)"
    done <<EOF
$parsed
EOF
    ask_value "$(L "Which one? Number 1-$n (0 = none of these)" "Welcher? Nummer 1-$n (0 = keiner davon)")" "1"
    pick="$REPLY_VALUE"
    case "$pick" in ''|*[!0-9]*) pick=1 ;; esac
    [ "$pick" -ge 1 ] && [ "$pick" -le "$n" ] || return 4
  fi
  line="$(printf '%s\n' "$parsed" | sed -n "${pick}p")"
  IFS=$'\t' read -r GEO_LAT GEO_LON GEO_NAME GEO_DESC <<EOF
$line
EOF
  [ -n "$GEO_LAT" ] && [ -n "$GEO_LON" ] || return 3
}

# Resolve CFG_PLACE to coordinates and let the user confirm the place found.
_clock_place() {
  local q="$CFG_PLACE" tries=0 rc
  if [ -z "$q" ] || [ "$q" = "-" ]; then
    info "$(L "No weather place set - the clock runs without weather. Later: ./install.sh clock --place \"<city>\"" \
              "Kein Wetter-Ort gesetzt - die Uhr läuft ohne Wetter. Später: ./install.sh clock --place \"<Ort>\"")"
    return 1
  fi
  if [ -n "$CFG_LAT" ] && [ -n "$CFG_LON" ] && [ "$FORCE" = 0 ]; then
    ok "$(L "Weather place" "Wetter-Ort"): $q ($CFG_LAT, $CFG_LON)"
    return 0
  fi
  while [ "$tries" -lt 5 ]; do
    tries=$((tries + 1))
    case "$q" in
      *[0-9][0-9][0-9]*)
        warn "$(L "Open-Meteo finds places by name, not by postal code. Type the town name, e.g. \"Neustadt, Holstein\"." \
                  "Open-Meteo findet Orte über den Namen, nicht über die Postleitzahl. Bitte den Ortsnamen eingeben, z. B. \"Neustadt, Holstein\".")"
        rc=3 ;;
      *)
        info "$(L "Looking up \"$q\" (Open-Meteo) ..." "Suche \"$q\" (Open-Meteo) ...")"
        _clock_geocode "$q"; rc=$? ;;
    esac
    if [ "$rc" = 0 ]; then
      if ask_yes_no "$(L "Weather place: $GEO_DESC ($GEO_LAT, $GEO_LON). Correct?" \
                         "Wetter-Ort: $GEO_DESC ($GEO_LAT, $GEO_LON). Richtig?")" Y; then
        CFG_PLACE="$(clean_value "${GEO_NAME:-$q}")"; CFG_LAT="$GEO_LAT"; CFG_LON="$GEO_LON"
        config_save
        return 0
      fi
    elif [ "$rc" = 3 ]; then
      warn "$(L "No place found for \"$q\"." "Kein Ort gefunden für \"$q\".")"
    elif [ "$rc" = 1 ]; then
      warn "$(L "Open-Meteo did not answer (internet?)." "Open-Meteo antwortet nicht (Internet?).")"
    fi
    [ "$ASSUME_YES" = 1 ] && break
    ask_value "$(L "Try another name, optionally with the region: \"<town>, <region>\" (empty = no weather)" \
                   "Anderen Namen versuchen, gern mit Region: \"<Ort>, <Region>\" (leer = kein Wetter)")" ""
    [ -n "$REPLY_VALUE" ] || break
    q="$REPLY_VALUE"
  done
  CFG_PLACE="-"; CFG_LAT=""; CFG_LON=""
  config_save
  info "$(L "Continuing without weather." "Weiter ohne Wetter.")"
  return 1
}

_clock_listener() {
  step "$(L "Now-playing face (notification access)" "Anzeige \"Läuft gerade\" (Benachrichtigungszugriff)")"
  if ! is_dry && setting_has secure enabled_notification_listeners "$LISTENER_COMPONENT"; then
    ok "$(L "already allowed" "schon erlaubt")"
    return 0
  fi
  msg "To show cover, title and artist of the music, Spot Clock needs notification access." \
      "Für Cover, Titel und Interpret der Musik braucht Spot Clock Zugriff auf Benachrichtigungen."
  hint "$(L "Notification access can read all notifications. Spot Clock only reads media sessions; the code is open (apps/clock/src/.../NowPlayingService.java)." \
            "Benachrichtigungszugriff kann alle Benachrichtigungen lesen. Spot Clock liest nur Medien-Sitzungen; der Code ist offen (apps/clock/src/.../NowPlayingService.java).")"
  if ask_yes_no "$(L "Allow notification access for Spot Clock now via adb?" \
                     "Benachrichtigungszugriff für Spot Clock jetzt per adb erlauben?")" N; then
    adb_sh cmd notification allow_listener "$LISTENER_COMPONENT"
    ok "$(L "Notification access allowed" "Benachrichtigungszugriff erlaubt")"
    return 0
  fi
  adb_sh am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS >/dev/null
  msg "The notification access settings are open on the Spot:" "Auf dem Spot sind die Einstellungen für Benachrichtigungszugriff offen:"
  howto "$(L "Tap \"Spot Clock\"." "Auf \"Spot Clock\" tippen.")" \
        "$(L "Switch on \"Allow notification access\" and confirm with \"Allow\"." \
             "\"Benachrichtigungszugriff erlauben\" einschalten und mit \"Zulassen\" bestätigen.")"
  if wait_setting secure enabled_notification_listeners "$LISTENER_COMPONENT" 300; then
    ok "$(L "Notification access allowed" "Benachrichtigungszugriff erlaubt")"
  else
    warn "$(L "Not allowed - the clock works, only the now-playing face stays off." \
              "Nicht erlaubt - die Uhr funktioniert, nur die Musik-Anzeige bleibt aus.")"
  fi
}

phase_clock() {
  ensure_device || return 1

  step "$(L "Install Spot Clock" "Spot Clock installieren")"
  obtain_apk spot-clock || return 1
  install_apk "$APK_PATH" "$CLOCK_PKG" || return 1

  _clock_listener

  step "$(L "Home screen and screensaver" "Startbildschirm und Bildschirmschoner")"
  adb_sh cmd package set-home-activity "$CLOCK_ACTIVITY" >/dev/null
  adb_sh settings put secure screensaver_components "$DREAM_COMPONENT"
  adb_sh settings put secure screensaver_enabled 1
  adb_sh settings put secure screensaver_activate_on_sleep 1
  adb_sh settings put secure screensaver_activate_on_dock 1
  ok "$(L "Spot Clock is the home screen and the screensaver" "Spot Clock ist Startbildschirm und Bildschirmschoner")"
  hint "$(L "On the Spot: tap the clock to open the app list; \"Back to clock\" returns." \
            "Auf dem Spot: Tippen auf die Uhr öffnet die App-Liste; \"Zur Uhr\" führt zurück.")"

  step "$(L "Weather" "Wetter")"
  if _clock_place; then
    adb_sh am start -n "$CLOCK_ACTIVITY" --es lat "$CFG_LAT" --es lon "$CFG_LON" --es place "$(shq "$CFG_PLACE")" >/dev/null
    ok "$(L "Weather place set" "Wetter-Ort gesetzt"): $CFG_PLACE"
  else
    adb_sh am start -n "$CLOCK_ACTIVITY" >/dev/null
  fi
  see_docs 07-clock.md
}
