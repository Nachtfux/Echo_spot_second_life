<sub>[English](../en/03-lineageos.md) · **Deutsch**</sub>

# 3 · LineageOS: Sicherung und Installation

> **Ziel:** eine vollständige Kopie des Speichers im Spot sichern, dann Fire OS durch LineageOS 18.1 ersetzen · **Zeit:** etwa 30 Minuten, meist Warten · **Installer:** `./install.sh backup` und `./install.sh flash`

Der Spot ist entsperrt und zeigt TWRP ([Kapitel 2](02-unlock.md)). Jetzt erledigst du von TWRP aus zwei Dinge:
zuerst eine vollständige Sicherung aller Partitionen, dann die Installation von LineageOS 18.1 (Android 11). Lass
USB-Kabel und Netzteil das ganze Kapitel über angeschlossen.

## Teil 1: Sicherung

### Warum

Die Sicherung ist dein einziger Weg zurück. Mit ihr kannst du Fire OS wiederherstellen oder einen Spot reparieren,
der nicht mehr startet. Einige Partitionen enthalten Daten, die es nur auf deinem Spot gibt, zum Beispiel
gerätespezifische Schlüssel. Die kann man nirgends herunterladen. Gehen sie verloren, kann sie dir niemand
zurückgeben.

Ohne Sicherung flasht der Installer nur, wenn du ausdrücklich zustimmst.

### Was gesichert wird

Der Installer liest die Partitionstabelle vom Spot (`/proc/partitions`) und kopiert jede Partition des internen
Speichers (`mmcblk0p…`) und dazu die beiden versteckten Boot-Bereiche des Chips (`mmcblk0boot0` und
`mmcblk0boot1`). Für lesbare Dateinamen nutzt er die Partitionsnamen aus TWRP. Eine typische Liste:

| Partition | Was drin ist (grob) | Gesichert? |
|---|---|---|
| `kb`, `dkb` | gerätespezifische Schlüssel | ja |
| `lk`, `tee1`, `tee2` (nach dem Entsperren evtl. als `lk_real`, `tee1_real`, `tee2_real`) | Bootloader und Firmware der „Secure World“ | ja |
| `logo`, `expdb`, `MISC` | Startlogo, Protokolle, Starteinstellungen | ja |
| `boot`, `recovery` | Kernel von Fire OS, Recovery (nach dem Entsperren TWRP) | ja |
| `system` | Fire OS selbst | ja |
| `mmcblk0boot0`, `mmcblk0boot1` | die Boot-Bereiche des Chips | ja |
| `cache` | temporäre Dateien | nein, nicht nötig |
| `userdata` | deine persönlichen Daten aus Fire OS, etwa 2 GB | nur wenn du mit `j` antwortest (zum Wiederherstellen des Spot nicht nötig) |

Bevor er etwas kopiert, zeigt der Installer den genauen Plan mit allen Partitionen und der Gesamtgröße. Auf dem
getesteten Spot war die Sicherung etwa 1,7 GB groß.

### Wohin sie kommt

In einen neuen Ordner pro Sicherung, benannt nach Datum und Uhrzeit:

```
backups/2026-11-23_101500/
├── mmcblk0p1_kb.img
├── mmcblk0p2_dkb.img
├── …
├── mmcblk0p11_system.img
├── mmcblk0boot0.img
├── mmcblk0boot1.img
├── MANIFEST.sha256     Prüfsumme jedes Images
└── PARTITIONS.txt      Namen und Größen der Partitionen (keine Seriennummer)
```

Git ignoriert den Ordner `backups/`. Er landet also nie in einem Commit.

> [!WARNING]
> Die Sicherung enthält gerätespezifische Daten (Schlüssel, Seriennummern). Behalte sie für dich. Lade sie nirgends
> hoch, häng sie an keinen Fehlerbericht und teile sie in keinem Forum.

### Die Sicherung erstellen

1. Achte darauf, dass der Spot TWRP zeigt. Nach dem Entsperren ist das so. Falls nicht, starte TWRP: Netzteil
   abziehen, **Lauter (+)** gedrückt halten und das Netzteil wieder einstecken.
2. Starte:

   ```bash
   ./install.sh backup
   ```

3. Beantworte die Frage nach userdata. Die Vorgabe `N` passt:

   ```
   ?? Auch userdata sichern (ca. 2 GB; enthält die persönlichen Daten von Fire OS; zum Wiederherstellen nicht nötig)? [j/N]
   ```

4. Lies den Plan und bestätige mit Enter:

   ```
   ?? Jetzt 1095 MiB vom Spot kopieren (nur lesend, dauert ein paar Minuten)? [J/n]
   ```

   (Deine Zahl ist eine andere.)
5. Warte. Für jede Partition siehst du eine Zeile wie `[3/13] mmcblk0p3 (lk_real, 1024 KiB)` und danach
   `[ok] Größe und sha256 ok`. Der Installer liest nur vom Spot; dort wird nichts verändert.
6. Am Ende zeigt der Installer den Ordner:

   ```
   [ok] Sicherung fertig: backups/2026-11-23_101500/ (MANIFEST.sha256)
        Diesen Ordner an einen sicheren Ort kopieren (z. B. externe Festplatte). Er wird zum Wiederherstellen gebraucht.
   ```

7. Kopier den Ordner an einen zweiten Ort, zum Beispiel auf eine externe Festplatte.

Eine Sicherung kannst du jederzeit prüfen:

```bash
cd backups/2026-11-23_101500 && shasum -a 256 -c MANIFEST.sha256
```

Jede Zeile muss mit `OK` enden. Unter Linux nimmst du `sha256sum -c MANIFEST.sha256`.

### Wiederherstellen, kurz erklärt

Hoffentlich brauchst du es nie, aber es hilft zu wissen, wie es geht: Du startest TWRP (Lauter gedrückt halten und
das Netzteil einstecken), kopierst die Image-Dateien zurück auf den Spot und schreibst jede in ihre Partition. Nicht
jede Partition sollte man leichtfertig zurückschreiben; vor allem die Teile des Bootloaders brauchen Sorgfalt. Die
genauen Befehle stehen in [Fehlerbehebung: Sicherung wiederherstellen](09-troubleshooting.md#restore-a-backup).

## Teil 2: LineageOS 18.1 installieren

### Was du installierst

| | |
|---|---|
| System | LineageOS 18.1 (Android 11), inoffizieller Build für den Echo Spot (`rook`) |
| Von | dem Projekt [amazon-oss](https://github.com/amazon-oss/releases/releases/tag/lineage-18.1-rook-v0.3) |
| Version | v0.3 |
| Datei | `lineage-18.1-20251108-UNOFFICIAL-rook.zip`, etwa 1 GB |
| Download | `https://github.com/amazon-oss/releases/releases/download/lineage-18.1-rook-v0.3/lineage-18.1-20251108-UNOFFICIAL-rook.zip` |
| SHA-256 | `2755428c124df88ffd3bc6a9c36db16bbe3b7e639883fd1092dc839ac5ae9d13` (liegt auch als `.sha256sum` neben der ZIP-Datei) |

Der Installer lädt die Datei nach `downloads/` und prüft sie. Liegt sie schon dort und stimmt, nimmt er sie wieder.
Eine Datei mit falscher Prüfsumme benennt er in `….bad` um und lädt sie beim nächsten Lauf neu.

Selbst prüfen kannst du sie so:

```bash
shasum -a 256 downloads/lineage-18.1-20251108-UNOFFICIAL-rook.zip
```

### Installieren

> [!WARNING]
> Das löscht Fire OS und alles auf dem Spot. Stell sicher, dass die Sicherung aus Teil 1 vollständig ist.

1. Lass den Spot in TWRP, per USB verbunden und am Netzteil.
2. Starte:

   ```bash
   ./install.sh flash
   ```

3. Der Installer lädt LineageOS herunter (etwa 1 GB) und prüft es. Dann prüft er den Spot: TWRP läuft, es ist ein
   `rook`, und in `backups/` liegt eine Sicherung.
4. Wenn er fragt, tipp `JA` ein und drück Enter:

   ```
   !! Das LÖSCHT Fire OS und alles auf dem Spot (wipe cache, data, system) und installiert LineageOS 18.1.
      JA eintippen zum Fortfahren, alles andere bricht ab:
   ```

5. Schau aufs Display des Spot. TWRP zeigt dort den Fortschritt. Zieh nichts ab. Die Installation dauert etwa
   2–4 Minuten.
6. Der Spot startet von selbst neu. Weiter geht es mit [Der erste Start](#der-erste-start).

### Was passiert, Schritt für Schritt

| # | Schritt | Befehl auf dem Spot |
|---|---|---|
| 1 | Cache löschen | `twrp wipe cache` |
| 2 | Benutzerdaten löschen | `twrp wipe data` |
| 3 | Altes System löschen | `twrp wipe system` |
| 4 | ZIP-Datei auf den Spot kopieren | `adb push lineage-…-rook.zip /sdcard/` |
| 5 | Kopie auf dem Spot prüfen | `sha256sum /sdcard/lineage-…-rook.zip` muss zum erwarteten Wert passen |
| 6 | Installieren | `twrp install /sdcard/lineage-…-rook.zip` |
| 7 | Aufräumen | die ZIP-Datei wird aus `/sdcard` gelöscht |
| 8 | Neu starten | `adb reboot` |

Die Ausgabe sieht so aus (gekürzt; `…` steht für die Meldungen von TWRP selbst):

```
==> Löschen
[..] twrp wipe cache
      …
[..] twrp wipe data
      …
[..] twrp wipe system
      …

==> LineageOS auf den Spot kopieren
[ok] sha256 auf dem Spot geprüft

==> LineageOS installieren (ca. 2-4 Minuten)
     Auf dem Display zeigt TWRP den Fortschritt. Nichts abstecken.
[..] twrp install /sdcard/lineage-18.1-20251108-UNOFFICIAL-rook.zip
      …
[ok] LineageOS installiert.

==> Erster Start
Der Spot startet jetzt zum ersten Mal LineageOS. Das dauert ca. 5 Minuten - die Startanimation läuft evtl. lange. Strom angeschlossen lassen.
```

Meldungen wie „Failed to mount …“ in der Ausgabe von TWRP sind direkt nach dem Löschen normal.

### Der erste Start

Der erste Start von LineageOS dauert **etwa 5 Minuten**. Die Startanimation läuft womöglich lange; das ist normal.
Lass das Netzteil dran und drück keine Tasten. Danach zeigt der Spot den Einrichtungsassistenten von LineageOS.

Richte noch nichts ein: Das nächste Kapitel führt dich durch den Assistenten, und `./install.sh setup` wartet auf
dich.

## Was der Installer für dich erledigt

`./install.sh backup` (Phase 3 von 9):

- prüft, ob auf dem Spot TWRP läuft und ob es ein `rook` ist (läuft TWRP nicht, zeigt er, wie du es startest, und
  wartet);
- liest die Partitionsnamen und `/proc/partitions` vom Spot;
- fragt, ob `userdata` mit soll, lässt `cache` aus und zeigt den Plan mit allen Größen;
- prüft, ob auf deinem Computer genug Platz frei ist;
- kopiert jede Partition mit `adb exec-out dd` (auf dem Spot nur lesend);
- vergleicht Größe und SHA-256-Prüfsumme jedes Images auf dem Spot und auf deinem Computer;
- schreibt `MANIFEST.sha256` und `PARTITIONS.txt` und bricht mit einem Fehler ab, wenn ein Image nicht passt.

`./install.sh flash` (Phase 4 von 9):

- lädt LineageOS 18.1 rook v0.3 herunter und prüft die SHA-256-Prüfsumme (oder nimmt eine korrekte Datei aus
  `downloads/`);
- prüft: adb-Status `recovery` (TWRP), `ro.product.device` ist `rook`, eine Sicherung ist vorhanden (ohne fragt er
  nach, Vorgabe Nein);
- bittet dich, `JA` einzutippen;
- löscht Cache, Daten und System, kopiert die ZIP-Datei, prüft ihre Prüfsumme auf dem Spot, installiert sie, löscht
  die ZIP-Datei vom Spot und startet neu.

## Prüfen

- **Sicherung:** `./install.sh status` zeigt die neueste Sicherung und die Zahl ihrer Dateien. Die Prüfung mit dem
  Manifest oben meldet `OK` für jede Datei.
- **LineageOS:** Nach ein paar Minuten zeigt der Spot den Einrichtungsassistenten von LineageOS.
- `adb devices` listet den Spot jetzt **nicht** auf. Das ist so gewollt: In einem frischen LineageOS ist
  USB-Debugging aus. Du schaltest es in [Kapitel 4](04-setup.md) ein.

## Wenn etwas schiefgeht

| Problem | Was du tust |
|---|---|
| `TWRP nicht gefunden.` | Starte TWRP mit Lauter, während du das Netzteil einsteckst, und führ denselben Befehl noch einmal aus. Siehe [Fehlerbehebung: Das Entsperren bricht ab oder TWRP erscheint nicht](09-troubleshooting.md#the-unlock-stops-or-twrp-does-not-appear). |
| `ro.product.device='…' - das sieht nicht nach einem Echo Spot (rook) in TWRP aus` (bei `flash`: `ro.product.device='…' (erwartet rook)`) | Ein anderes Android-Gerät ist angeschlossen. Steck es ab. |
| `Nicht genug freier Speicher` | Schaff etwas Platz und starte `./install.sh backup` noch einmal. |
| `Größe stimmt nicht` oder `sha256 stimmt nicht` während der Sicherung | Die Kopie wurde beschädigt, oft durch ein wackeliges Kabel oder einen Hub. Starte `./install.sh backup` noch einmal, **bevor** du irgendetwas flashst. |
| `Download fehlgeschlagen` | Prüf deine Internetverbindung und starte `./install.sh flash` noch einmal. |
| `sha256 auf dem Spot falsch` | Die Kopie auf dem Spot ist beschädigt. Starte `./install.sh flash` noch einmal. |
| `Die Installation meldete einen Fehler` | Der Spot bleibt in TWRP. Starte ihn **nicht** neu. Lies die Meldungen über dem Fehler und schau dann unter [Fehlerbehebung: LineageOS startet nicht](09-troubleshooting.md#lineageos-does-not-start). |
| Die Startanimation läuft länger als 15 Minuten | Siehe [Fehlerbehebung: LineageOS startet nicht](09-troubleshooting.md#lineageos-does-not-start). Mit deiner Sicherung kommst du zurück: [Sicherung wiederherstellen](09-troubleshooting.md#restore-a-backup). |

---
[← 2 · Den Spot entsperren](02-unlock.md) · [Übersicht](../../README.de.md) · [4 · Android einrichten →](04-setup.md)
