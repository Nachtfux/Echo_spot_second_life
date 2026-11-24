<sub>**English** · [Deutsch](../de/04-setup.md)</sub>

# 4 · Android setup

> **Goal:** finish the first start of LineageOS, switch on developer options, and let the installer set language, time zone, display and Aurora Store · **Time:** about 20 minutes · **Installer:** `./install.sh setup`

LineageOS is installed and shows its setup wizard ([chapter 3](03-lineageos.md)). In this chapter you tap through a
few screens on the Spot yourself, until your computer is allowed to talk to it. Then the installer takes over.

A few things that help on the small round screen:

- The screen is 480 × 480 pixels and round, so the corners of menus are cut off. Scroll a little if something is hidden.
- The **middle button on top** works as the power button under LineageOS: a short press turns the screen on or off.
- Keep the USB cable and the power supply connected.

> [!TIP]
> You can start `./install.sh setup` right now. It shows the same steps as below on your computer and waits up to
> 30 minutes until the Spot shows up with USB debugging.

## Step 1: The setup wizard

1. Wait until the wizard appears. The first start takes about 5 minutes.
2. **Language:** choose any language. The installer sets German or English later, depending on `--lang`.
3. **Wi-Fi:** choose your network and enter its password.
4. **Date and time:** accept what is shown. The installer sets the time zone later.
5. Skip everything optional. If the wizard offers a screen lock (PIN, pattern, password), skip it. See
   [Screen lock: choose "None"](#screen-lock-choose-none) for why.
6. At the end you see the LineageOS home screen.

> [!TIP]
> Typing a long Wi-Fi password on the tiny keyboard is fiddly. You can skip Wi-Fi in the wizard, finish steps 2 and
> 3 below, and then add the network from your computer with the Android 11 command
> `adb shell cmd wifi connect-network "<network name>" wpa2 "<password>"`. Note that the password then ends up in
> your shell history. This way was not tried on the Spot itself.

## Step 2: Switch on developer options

Developer options are hidden in Android until you unlock them. The menu names below are the English ones of
LineageOS 18.1; your Spot may say "About phone" instead of "About tablet".

1. Swipe up on the home screen to open the app list, then open **Settings**.
2. Scroll down and open **About tablet**.
3. Scroll to the bottom and tap **Build number** seven times. After a few taps a message counts down. At the end
   it says **"You are now a developer!"**
4. Go back to **Settings → System → Advanced → Developer options**. ("Advanced" may be folded; tap it to open.)
5. Switch on **Android debugging**. (Other Android systems call it "USB debugging".) Confirm with **OK**.
6. Switch on **Rooted debugging**. The installer needs it to set the language and the time zone.
7. Leave **ADB over network** off for now. See [adb over Wi-Fi](#adb-over-wi-fi) below.

## Step 3: Allow your computer

1. Make sure the USB cable connects the hidden port to your computer.
2. On the Spot a dialog appears: **"Allow USB debugging?"** with the RSA key fingerprint of your computer.
3. Tick **"Always allow from this computer"**.
4. Tap **Allow**.

Check on your computer:

```bash
adb devices
```

The Spot must be listed with the state `device`:

```
List of devices attached
XXXXXXXXXXXXXXXX	device
```

If it says `unauthorized`, the dialog was missed or declined. Unplug the USB cable and plug it in again; the dialog
comes back. If no dialog appears at all, switch **Android debugging** off and on again.

## Step 4: Let the installer finish the setup

```bash
./install.sh setup
```

If you have not answered it before, the installer first asks for the language (`de` or `en`). Then it asks two
questions on the way:

1. **Time zone.** The default is the time zone of your computer. Press Enter to accept it, or type one in the form
   `Area/City`, for example `Europe/Berlin` or `America/New_York`.
2. **adb over Wi-Fi.** Answer `Y` (the default) if you want to remove the USB cable afterwards.

While it runs, the Spot's screen goes black for a moment: the Android user interface restarts with the new language.

The output looks like this (shortened):

```
==> Phase 5/9: setup - first start, developer options, language, Aurora Store
     More help: docs/en/04-setup.md
[..] Waiting for the Spot with USB debugging (up to 30 minutes) ...
[ok] rook, LineageOS 18.1-20251108-UNOFFICIAL-rook

==> Language, time zone, display
?? Time zone of the Spot [Europe/Berlin]:
[..] Language en-US, time zone Europe/Berlin
[..] Waiting 6 s so the settings are written, then restarting the Android UI (the screen goes black for a moment) ...
[ok] Android UI is back
[..] Display density 120 (makes phone apps usable on the round 480 px screen), screen off after 60 s

==> Aurora Store (app store without Google account)
[..] Downloading com.aurora.store_76.apk ...
[ok] sha256 verified: downloads/com.aurora.store_76.apk
[ok] Aurora Store signer verified
[..] Installing downloads/com.aurora.store_76.apk (com.aurora.store) ...
[ok] com.aurora.store installed

==> Screen lock
Recommended - otherwise the wake word cannot open the assistant while the Spot is locked:
    1. On the Spot: Settings > Security > Screen lock.
    2. Choose "None".

==> adb over Wi-Fi
?? Use adb over Wi-Fi from now on (then the USB cable can be removed)? [Y/n]
[ok] adb over Wi-Fi: 192.168.x.y:5555 (saved). You can unplug the USB cable now - keep the power plugged in.
     After a reboot of the Spot this is off again; plug in the cable and run any ./install.sh command.
[ok] Phase "setup" done.
```

## What the installer does for you

`./install.sh setup` (phase 5 of 9):

| What | How | Why |
|---|---|---|
| Checks the Spot | `getprop ro.product.device` must be `rook`, `ro.lineage.version` must exist | Makes sure LineageOS from chapter 3 is running on the right device. |
| Root access for adb | `adb root` | Language and time zone are system settings. This needs "Rooted debugging". |
| Time zone | `settings put global auto_time_zone 0`, `setprop persist.sys.timezone <Area/City>` | Clock, alarms and the weather need the right local time. |
| Language | `settings put system system_locales en-US` (or `de-DE`), `setprop persist.sys.locale …`, wait 6 s, `setprop ctl.restart zygote` | Android only applies the language when its user interface starts, so the installer waits until the settings are saved and then restarts it. |
| Display density | `wm density 120` | Apps such as Claude and ChatGPT are built for phone screens. At density 120 the Spot's 480-pixel screen is 640 dp wide (480 × 160 / 120), so these apps show their full layout instead of a cramped one. Text gets smaller. |
| Screen timeout | `settings put system screen_off_timeout 60000` | The screen goes off after 60 seconds without touch. In [chapter 7](07-clock.md) the clock becomes the screensaver. |
| Aurora Store | downloads `com.aurora.store_76.apk` from F-Droid, checks SHA-256 `fd9c75d9…2c4d11` and, if `apksigner` is available, the signing certificate `5c83c767…15f532`, then installs it | Aurora Store installs apps from Google Play without a Google account or Google services. You use it in [chapter 5](05-voice-assistant.md). |
| Install permission for Aurora | `appops set com.aurora.store REQUEST_INSTALL_PACKAGES allow` | Aurora can install apps without the extra "allow this source" screen. |
| Screen lock | shows the steps below | The wake word must be able to open the assistant. |
| adb over Wi-Fi | reads the Spot's Wi-Fi address, `adb tcpip 5555`, `adb connect <ip>:5555`, saves the address as `DEVICE` in `.state/config.env` | All later phases work without the cable. |

If `apksigner` is not installed (see [chapter 1](01-hardware.md#your-computer)), the signature check is skipped. The
SHA-256 check always runs.

## Screen lock: choose "None"

1. On the Spot, open **Settings → Security → Screen lock**.
2. Choose **None**.

Why: when you say the wake word, Spot Voice wakes the screen and opens Claude or ChatGPT. With a PIN or pattern lock
the assistant would open behind the lock screen, and nothing would happen until someone unlocks it.

> [!NOTE]
> Without a screen lock, anyone in the room can use the Spot and the apps you are logged in to. For a device on a
> kitchen shelf that is usually fine. Decide for your home.

## adb over Wi-Fi

After setup, the installer talks to the Spot over your Wi-Fi at an address like `192.168.x.y:5555`. You can remove
the USB cable; the power supply stays connected.

**After a restart of the Spot, adb over Wi-Fi is off again.** That is how Android works. You do not need to remember
it: the next `./install.sh` command notices that the Spot does not answer, asks you to plug in the USB cable once, and
offers to switch adb over Wi-Fi back on. The same happens if your router gave the Spot a new address.

```
[!!] The Spot does not answer at 192.168.x.y:5555.
     adb over Wi-Fi switches off when the Spot reboots (or its IP address changed). Use the cable once:
```

Two ways to avoid the cable:

- In **Developer options**, LineageOS has a switch **ADB over network**. With it on, the Spot listens for adb on the
  network without the `adb tcpip` step. Only use it on a home network you trust.
- Give the Spot a fixed address in your router (often called "DHCP reservation"), so the saved address stays valid.

To connect by hand, for example for your own `adb` commands:

```bash
adb connect 192.168.x.y:5555
```

Replace `192.168.x.y` with the address that `./install.sh status` shows under "Device".

## Check

1. `./install.sh status` shows a check mark next to `setup`, and under "Device" the Spot's address `192.168.x.y:5555`.
2. On your computer:

   ```bash
   adb devices -l
   ```

   lists the Spot with its Wi-Fi address and the state `device`. While the USB cable is still plugged in, the same
   Spot shows up a second time with its serial number. Unplug the USB cable now (keep the power supply in), so that
   your own `adb` commands in later chapters do not stop with `adb: more than one device/emulator`. See
   [More than one device](09-troubleshooting.md#more-than-one-device).
3. On the Spot: the menus are in your language, everything looks smaller, and **Aurora Store** is in the app list.
4. Settings → Security → Screen lock shows **None**.

## If something goes wrong

| Message or problem | What to do |
|---|---|
| `The Spot did not show up with USB debugging.` | Check steps 2 and 3. See [Troubleshooting: adb shows unauthorized or no device](09-troubleshooting.md#adb-shows-unauthorized-or-no-device). |
| `adb devices` shows `unauthorized` | Unplug and replug the USB cable, then allow the dialog on the Spot. |
| `This is not LineageOS (no ro.lineage.version).` | The flash did not complete. Go back to [chapter 3](03-lineageos.md) and run `./install.sh flash`. |
| `adb could not switch to root` or `Language and time zone need "Rooted debugging"` | Switch on **Rooted debugging** in Developer options, then run `./install.sh setup` again. |
| `Android takes long to come back - continuing anyway.` | Wait a minute. If the screen stays black, restart the Spot (unplug the power briefly) and run `./install.sh setup` again. |
| `Aurora Store signer is wrong` | Do not install it. Delete `downloads/com.aurora.store_76.apk` and run `./install.sh setup` again. If it happens again, please open an issue. |
| `The Spot has no Wi-Fi address yet.` | Connect the Spot to Wi-Fi: Settings → Network & internet → Wi-Fi. Then run `./install.sh setup` again. |
| `adb over Wi-Fi did not answer - staying on USB.` | Is your computer in the same network? Guest networks often block devices from seeing each other. You can stay on USB. |
| Later: `The Spot does not answer at 192.168.x.y:5555.` | Plug in the USB cable and follow the installer. See [Troubleshooting: adb over Wi-Fi is gone after a reboot](09-troubleshooting.md#adb-over-wi-fi-is-gone-after-a-reboot). |

---
[← 3 · LineageOS: backup and install](03-lineageos.md) · [Overview](../../README.md) · [5 · Voice assistant: Claude or ChatGPT →](05-voice-assistant.md)
