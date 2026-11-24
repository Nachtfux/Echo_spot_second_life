#!/usr/bin/env bash
# backup.sh - phase "backup": full partition backup of the Echo Spot (rook) from TWRP.
#
# Dumps every mmcblk0 partition listed in /proc/partitions plus mmcblk0boot0/boot1 with
# `adb exec-out dd`, checks size (/proc/partitions) and sha256 (Spot vs. computer) and writes
# backups/<date>/MANIFEST.sha256 (+ PARTITIONS.txt). READ-ONLY on the device.
# cache is skipped (not needed), userdata only on request (big, personal data).
#
# The backup contains device-unique data (keys, serials): keep it private, never share it.
# Sourced by install.sh; running it directly runs "./install.sh backup".
if [ "${BASH_SOURCE[0]}" = "$0" ]; then exec bash "$(dirname "$0")/../install.sh" backup "$@"; fi
# shellcheck source-path=SCRIPTDIR source=lib.sh
[ -n "${ESL_LIB_LOADED:-}" ] || . "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

ROOK_BYNAME=/dev/block/platform/mtk-msdc.0/by-name

# Example data for --dry-run (layout of the rook guide; a real run always reads the device).
_backup_example_map() {
  cat <<'EOF'
kb /dev/block/mmcblk0p1
dkb /dev/block/mmcblk0p2
lk /dev/null
lk_real /dev/block/mmcblk0p3
tee1 /dev/null
tee1_real /dev/block/mmcblk0p4
logo /dev/block/mmcblk0p5
tee2 /dev/null
tee2_real /dev/block/mmcblk0p6
expdb /dev/block/mmcblk0p7
MISC /dev/block/mmcblk0p8
boot /dev/block/mmcblk0p9
recovery /dev/block/mmcblk0p10
system /dev/block/mmcblk0p11
cache /dev/block/mmcblk0p12
userdata /dev/block/mmcblk0p13
EOF
}

_backup_example_partitions() {
  cat <<'EOF'
major minor  #blocks  name

 179        0    3817472 mmcblk0
 179        1       1024 mmcblk0p1
 179        2       1024 mmcblk0p2
 179        3       1024 mmcblk0p3
 179        4       5120 mmcblk0p4
 179        5       8192 mmcblk0p5
 179        6       5120 mmcblk0p6
 179        7      10240 mmcblk0p7
 179        8        512 mmcblk0p8
 179        9      16384 mmcblk0p9
 179       10      16384 mmcblk0p10
 179       11    1048576 mmcblk0p11
 179       12     442368 mmcblk0p12
 179       13    2259968 mmcblk0p13
 179       32       4096 mmcblk0boot0
 179       64       4096 mmcblk0boot1
EOF
}

_backup_twrp_help() {
  msg "The Spot must be running TWRP (after the unlock it is there automatically). Otherwise:" \
      "Auf dem Spot muss TWRP laufen (nach dem Entsperren ist das automatisch so). Sonst:"
  howto "$(L "Unplug the Spot's power. Keep the USB cable connected." "Strom des Spot abziehen. USB-Kabel bleibt dran.")" \
        "$(L "Hold Volume + and plug the power back in. Let go when TWRP appears." \
             "Lauter gedrückt halten und Strom wieder einstecken. Loslassen, sobald TWRP erscheint.")"
}

phase_backup() {
  local prod map parts plan="" skipped="" total=0 dev blocks bytes label file out stamp
  local with_userdata=0 free_kb fail=0 got h_dev h_host n=0 count
  use_usb
  is_dry || need_adb || return 1
  if ! is_dry && [ "$(adb_state)" != recovery ]; then
    _backup_twrp_help
    wait_adb_state recovery 600 || { err "$(L "TWRP not found." "TWRP nicht gefunden.")"; return 1; }
  fi
  prod="$(adb_shq rook getprop ro.product.device)"
  case "$prod" in
    *rook*) ok "ro.product.device = $prod" ;;
    *) err "$(L "ro.product.device='$prod' - this does not look like an Echo Spot (rook) in TWRP. Stopping." \
                "ro.product.device='$prod' - das sieht nicht nach einem Echo Spot (rook) in TWRP aus. Abbruch.")"
       return 1 ;;
  esac

  # by-name map (for readable file names) and /proc/partitions (what is dumped, and the sizes)
  map="$(adb_shq "$(_backup_example_map)" "for f in $ROOK_BYNAME/*; do echo \"\${f##*/} \$(readlink -f \"\$f\")\"; done")"
  parts="$(adb_shq "$(_backup_example_partitions)" cat /proc/partitions)"
  if [ -z "$parts" ]; then err "$(L "Could not read /proc/partitions." "/proc/partitions nicht lesbar.")"; return 1; fi

  if ask_yes_no "$(L "Also back up userdata (about 2 GB; contains the personal data of Fire OS; not needed to restore the Spot)?" \
                     "Auch userdata sichern (ca. 2 GB; enthält die persönlichen Daten von Fire OS; zum Wiederherstellen nicht nötig)?")" N; then
    with_userdata=1
  fi

  # Plan lines: "<dev> <bytes> <label>"
  while read -r _maj _min blocks dev; do
    case "$dev" in mmcblk0p[0-9]*|mmcblk0boot0|mmcblk0boot1) ;; *) continue ;; esac
    case "$blocks" in ''|*[!0-9]*) continue ;; esac
    label="$(printf '%s\n' "$map" | awk -v d="/dev/block/$dev" '$2 == d {print $1; exit}')"
    case "$(lower "$label")" in
      cache) skipped="$skipped $dev($label)"; continue ;;
      userdata) if [ "$with_userdata" = 0 ]; then skipped="$skipped $dev($label)"; continue; fi ;;
    esac
    bytes=$((blocks * 1024))
    plan="$plan$dev $bytes ${label:--}
"
    total=$((total + bytes))
  done <<EOF
$parts
EOF
  count="$(printf '%s' "$plan" | grep -c .)"
  [ "$count" -gt 0 ] || { err "$(L "No partitions found." "Keine Partitionen gefunden.")"; return 1; }

  stamp="$(date +%Y-%m-%d_%H%M%S)"
  out="$BACKUP_DIR/$stamp"
  step "$(L "Backup plan" "Sicherungsplan"): $count $(L "partitions" "Partitionen"), $((total / 1024 / 1024)) MiB -> $(rel "$out")/"
  printf '%s' "$plan" | awk '{printf "     %-14s %-12s %12d bytes\n", $1, $3, $2}'
  [ -n "$skipped" ] && hint "$(L "skipped" "ausgelassen"):$skipped"
  warn "$(L "The backup contains device-unique data (keys, serial numbers). Keep it private - never upload or share it." \
            "Die Sicherung enthält gerätespezifische Daten (Schlüssel, Seriennummern). Privat aufbewahren - nie hochladen oder teilen.")"

  if ! is_dry; then
    mkdir -p "$out" || return 1
    free_kb="$(df -Pk "$out" | awk 'NR==2 {print $4}')"
    if [ -n "$free_kb" ] && [ $((free_kb * 1024)) -lt "$total" ]; then
      err "$(L "Not enough free space ($((free_kb / 1024)) MiB free)." "Nicht genug freier Speicher ($((free_kb / 1024)) MiB frei).")"
      return 1
    fi
  fi
  ask_yes_no "$(L "Copy $((total / 1024 / 1024)) MiB from the Spot now (read-only on the Spot, takes a few minutes)?" \
                  "Jetzt $((total / 1024 / 1024)) MiB vom Spot kopieren (nur lesend, dauert ein paar Minuten)?")" Y || return 1

  if ! is_dry; then
    {
      echo "# Echo Spot (rook) partition backup made by install.sh - PRIVATE, never share"
      echo "# date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
      echo "# product: $prod  twrp: $(adb_shq "" getprop ro.twrp.version)"
      echo "# restore help: docs/en/09-troubleshooting.md"
      echo "# --- $ROOK_BYNAME"
      printf '%s\n' "$map"
      echo "# --- /proc/partitions"
      printf '%s\n' "$parts"
    } > "$out/PARTITIONS.txt"
    : > "$out/MANIFEST.sha256"
  fi

  while read -r dev bytes label; do
    [ -n "$dev" ] || continue
    n=$((n + 1))
    if [ "$label" = "-" ]; then file="$dev.img"; else file="${dev}_${label}.img"; fi
    info "[$n/$count] $dev ($label, $((bytes / 1024)) KiB)"
    if is_dry; then
      adbx exec-out "dd if=/dev/block/$dev bs=1048576 2>/dev/null"
      printf '%s\n' "${C_DIM}+   > $(rel "$out")/$file${C_RST}" >&2
      adb_shq "" sha256sum "/dev/block/$dev" >/dev/null
      continue
    fi
    adbx exec-out "dd if=/dev/block/$dev bs=1048576 2>/dev/null" > "$out/$file"
    got="$(file_size "$out/$file")"
    if [ "$got" != "$bytes" ]; then
      err "$(L "size mismatch" "Größe stimmt nicht"): $(L "computer" "Computer") $got != Spot $bytes"
      fail=$((fail + 1)); continue
    fi
    h_dev="$(adb_shq "" sha256sum "/dev/block/$dev" | awk '{print $1}')"
    h_host="$(sha256_local "$out/$file")"
    if [ -z "$h_dev" ] || [ "$h_dev" != "$h_host" ]; then
      err "$(L "sha256 mismatch" "sha256 stimmt nicht"): Spot ${h_dev:-?} / $(L "computer" "Computer") $h_host"
      fail=$((fail + 1)); continue
    fi
    printf '%s  %s\n' "$h_host" "$file" >> "$out/MANIFEST.sha256"
    ok "$(L "size and sha256 ok" "Größe und sha256 ok")"
  done <<EOF
$plan
EOF

  if [ "$fail" -gt 0 ]; then
    err "$(L "$fail partition(s) failed verification - run ./install.sh backup again before flashing anything!" \
             "$fail Partition(en) nicht korrekt - ./install.sh backup erneut ausführen, bevor irgendetwas geflasht wird!")"
    return 1
  fi
  ok "$(L "Backup complete" "Sicherung fertig"): $(rel "$out")/ (MANIFEST.sha256)"
  hint "$(L "Copy this folder to a safe place (e.g. an external drive). You need it to restore or unbrick the Spot." \
            "Diesen Ordner an einen sicheren Ort kopieren (z. B. externe Festplatte). Er wird zum Wiederherstellen gebraucht.")"
  hint "$(L "Check later with: (cd $(rel "$out") && shasum -a 256 -c MANIFEST.sha256)" \
            "Später prüfen mit: (cd $(rel "$out") && shasum -a 256 -c MANIFEST.sha256)")"
}
