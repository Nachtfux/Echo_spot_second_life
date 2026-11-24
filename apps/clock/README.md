# Spot Clock (`ai.pocket.spotclock`)

A full-screen round analog clock for the 480×480 display of the Echo Spot (1st gen, LineageOS 18.1 /
Android 11). It is the Spot's home screen and its screensaver, and it shows what the voice assistant
([Spot Voice](../voice), package `ai.pocket.heyclaude`) is doing.

User documentation: [docs/en/07-clock.md](../../docs/en/07-clock.md) ·
[docs/de/07-clock.md](../../docs/de/07-clock.md). This file is for developers.

## What it shows

| Face | When | Content |
|---|---|---|
| Day | default | analog hands, four complications: **sun** (next sunrise/sunset), **weather** (icon, temperature, high/low, description), **rain** (today's max. probability as a ring), **date** (weekday, day, month, moon phase) |
| Night | dark room (light sensor), or 22:00–07:00 if the device has no light sensor | dimmed orange hands and hour dots, temperature, no second hand, low brightness |
| Now playing | a media session is playing (and for 20 s after a pause) | cover art, title, artist, progress on the rim. Needs notification access. |
| Timer / alarm | a timer or alarm is set | countdown ring on the rim, remaining time at the top, alarm time at the bottom |
| Ringing | a timer or alarm fires | pulsing rings, alarm sound for at most 2 minutes. A tap, or the voice command "Hey Claude, stop" ("Hey ChatGPT, stop"), ends it. |

The **centre cap** shows the assistant state: gray = off, orange with halo = listening for the wake word,
pulsing = conversation active.
Screen brightness follows the room light in four steps. Tapping the clock opens the **apps screen**
(`AppsActivity`, favourites first) when the clock is the home screen, closes the clock when it was opened
as an app, and stops a ringing timer/alarm.

## Build

No Gradle: `build.sh` runs aapt2, javac, d8, zipalign and apksigner directly.

```sh
cd apps/clock
VERSION_CODE=10 VERSION_NAME=1.0.0 bash build.sh
# -> build/spot-clock.apk
```

Requirements:

* **JDK 17 or newer.** Found via `JAVA_HOME`, `/usr/libexec/java_home`, Homebrew (`openjdk@17`) or
  `/usr/lib/jvm/…`. macOS: `brew install openjdk@17`; Debian/Ubuntu: `apt install openjdk-17-jdk`.
* **Android SDK** with any build-tools and a platform `android-30` or newer. Found via `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, `~/Library/Android/sdk` (macOS) or `~/Android/Sdk` (Linux). The newest build-tools
  and the newest platform present are used.
* `zip`.

Environment variables (all optional):

| Variable | Default | Meaning |
|---|---|---|
| `VERSION_CODE` | `10` | integer version code |
| `VERSION_NAME` | `1.0.0` | version name |
| `ESL_KEYSTORE`, `ESL_KEYSTORE_PASS`, `ESL_KEY_ALIAS`, `ESL_KEY_PASS` | unset | release signing key. Used only when **all four** are set (passwords are handed to apksigner through the environment, not the command line). Otherwise the APK is signed with the standard debug key `~/.android/debug.keystore` (alias `androiddebugkey`, password `android`), which is created with `keytool` if it is missing. |

`build.sh` warns when a German string is missing and prints the locales in the APK at the end. Check by
hand with:

```sh
aapt2 dump badging build/spot-clock.apk | grep -E "^(package|application-label|locales)"
aapt2 dump resources build/spot-clock.apk | grep -A2 "string/app_name"
```

From the repo root, `scripts/build-apps.sh` builds both apps into `dist/`, and `./install.sh --from-source`
uses that instead of downloading the release APKs.

**Code rules:** Java 8 syntax only and **no lambdas or method references** (android.jar lacks
`LambdaMetafactory` with this toolchain). Use anonymous classes. Every user-visible text goes into
`res/values/strings.xml` and `res/values-de/strings.xml`.

## Install and set up by hand

`./install.sh clock` does all of this for you. By hand (device reachable via `adb`, e.g. `192.168.x.y:5555`):

```sh
adb install -r build/spot-clock.apk
# "Now playing" face: lets the app read media sessions (Android only grants this via notification access)
adb shell cmd notification allow_listener ai.pocket.spotclock/ai.pocket.spotclock.NowPlayingService
# Clock as home screen
adb shell cmd package set-home-activity ai.pocket.spotclock/.ClockActivity
# Clock as screensaver
adb shell settings put secure screensaver_components ai.pocket.spotclock/ai.pocket.spotclock.ClockDream
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_activate_on_sleep 1
adb shell settings put secure screensaver_activate_on_dock 1
```

Notification access technically allows reading notifications. The app ignores them and only reads title,
artist, cover and position of the active media session (`NowPlayingService`).

## Weather location

There is **no default location**. Until one is set, nothing is fetched and the weather complications show
`—` (the weather description reads "NO LOCATION" / "ORT FEHLT").
Set it with string extras on `ClockActivity`:

```sh
adb shell am start -n ai.pocket.spotclock/.ClockActivity --es lat 52.52 --es lon 13.40 --es place Berlin
```

| Extra | Type | Meaning |
|---|---|---|
| `lat` | string (`--es`) | latitude, −90…90 |
| `lon` | string (`--es`) | longitude, −180…180 |
| `place` | string (`--es`), optional | name, stored for reference only |

Both `lat` and `lon` are needed; invalid values are ignored (see `adb logcat -s SpotClock`). A new location
clears the cached weather. `install.sh` finds the coordinates through the Open-Meteo geocoding API:

```sh
curl 'https://geocoding-api.open-meteo.com/v1/search?name=Berlin&count=1&language=en&format=json'
```

Weather comes from the Open-Meteo forecast API (no key): fetched at most every 15 minutes (retry after a
minute on errors) and cached so the clock has data right after a restart. The request contains only the
coordinates. To forget the location, clear the app data (`adb shell pm clear ai.pocket.spotclock`; this also
clears a set timer/alarm).

## Broadcasts

### Received from Spot Voice: `ai.pocket.heyclaude.STATE`

| Extra | Type | Values |
|---|---|---|
| `state` | string | `off` \| `listening` \| `active` (anything else is drawn like `off`) |
| `assistant` | string, optional | display name of the selected assistant, e.g. `Claude` or `ChatGPT` |

Registered at runtime while a clock face is on screen. When the face appears, the clock sends
`ai.pocket.heyclaude.QUERY_STATE` to `ai.pocket.heyclaude` (if installed) so Spot Voice answers with its
current state. The last `assistant` name is stored (default `Claude`) and used in the ringing notification
("Tap the clock or say “Hey ChatGPT, stop”"); the face itself does not show it.

Test:

```sh
adb shell am broadcast -a ai.pocket.heyclaude.STATE -p ai.pocket.spotclock --es state active --es assistant ChatGPT
```

### Received: timer and alarm (`Timers` receiver)

These go to a manifest receiver, so they must name the package (`-p ai.pocket.spotclock`); Android 8+ does
not deliver implicit broadcasts to manifest receivers. Spot Voice sends them for the offline voice commands.

| Action | Extras | Effect |
|---|---|---|
| `ai.pocket.spotclock.TIMER` | `seconds` (**long**, `--el`) | starts a countdown of that many seconds, replacing a running one. `0`, negative or missing = cancel. |
| `ai.pocket.spotclock.ALARM` | `hour` (int, `--ei`, 0–23), `minute` (int, `--ei`, 0–59, default 0) | sets the alarm to the next occurrence of that time (today or tomorrow), replacing the previous one. `hour -1`, missing or any out-of-range value = cancel. |
| `ai.pocket.spotclock.STOP_RINGING` | none | stops a ringing timer or alarm |

```sh
adb shell am broadcast -a ai.pocket.spotclock.TIMER -p ai.pocket.spotclock --el seconds 600
adb shell am broadcast -a ai.pocket.spotclock.ALARM -p ai.pocket.spotclock --ei hour 6 --ei minute 30
adb shell am broadcast -a ai.pocket.spotclock.ALARM -p ai.pocket.spotclock --ei hour -1
adb shell am broadcast -a ai.pocket.spotclock.STOP_RINGING -p ai.pocket.spotclock
```

Note the types: `--ei seconds 600` is read as a missing long and **cancels** the timer.
One timer and one alarm exist at a time. They fire through `AlarmManager` (exact, allowed while idle), so they
also ring when the clock is not on screen; `RingService` then plays the default alarm sound and brings the
clock to the front. The internal action `ai.pocket.spotclock.FIRE` is the `AlarmManager` callback.

## Languages

All texts live in `res/values/strings.xml` (English, default) and `res/values-de/strings.xml` (German) and
follow the system locale. Weekday and month names come from `Locale.getDefault()`. Times use the
24-hour format in every language (design choice).

To add a language, copy `res/values-de/strings.xml` to `res/values-<code>/strings.xml` and translate it.
Complication labels are upper case and should stay at about 13 characters or fewer.

## Stored data

SharedPreferences file `spotclock` (app-private): `lat`, `lon`, `place`, the last weather response,
`timer_end`/`timer_total`, `alarm_at`, `assistant`, and a flag for the one-time dark wallpaper that is set
when the clock first runs as the home screen.

## Code map

| File | Role |
|---|---|
| `ClockActivity.java` | clock as app / home screen, location extras, brightness |
| `ClockDream.java` | the same face as screensaver (`DreamService`) |
| `ClockView.java` | draws every face; light sensor, STATE receiver |
| `Weather.java` | Open-Meteo fetch, cache, WMO code → label/icon |
| `Moon.java` | moon phase |
| `NowPlayingService.java`, `NowPlaying.java` | media session reader and its state |
| `Timers.java`, `RingService.java` | timer/alarm broadcasts, `AlarmManager`, ringing |
| `AppsActivity.java` | apps screen in the clock's look |
| `res/drawable/` | vector icons (weather, sun, launcher) |

Logs: `adb logcat -s SpotClock`.
