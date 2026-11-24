# Echo Spot Second Life — maintainer spec

Single source of truth for contributors (and for the agents that helped build this repo).
User-facing docs live in `docs/en/` and `docs/de/`; this file defines the contracts they describe.

## 1. Goal

Turn an **Amazon Echo Spot, 1st gen (2017/2018, codename `rook`)** into an open device:
unlock → LineageOS 18.1 (Android 11) → a voice assistant that starts **Claude or ChatGPT
speech-to-speech (voice mode)** by wake word, plus offline voice commands (Spotify, internet radio,
timer, alarm) and a full-screen round clock. Everything is driven by **one script: `./install.sh`**.

Priorities: (1) voice control and S2S with Claude or ChatGPT, (2) reproducibility for beginners,
(3) honesty about what was tested.

## 2. Hard facts (verified on a real device, 2026-11-23/24)

| Item | Value |
|---|---|
| SoC / ABI | MediaTek MT8163, **armeabi-v7a (32-bit only)**, 2 GB RAM, `ro.config.low_ram=true` |
| Display | round, 480×480 px |
| Stock firmware tested | Fire OS 5.5.6.9, LK `c1c79aa-20220824_171625` |
| Hidden USB | micro-USB under a glued rubber cover on the back, between the power jack and the 3.5 mm audio jack. External power (original barrel PSU) must stay connected. |
| Fastboot entry | hold **all three top buttons** (vol −, action/mute, vol +) while plugging in power; screen shows "fastboot mode" |
| Unlock tool | amonet-rook **v2.0.0** (XDA thread https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-spot-2017-rook.4754878/ — attachment needs an XDA login, so it is downloaded manually). Zip sha256 `be856ba4c880fc2b8664d326d7b9cbe8e2f74d2cc82718cc4aa6a895c0e2418f`. `amonet/bin/fastbrick.img` is 114294816 bytes. |
| macOS quirk | current `fastboot` refuses fastbrick.img (32 bytes over max-download-size) → use `fastboot -S 1G flash brick bin/fastbrick.img` (techo5 issue #394). Tested logic: `scripts/reference/unlock-rook-tested.sh`. First attempt prints `restricted on locked hw`, second attempt times out (= exploit running) → TWRP appears ~30 s later. |
| Recovery | TWRP 3.7.0_9-0 (from amonet) |
| ROM | LineageOS 18.1 rook **v0.3**: `https://github.com/amazon-oss/releases/releases/download/lineage-18.1-rook-v0.3/lineage-18.1-20251108-UNOFFICIAL-rook.zip`, sha256 `2755428c124df88ffd3bc6a9c36db16bbe3b7e639883fd1092dc839ac5ae9d13` (also published as `.sha256sum` next to it) |
| Flash sequence (TWRP, adb) | `twrp wipe cache` → `twrp wipe data` → `twrp wipe system` → `adb push <zip> /sdcard/` → verify sha256 on device → `twrp install /sdcard/<zip>` → `reboot` |
| Aurora Store | F-Droid `https://f-droid.org/repo/com.aurora.store_76.apk`, sha256 `fd9c75d90d0f4a7c132b9b4a5a2cf1992a45e03b8d8ff988b7dcfbc0db2c4d11`, signer SHA-256 `5c83c7672b929955dc0a1db89a5e6ae4389e2eae7ec939956041694e5815f532` |
| Claude app | `com.anthropic.claude` (Play, has armeabi-v7a split) |
| ChatGPT app | `com.openai.chatgpt` v1.2026.321, minSdk 29, has `config.armeabi_v7a.apk` split |
| Spotify | `com.spotify.music` (optional) |
| Aurora install quirk | the installer confirmation can hang on this ROM. Fallback (needs `adb root`, i.e. "Rooted debugging" on): pull `/data/data/com.aurora.store/cache/Downloads/<pkg>/<versionCode>/*.apk`, check all splits have the same signer with `apksigner verify --print-certs`, then `adb install-multiple`. |
| Vosk DE | `https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip` sha256 `b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43` |
| Vosk EN | `https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip` sha256 `30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498` |
| Weather | Open-Meteo forecast + geocoding (`https://geocoding-api.open-meteo.com/v1/search?name=<place>&count=1&language=<de|en>&format=json`), no key |
| No camera | GC0312 sensor + kernel drivers exist, but no camera HAL in LineageOS 18.1 → "0 cameras". Not fixable here. |
| No hardware AEC | the ROM has no acoustic echo cancellation → our EchoGuard (half duplex) |

## 3. Repo layout

```
install.sh                  single entry point (bash 3.2 compatible — macOS default bash!)
scripts/lib.sh              shared helpers (colors, prompts, adb wrappers, download+verify, state)
scripts/<phase>.sh          one file per phase, sourced by install.sh
scripts/build-apps.sh       builds both APKs into dist/
scripts/reference/          tested one-off scripts kept for reference
apps/voice/                 Android app "Spot Voice" (package ai.pocket.heyclaude, Gradle, Kotlin)
apps/clock/                 Android app "Spot Clock" (package ai.pocket.spotclock, plain build.sh, Java 8 syntax, no lambdas)
training/                   openWakeWord training on a Mac (Apple Silicon) / Linux
models/                     trained wake/stop models (*.onnx, CC BY-NC-SA 4.0)
docs/en/, docs/de/          user docs (same files, same structure)
docs/images/                screenshots (PNG) and illustrations (SVG; text-bearing ones as *.en.svg / *.de.svg)
```

## 4. `install.sh` contract

```
./install.sh [phase ...] [options]
phases (in order): doctor unlock backup flash setup apps voice clock verify
extra commands:    status | open-link | train | build | help
options: --assistant claude|chatgpt   --lang de|en   --place "<city>"   --device <serial|ip:port>
         --spotify | --no-spotify      --from-source   --dry-run   --yes   --force
```

* No phase given → run all phases in order, **skipping phases already marked done** in `.state/done/<phase>`
  (`--force` re-runs). Asks before each phase: `Run phase "flash" now? [Y/n/skip]`.
* Config persists in `.state/config.env` (ASSISTANT, LANG, PLACE, LAT, LON, DEVICE, SPOTIFY). First run asks for
  missing values interactively (assistant, language default from `$LANG`, weather place).
* `--dry-run` prints every command (`+ adb shell …`) instead of running it, and never touches the device.
  `./install.sh --dry-run` must work **without any device and without downloads** (use placeholders).
* UI language of the script follows `--lang` (de/en). All messages exist in both languages (small `msg()` helper).
* Safety: destructive steps (unlock, wipe/flash) require typing `YES`/`JA`, never just Enter. Before `unlock`:
  check exactly one fastboot device, product `ROOK`, `unlock_status false`, LK as above; abort otherwise.
  Before `flash`: adb state `recovery`, `ro.product.device=rook`, sha256 on device equals expected.
* Security-relevant device settings (accessibility service, notification access) are only set after an explicit
  `[y/N]` prompt (default No → script opens the settings screen and waits for the user instead).
* Login data is never passed on the command line. `open-link` reads a login/magic link with `read -s` and opens
  it on the Spot via `am start -a android.intent.action.VIEW -d "$link"`; never logs or stores it.
* Downloads go to `downloads/`, are verified by sha256, re-used when present.
* APKs: default = download `spot-voice.apk`, `spot-clock.apk` and `SHA256SUMS` from the latest GitHub release of
  `Nachtfux/Echo_spot_second_life`; `--from-source` = `scripts/build-apps.sh` (needs JDK 17 + Android SDK; script
  explains how to get them).
* Host support: macOS (tested), Linux (should work; untested). Uses `timeout`/`gtimeout` if present.

### Phase details

| Phase | Steps |
|---|---|
| doctor | check `adb`, `fastboot`, `curl`, `unzip`, `python3`, `shasum`/`sha256sum`; offer `brew install android-platform-tools` / `apt install adb fastboot`. Warn about USB hubs; recommend a direct port. |
| unlock | ask the user to download the amonet zip into `downloads/` (print thread URL); verify sha256; unzip; explain hidden port + 3-button fastboot entry (with image refs); wait for `fastboot devices`; pre-checks; send payload with `-S 1G` and timeout logic exactly as in the reference script; wait up to 6 min for `adb get-state == recovery`. |
| backup | in TWRP: dump every partition from `/proc/partitions` (mmcblk0 + boot0/boot1) with `adb exec-out dd`, write `MANIFEST.sha256`; keep in `backups/<date>/` (git-ignored, contains device-unique data — never share). Reuse logic of existing `scripts/backup.sh`. |
| flash | download LineageOS + verify; wipe/push/verify/install/reboot (see §2). Tell the user first boot takes ~5 min. |
| setup | guide on-device steps with clear numbered text: finish setup wizard, connect Wi-Fi, Settings → About → tap "Build number" 7× → Developer options → enable **USB debugging** and **Rooted debugging**, allow the Mac's key. Then via adb: locale (`settings put system system_locales de-DE|en-US` + `setprop persist.sys.locale`, then `setprop ctl.restart zygote` after a 6 s wait so settings flush), timezone (ask; default from host `/etc/localtime`), `wm density 120` (makes phone-sized apps usable on 480 px), `settings put system screen_off_timeout 60000`, install Aurora (verified), `adb tcpip 5555` and store `ip:5555` as DEVICE (cable can then be removed). Recommend screen lock "None". |
| apps | install the chosen assistant app (and Spotify if wanted) through Aurora: open `market://details?id=<pkg>` with `-p com.aurora.store`, user taps Install; script polls `pm list packages`; on timeout use the split-APK fallback (§2). Then tell the user to log in (Claude: email magic link → `./install.sh open-link`; ChatGPT: email+code on the device, or `scrcpy` for a big keyboard). |
| voice | install `spot-voice.apk`; push models to `/sdcard/Android/data/ai.pocket.heyclaude/files/` (claude → `hey_claude.onnx`+`danke_claude.onnx`; chatgpt → `hey_chatgpt.onnx`+`danke_chatgpt.onnx`); download+push Vosk model of the chosen language to `files/vosk-de/` or `files/vosk-en/` (folder contents, not the zip); `pm grant ai.pocket.heyclaude android.permission.RECORD_AUDIO`; `dumpsys deviceidle whitelist +ai.pocket.heyclaude`; accessibility service `ai.pocket.heyclaude/ai.pocket.heyclaude.launch.VoiceModeA11yService` (append to existing `enabled_accessibility_services`, set `accessibility_enabled 1`) after [y/N]; set assistant + language via broadcasts (§5); start `am start-foreground-service -n ai.pocket.heyclaude/.WakeWordService`. |
| clock | install `spot-clock.apk`; notification listener `cmd notification allow_listener ai.pocket.spotclock/ai.pocket.spotclock.NowPlayingService` after [y/N]; `cmd package set-home-activity ai.pocket.spotclock/.ClockActivity`; screensaver: `settings put secure screensaver_components ai.pocket.spotclock/ai.pocket.spotclock.ClockDream`, `screensaver_enabled 1`, `screensaver_activate_on_sleep 1`, `screensaver_activate_on_dock 1`; geocode PLACE via Open-Meteo and `am start -n ai.pocket.spotclock/.ClockActivity --es lat <lat> --es lon <lon> --es place "<name>"`. |
| verify | table of checks (✓/✗ + fix hint): packages installed, a11y enabled, listener allowed, home activity, models present, Vosk present, WakeWordService running, assistant logged in (heuristic: app data size), then optional live test `am start-foreground-service -n ai.pocket.heyclaude/.WakeWordService -a ai.pocket.heyclaude.TRIGGER_NOW` and grep `adb logcat -d -s HeyClaude` for `voice-mode button tapped`. |

## 5. Voice app (`apps/voice`) contract

* Package stays `ai.pocket.heyclaude`; launcher label becomes **"Spot Voice"**.
* **Assistant profiles** (`Assistant.kt`): `CLAUDE`, `CHATGPT`, each with: id, display name, package, voice-button
  regex, voice-button exclusion regex (dictation etc.), mic-mute / mic-unmute label regexes, end-button regex,
  end-notification-action regex, assistant-name regex for spoken stop phrases, wake model file, stop model file.
  The regexes currently hard-coded in `launch/VoiceModeA11yService.kt`, `Prefs.kt` (`DEFAULT_VOICE_BUTTON_PATTERN`,
  `DICTATION_PATTERN`) and `EchoGuard.kt` (`STOP_PHRASE`) move into the profile. Claude values = current values.
  ChatGPT values are calibrated on the device with the existing `DUMP_NODES` broadcast.
* Selected via `Prefs.assistant` (default `claude`), UI switch in `MainActivity`, and broadcast
  `am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant chatgpt|claude`
  (service reloads models without reinstall).
* **Command language**: `Prefs.commandLang` (`de`|`en`, default from system locale), broadcast
  `ai.pocket.heyclaude.SET_LANG --es lang de|en`. Vosk model dir `vosk-<lang>`. `CommandParser` interface with
  `GermanParser` (existing `VoiceCommands.parse` logic + `GermanNumbers`) and new `EnglishParser` (+ `EnglishNumbers`)
  producing the same `Action`s: play <query> (Spotify), radio <station>, radio off, pause/stop, resume, next,
  previous, louder/quieter (much louder), timer for <duration>, cancel timer, alarm/wake me at <time>
  (7, 7:30, "seven thirty", "half past seven", "quarter to eight", "6 am/pm"), cancel alarm.
  Spoken stop phrases in both languages ("danke Claude", "Claude stopp", "thank you Claude", "thanks ChatGPT",
  "stop ChatGPT", "bye …").
* Existing debug broadcasts stay: `RESUME`, `QUERY_STATE`, `MIC_MUTE`, `LEVEL_LOG`, `COMMAND --es text "…"`,
  `SET_THRESHOLD --ef wake 0.7 --ef stop 0.5`, `DUMP_NODES`, service action `TRIGGER_NOW`.
* `STATE` broadcast to the clock additionally carries `--es assistant <display name>`.
* Unit tests (JVM, `app/src/test`): German and English parser tables, duration/clock parsing, profile regexes
  (Claude labels: "Mikrofon stummschalten", "Mikrofon-Stummschaltung aufheben", "Beenden", dictation "Spracheingabe
  starten" must NOT match the voice button).
* Versions: versionCode 10, versionName "1.0.0". Release signing from env (`ESL_KEYSTORE`, `ESL_KEYSTORE_PASS`,
  `ESL_KEY_ALIAS`, `ESL_KEY_PASS`), fallback = debug key.

## 6. Clock app (`apps/clock`) contract

* Label "Spot Clock". Strings in English (`res/values`) and German (`res/values-de`), chosen by system locale —
  includes complication labels (SUNRISE/AUFGANG, RAIN/REGEN, ALARM/WECKER …), weather descriptions, weekday/month
  formatting via `Locale.getDefault()`, apps screen button ("Back to clock"/"Zur Uhr").
* No personal default location anywhere (comments/examples use Berlin 52.52/13.40).
* Centre cap keeps showing assistant state; optional small assistant name from `STATE` extra.
* Same signing env vars as voice; versionCode 10, versionName "1.0.0" (`build.sh` reads `VERSION_CODE`/`VERSION_NAME`).
* Java 8 syntax, **no lambdas/method references** (android.jar lacks LambdaMetafactory with this toolchain).

## 7. Images (`docs/images/`)

Screenshots (480×480 PNG, round-masked, made with `adb exec-out screencap -p`): `shot-clock-day.png`,
`shot-clock-night.png`, `shot-now-playing.png`, `shot-timer.png`, `shot-apps.png`, `shot-claude-voice.png`,
`shot-chatgpt-voice.png`, `shot-voice-app.png`.
Illustrations (SVG, palette below, `*.en.svg` + `*.de.svg` when they contain words): `hero`, `spot-back-port`
(back view, hidden micro-USB between power and audio jack, cover removed), `fastboot-buttons` (3 top buttons held
while plugging power), `wiring` (computer ↔ USB ↔ Spot + power), `install-flow` (the 9 phases, which need hands),
`architecture` (mic → SilenceGate → openWakeWord (mel → embedding → classifiers) → Accessibility → Claude/ChatGPT
voice mode; EchoGuard; Vosk → Spotify/Radio/Timer/Alarm; Clock via STATE broadcast), `voice-session`
(timeline: wake word → voice mode → Claude talks/mic muted → user talks → stop word / 10 s silence → home).
Palette: ivory `#FAF9F5`, dark `#141413`, accent `#D97757`, gray `#B0AEA5`; rounded, calm, lots of whitespace;
system-ui sans + a serif for headings. Must stay legible on GitHub light and dark backgrounds (use a filled
background card).

## 8. Docs outline (identical in `docs/en` and `docs/de`)

`README.md` / `README.de.md` (root): hero, what you get (with screenshots), what you need, **Quick start
(`git clone … && ./install.sh`)**, feature tour, links to chapters, limitations, credits, licence, disclaimer.
Chapters: `01-hardware.md`, `02-unlock.md`, `03-lineageos.md`, `04-setup.md`, `05-voice-assistant.md`
(Claude **and** ChatGPT S2S: how it works, switching, wake/stop words, echo guard, ending a conversation, tuning
thresholds), `06-voice-commands.md` (full command tables DE + EN), `07-clock.md`, `08-wakeword-training.md`,
`09-troubleshooting.md` (FAQ incl. camera, adb over Wi-Fi resets after reboot, false alarms, Aurora hang,
echo, recovery/unbrick via backup), `10-architecture.md`. Each chapter: goal, time needed, hands-on steps,
what `install.sh` does for you (exact phase), verify, troubleshooting links, prev/next nav, language switch link.

## 9. Privacy rules (never in the repo)

Wi-Fi names/passwords, e-mail addresses, login links/tokens, device serial numbers, LAN IPs (use
`192.168.x.y:5555` placeholders), the maintainer's home location, partition backups, personal chat content in
screenshots.

## 10. Honesty

Docs state what was tested: unlock + flash were done once on a real Spot on 2026-11-23 (macOS, Apple Silicon) and
are **not** re-run for every release; setup → verify phases are tested against a real device; Linux untested;
Windows not supported by `install.sh` (amonet itself ships `.bat/.ps1`). Third-party projects (amonet by R0rt1z2
and contributors, amazon-oss LineageOS, TWRP, openWakeWord, Vosk, Piper, Aurora Store, Open-Meteo,
radio-browser.info) are credited in `THIRD_PARTY.md`. Not affiliated with Amazon, Anthropic, OpenAI or Spotify.
