<sub>**English** · [Deutsch](../de/03-lineageos.md)</sub>

# 3 · LineageOS: backup and install

> **Goal:** save a complete copy of the Spot's storage, then replace Fire OS with LineageOS 18.1 · **Time:** about 30 minutes, mostly waiting · **Installer:** `./install.sh backup` and `./install.sh flash`

The Spot is unlocked and shows TWRP ([chapter 2](02-unlock.md)). Now you do two things from TWRP: first a full
backup of every partition, then the installation of LineageOS 18.1 (Android 11). Keep the USB cable and the power
supply connected for the whole chapter.

## Part 1: Backup

### Why

The backup is your only way back. With it you can restore Fire OS or repair a Spot that no longer starts. Some
partitions hold data that exists only on your Spot, such as device-unique keys. They cannot be downloaded anywhere,
so if they get lost, nobody can give them back to you.

The installer will not flash without a backup unless you explicitly say so.

### What is saved

The installer reads the partition table from the Spot (`/proc/partitions`) and copies every partition of the
internal storage (`mmcblk0p…`) plus the two hidden boot areas of the chip (`mmcblk0boot0` and `mmcblk0boot1`).
It uses the partition names from TWRP for readable file names. A typical list:

| Partition | What it holds (roughly) | Saved? |
|---|---|---|
| `kb`, `dkb` | device-unique keys | yes |
| `lk`, `tee1`, `tee2` (after the unlock they can show up as `lk_real`, `tee1_real`, `tee2_real`) | bootloader and secure-world firmware | yes |
| `logo`, `expdb`, `MISC` | boot logo, logs, boot settings | yes |
| `boot`, `recovery` | Fire OS kernel, recovery (TWRP after the unlock) | yes |
| `system` | Fire OS itself | yes |
| `mmcblk0boot0`, `mmcblk0boot1` | the chip's boot areas | yes |
| `cache` | temporary files | no, not needed |
| `userdata` | your personal Fire OS data, about 2 GB | only if you answer `y` (not needed to restore the Spot) |

Before it copies anything, the installer prints the exact plan with every partition and the total size. Without
`userdata` expect a little over 1 GB (the example below shows 1095 MiB); with `userdata` about 2 GB more. The backup
of the tested Spot was about 1.7 GB.

### Where it goes

Into a new folder per backup, named after the date and time:

```
backups/2026-11-23_101500/
├── mmcblk0p1_kb.img
├── mmcblk0p2_dkb.img
├── …
├── mmcblk0p11_system.img
├── mmcblk0boot0.img
├── mmcblk0boot1.img
├── MANIFEST.sha256     checksum of every image
└── PARTITIONS.txt      partition names and sizes (no serial number)
```

The `backups/` folder is ignored by git, so it never ends up in a commit.

> [!WARNING]
> The backup contains device-unique data (keys, serial numbers). Keep it private. Never upload it, attach it to a
> bug report or share it in a forum.

### Make the backup

1. Make sure the Spot shows TWRP. After the unlock it does. If not, start TWRP: unplug the power, hold
   **Volume +** and plug the power back in.
2. Run:

   ```bash
   ./install.sh backup
   ```

3. Answer the question about userdata. The default `N` is fine:

   ```
   ?? Also back up userdata (about 2 GB; contains the personal data of Fire OS; not needed to restore the Spot)? [y/N]
   ```

4. Read the plan, then confirm with Enter:

   ```
   ?? Copy 1095 MiB from the Spot now (read-only on the Spot, takes a few minutes)? [Y/n]
   ```

   (Your number will differ.)
5. Wait. For every partition you see a line like `[3/13] mmcblk0p3 (lk_real, 1024 KiB)` and then
   `[ok] size and sha256 ok`. The installer only reads from the Spot; nothing on it is changed.
6. At the end the installer shows the folder:

   ```
   [ok] Backup complete: backups/2026-11-23_101500/ (MANIFEST.sha256)
        Copy this folder to a safe place (e.g. an external drive). You need it to restore or unbrick the Spot.
   ```

7. Copy the folder to a second place, for example an external drive.

You can check a backup at any time:

```bash
(cd backups/2026-11-23_101500 && shasum -a 256 -c MANIFEST.sha256)
```

Use the folder name of your own backup; the installer prints this exact line at the end of the backup. The
brackets keep your terminal in the project folder. Every line must end with `OK`. On Linux use `sha256sum -c
MANIFEST.sha256` instead of `shasum -a 256 -c MANIFEST.sha256`.

### Restoring, in short

You hope you never need it, but it helps to know how it works: you start TWRP (Volume + while plugging in the
power), copy the image files back to the Spot and write each one to its partition. Not every partition should be
written back casually; the bootloader parts in particular need care. Restoring is **not automated** by the installer
and was not tested in this project. [Troubleshooting: restore a backup](09-troubleshooting.md#restore-a-backup)
explains the safe order of steps and where to find the unbrick instructions.

## Part 2: Install LineageOS 18.1

### What you install

| | |
|---|---|
| System | LineageOS 18.1 (Android 11), unofficial build for the Echo Spot (`rook`) |
| Made by | the [amazon-oss](https://github.com/amazon-oss/releases/releases/tag/lineage-18.1-rook-v0.3) project |
| Version | v0.3 |
| File | `lineage-18.1-20251108-UNOFFICIAL-rook.zip`, about 1 GB |
| Download | `https://github.com/amazon-oss/releases/releases/download/lineage-18.1-rook-v0.3/lineage-18.1-20251108-UNOFFICIAL-rook.zip` |
| SHA-256 | `2755428c124df88ffd3bc6a9c36db16bbe3b7e639883fd1092dc839ac5ae9d13` (also published as `.sha256sum` next to the zip) |

The installer downloads the file into `downloads/` and checks it. If the file is already there and correct, it is
reused. A file with a wrong checksum is renamed to `….bad` and downloaded again on the next run.

To check it yourself:

```bash
shasum -a 256 downloads/lineage-18.1-20251108-UNOFFICIAL-rook.zip
```

### Install it

> [!WARNING]
> This erases Fire OS and everything on the Spot. Make sure the backup from Part 1 is complete.

1. Leave the Spot in TWRP, connected by USB and to its power supply.
2. Run:

   ```bash
   ./install.sh flash
   ```

3. The installer downloads LineageOS (about 1 GB) and checks it. Then it checks the Spot: TWRP is running, it is a
   `rook`, and there is a backup in `backups/`.
4. When it asks, type `YES` and press Enter:

   ```
   !! This ERASES Fire OS and everything on the Spot (wipe cache, data, system) and installs LineageOS 18.1.
      Type YES to continue, anything else stops:
   ```

5. Watch the Spot's screen. TWRP shows the progress. Do not unplug anything. The installation takes about 2–4 minutes.
6. The Spot restarts on its own. Go on with [First boot](#first-boot).

### What happens, step by step

| # | Step | Command on the Spot |
|---|---|---|
| 1 | Wipe the cache | `twrp wipe cache` |
| 2 | Wipe the user data | `twrp wipe data` |
| 3 | Wipe the old system | `twrp wipe system` |
| 4 | Copy the zip to the Spot | `adb push lineage-…-rook.zip /sdcard/` |
| 5 | Check the copy on the Spot | `sha256sum /sdcard/lineage-…-rook.zip` must match the expected value |
| 6 | Install | `twrp install /sdcard/lineage-…-rook.zip` |
| 7 | Clean up | the zip is deleted from `/sdcard` |
| 8 | Restart | `adb reboot` |

The output looks like this (shortened; `…` stands for TWRP's own messages):

```
==> Wipe
[..] twrp wipe cache
      …
[..] twrp wipe data
      …
[..] twrp wipe system
      …

==> Copy LineageOS to the Spot
[ok] sha256 on the Spot verified

==> Install LineageOS (about 2-4 minutes)
     On the screen TWRP shows the installation progress. Do not unplug anything.
[..] twrp install /sdcard/lineage-18.1-20251108-UNOFFICIAL-rook.zip
      …
[ok] LineageOS installed.

==> First start
The Spot now starts LineageOS for the first time. This takes about 5 minutes - the boot animation may run for a long time. Keep the power connected.
```

Messages like "Failed to mount …" in TWRP's output are normal right after a wipe.

### First boot

The first start of LineageOS takes **about 5 minutes**. The boot animation may run for a long time; that is normal.
Keep the power connected and do not press any buttons. When it is done, the Spot shows the LineageOS setup wizard.

Do not set anything up yet: the next chapter walks you through the wizard, and `./install.sh setup` waits for you.

## What the installer does for you

`./install.sh backup` (phase 3 of 9):

- checks that the Spot is running TWRP and is a `rook` (if TWRP is not running, it shows how to start it and waits);
- reads the partition names and `/proc/partitions` from the Spot;
- asks whether to include `userdata`, skips `cache`, and prints the plan with sizes;
- checks that your disk has enough free space;
- copies every partition with `adb exec-out dd` (read-only on the Spot);
- compares size and SHA-256 of every image on the Spot and on your computer;
- writes `MANIFEST.sha256` and `PARTITIONS.txt`, and stops with an error if any image does not match.

`./install.sh flash` (phase 4 of 9):

- downloads LineageOS 18.1 rook v0.3 and checks its SHA-256 (or reuses a correct file in `downloads/`);
- checks: adb state `recovery` (TWRP), `ro.product.device` is `rook`, a backup exists (without one it asks, default No);
- asks you to type `YES`;
- wipes cache, data and system, copies the zip, checks its SHA-256 on the Spot, installs it, deletes the zip from the Spot and restarts.

## Check

- **Backup:** `./install.sh status` lists the newest backup and its number of files. The manifest check above
  reports `OK` for every file.
- **LineageOS:** after a few minutes the Spot shows the LineageOS setup wizard.
- `adb devices` does **not** list the Spot at this point. That is expected: USB debugging is off in a fresh
  LineageOS. You switch it on in [chapter 4](04-setup.md).

> [!NOTE]
> Trying the flash phase as a dry run on its own (`./install.sh --dry-run --yes flash`) ends with
> `[xx] Phase "flash" did not complete.` That is the backup check at work: without a backup in `backups/` it asks
> `Continue WITHOUT a backup?`, and `--yes` takes the default answer, No. A dry run that includes the backup phase
> (`./install.sh --dry-run --yes backup flash`, or the full `./install.sh --dry-run --yes`) goes through.

## If something goes wrong

| Problem | What to do |
|---|---|
| `TWRP not found.` | Start TWRP with Volume + while plugging in the power, then run the same command again. See [Troubleshooting: the unlock stops or TWRP does not appear](09-troubleshooting.md#the-unlock-stops-or-twrp-does-not-appear). |
| `ro.product.device='…' - this does not look like an Echo Spot (rook)` | Another Android device is connected. Unplug it. |
| `Not enough free space` | Free some disk space and run `./install.sh backup` again. |
| `size mismatch` or `sha256 mismatch` during the backup | The copy was damaged, often by a loose cable or a hub. Run `./install.sh backup` again **before** you flash anything. |
| `Download failed` | Check your internet connection and run `./install.sh flash` again. |
| `sha256 on the Spot is wrong` | The copy to the Spot was damaged. Run `./install.sh flash` again. |
| `The installation reported an error` | The Spot stays in TWRP. Do **not** restart it. Read the messages above the error, then see [Troubleshooting: LineageOS does not start](09-troubleshooting.md#lineageos-does-not-start). |
| The boot animation runs for more than 15 minutes | See [Troubleshooting: LineageOS does not start](09-troubleshooting.md#lineageos-does-not-start). Your backup lets you go back: [restore a backup](09-troubleshooting.md#restore-a-backup). |

---
[← 2 · Unlocking the Spot](02-unlock.md) · [Overview](../../README.md) · [4 · Android setup →](04-setup.md)
