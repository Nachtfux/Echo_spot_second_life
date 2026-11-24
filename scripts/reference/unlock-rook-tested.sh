#!/bin/bash
# amonet-rook v2.0.0 fastbrick unlock from macOS (Apple Silicon).
# Same logic as amonet's fastbrick.sh flash_payload(), but with
# `fastboot -S 1G` (techo5 #394: fastbrick.img is 32 bytes over the Spot's
# max-download-size, so current fastboot refuses it without -S).
# Writes ONLY via `fastboot flash brick` - the exploit payload does the rest.
set -u
AM="${AM:?path to extracted amonet-rook-v2.0.0/amonet}"
FB="${FB:-fastboot}"
IMG="$AM/bin/fastbrick.img"
MAX_ATTEMPTS=15

ts() { date +%T; }
die() { echo "[$(ts)] ABBRUCH: $*"; exit 1; }

# ---------------- pre-checks (read-only) ----------------
[ -f "$IMG" ] || die "fastbrick.img fehlt"
[ "$(wc -c < "$IMG" | tr -d ' ')" = 114294816 ] || die "fastbrick.img hat unerwartete Groesse"
n="$($FB devices 2>/dev/null | grep -c fastboot)"
[ "$n" = 1 ] || die "erwarte genau 1 Fastboot-Geraet, gefunden: $n"
prod="$($FB getvar product 2>&1 | awk '/^product:/ {print $2; exit}')"
[ "$prod" = ROOK ] || die "product='$prod' (erwartet ROOK)"
unl="$($FB getvar unlock_status 2>&1 | awk '/^unlock_status:/ {print tolower($2); exit}')"
[ "$unl" = false ] || die "unlock_status='$unl' - bereits entsperrt? Dann nicht erneut ausfuehren."
lk="$($FB getvar lk_build_desc 2>&1 | awk -F': ' '/^lk_build_desc:/ {print $2; exit}')"
case "$lk" in c1c79aa-20220824_171625*) ;; *) die "unerwartetes lk_build_desc '$lk'";; esac
echo "[$(ts)] Checks ok: ROOK, gesperrt, Fire OS 5.5.6.9 (lk $lk)"

# ---------------- send payload (fastbrick.sh logic) ----------------
cd "$AM" || die "cd $AM"
attempt=1; success=0
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  echo "[$(ts)] Versuch $attempt: fastboot -S 1G flash brick bin/fastbrick.img"
  OUT="$(gtimeout 8 "$FB" -S 1G flash brick bin/fastbrick.img 2>&1)"; EC=$?
  printf '%s\n' "$OUT" | sed 's/^/      /'
  echo "      exit=$EC"
  if printf '%s' "$OUT" | grep -q "eMMC-RO"; then die "eMMC ist dauerhaft read-only (Hardwaredefekt). Geraet unveraendert."; fi
  if printf '%s' "$OUT" | grep -q "Device mismatch"; then die "Device mismatch - falsches Payload."; fi
  if printf '%s' "$OUT" | grep -q -E "unaligned|greater than the maximum download size"; then die "fastboot lehnt Image ab (-S wirkt nicht)."; fi
  if [ "$EC" -eq 124 ]; then echo "[$(ts)] Timeout beim Senden -> Exploit laeuft vermutlich. Keine weiteren Sendeversuche."; success=1; break; fi
  sleep 2; attempt=$((attempt + 1))
done
[ "$success" = 1 ] || die "nach $MAX_ATTEMPTS Versuchen kein Erfolg. Strom NICHT trennen, erst Lage pruefen."

# ---------------- wait for TWRP (read-only) ----------------
echo "[$(ts)] Warte bis zu 6 min auf TWRP (adb recovery). NICHT abstecken!"
end=$((SECONDS + 360))
while [ "$SECONDS" -lt "$end" ]; do
  st="$(adb get-state 2>/dev/null | tr -d '\r')"
  if [ "$st" = recovery ]; then
    echo "[$(ts)] TWRP ist da."
    adb devices -l
    echo "ro.product.device=$(adb shell getprop ro.product.device | tr -d '\r')"
    echo "twrp=$(adb shell getprop ro.twrp.version | tr -d '\r')"
    exit 0
  fi
  sleep 3
done
echo "[$(ts)] Nach 6 min kein TWRP per adb sichtbar. Bitte Bildschirm des Spot pruefen."
echo "fastboot: $($FB devices 2>/dev/null)"
exit 2
