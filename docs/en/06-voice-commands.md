<sub>**English** · [Deutsch](../de/06-voice-commands.md)</sub>

# 6 · Voice commands

> **Goal:** Control music, internet radio, timers and alarms by voice, recognised offline on the Spot, without opening the assistant. · **Time:** 5 minutes to read · **Installer:** `./install.sh voice`

Not every request needs Claude or ChatGPT. "Set a timer for ten minutes" or "play radio BBC one" should just happen, quickly and without the cloud. That is what the offline voice commands are for. The speech recognition runs on the Spot itself with a small [Vosk](https://alphacephei.com/vosk/) model, in English or German.

---

## How a command works

1. Say the wake word, for example "Hey Claude".
2. You hear a short **beep**. The Spot pauses its own music, if any is playing, and the clock's centre starts to pulse.
3. Say your command within about **2 seconds**, for example "set a timer for five minutes". The Spot listens until you stop talking, for at most about 7 seconds in total.
4. Vosk turns what you said into text **on the Spot**, and the command parser checks it against the tables below.

What happens next depends on what the Spot heard:

| The Spot heard | Result |
|---|---|
| A command | It runs and a short confirmation tone plays. For "play …" and "radio …" the music starting is the confirmation (a low error tone if that fails). The assistant does **not** open. |
| Nothing within 2 seconds | The assistant opens in voice mode (chapter 5). |
| One or two words that are no command | The assistant opens. The words themselves are not passed on. |
| More than two words that are no command | Treated as a **false alarm**: a real wake word is followed by a pause, so ongoing talk means the wake word fired on room conversation. A low error tone plays, nothing opens, and music that was playing continues. |

Music after a command: after a volume, "next" or "previous" command the music continues by itself. After other commands (for example a timer) it stays paused; say the wake word and "resume" to continue it.

> [!TIP]
> Wait for the beep, then say the command in one go. Use the phrases from the tables below. The parser understands many variants, but it does not understand free sentences.

> [!NOTE]
> Without a Vosk model on the Spot, voice commands are off. The wake word then opens the assistant right away, without a beep.

---

## Command language

Voice commands work in **English** or **German**, one at a time. The default follows the Spot's system language. The command language is independent of the assistant: Claude can talk to you in any language, but the offline commands need the language you chose here.

Each language needs its own Vosk model on the Spot (`vosk-en` or `vosk-de`). The installer copies only the model of your language. To switch:

| Way | Command or step | Notes |
|---|---|---|
| Installer (recommended) | `./install.sh voice --lang en` | Downloads and copies the English model, then switches. `--lang` is saved and also sets the installer's language and, the next time `setup` runs, the Spot's system language. |
| Spot Voice app | **Command language** → Deutsch / English | Only works if the model of that language is already on the Spot. The status line `Voice commands (vosk-en): yes` tells you. |
| Broadcast | `adb shell am broadcast -a ai.pocket.heyclaude.SET_LANG -p ai.pocket.heyclaude --es lang en` | Same as the app picker. |

---

## English commands

Words like "please", "can you", "could you", "just", "for me", "on Spotify" and a leading "hey Claude" or "ok ChatGPT" are ignored, so "can you play Queen on Spotify please" works like "play Queen". Numbers can be words or digits.

### Music

| You say | What happens |
|---|---|
| "play Queen", "play Queen on Spotify", "play some jazz", "play songs by Adele", "play the album Thriller", "put on …", "listen to …" | A radio station with exactly that name, if one exists (see [Internet radio](#internet-radio-how-stations-are-found)); otherwise a Spotify search. |
| "pause", "stop", "stop the music", "pause the music", "be quiet", "stop playing" | Pause (any player). Also stops a ringing timer or alarm. |
| "resume", "continue", "play", "play some music", "keep playing", "unpause", "music on" | Play / continue. |
| "next", "skip", "next song", "skip this song", "play the next song" | Next track. |
| "previous", "go back", "previous song", "last song", "play the previous song" | Previous track. |
| "louder", "volume up", "turn it up", "increase the volume" | Media volume up by 2 steps. |
| "much louder", "a lot louder" | Up by 4 steps. |
| "quieter", "softer", "volume down", "turn it down", "lower the volume" | Down by 2 steps. |
| "much quieter" | Down by 4 steps. |

### Radio

| You say | What happens |
|---|---|
| "radio BBC one", "play radio KEXP", "turn on radio Antenne Bayern", "turn on the radio station KEXP", "start radio …", "listen to radio …" | Searches the station and plays it. |
| "radio off", "turn off the radio", "stop the radio", "turn the radio off" | Stops the radio. |

### Timer

| You say | Timer |
|---|---|
| "set a timer for 5 minutes", "set a timer for five minutes" | 5 min |
| "timer ten minutes", "timer 10 minutes", "ten minutes" | 10 min |
| "timer twenty five minutes" | 25 min |
| "timer for 90 seconds" | 1 min 30 s |
| "set a timer for half an hour", "half an hour" | 30 min |
| "a quarter of an hour", "three quarters of an hour" | 15 min, 45 min |
| "timer for one and a half hours", "set a timer for an hour and a half" | 1 h 30 min |
| "1 hour 30 minutes" | 1 h 30 min |
| "wake me up in twenty minutes" | 20 min (a timer, because of "in") |
| "cancel the timer", "stop the timer", "timer off", "delete the timer" | Cancels the running timer. |

Units: hours, minutes and seconds (also "hrs", "mins", "secs"). The longest timer is 24 hours.

### Alarm

| You say | Alarm |
|---|---|
| "wake me up at 7", "wake me up at seven", "wake me up at seven in the morning" | 07:00 |
| "set an alarm for 6:30", "set an alarm for 6.30", "alarm at six thirty" | 06:30 |
| "alarm at seven oh five" | 07:05 |
| "set an alarm for half past seven" | 07:30 |
| "alarm at quarter past eight" | 08:15 |
| "wake me at quarter to eight" | 07:45 |
| "wake me up at ten past six", "alarm at twenty to seven" | 06:10, 06:40 |
| "alarm at 6 am", "wake me up at 6 a.m." | 06:00 |
| "alarm at 6 pm", "set an alarm for 7pm", "set an alarm for six thirty p m", "alarm at seven in the evening" | 18:00, 19:00, 18:30, 19:00 |
| "alarm at noon", "alarm at midnight" | 12:00, 00:00 |
| "cancel the alarm", "turn off the alarm", "alarm off", "don't wake me" | Cancels the alarm. |

Without "am", "pm", "in the morning" or "in the evening" the hour counts as written in 24-hour time: "alarm at seven" is 07:00, "alarm at nineteen" is 19:00. The alarm rings at the next time that clock time comes round, today or tomorrow. It rings once and does not repeat.

---

## German commands (Deutsch)

Set the command language to German first. Filler words such as "bitte", "mal", "doch", "auf Spotify" and the assistant's name are ignored. Numbers can be words or digits; "fünf und zwanzig" and "fünfundzwanzig" both work.

### Music

| You say (German) | What happens |
|---|---|
| „spiel Queen", „spiel Queen auf Spotify", „spiele Musik von Queen", „spiel bitte die Beatles", „spiel das Album Thriller" | A radio station with exactly that name, if one exists; otherwise a Spotify search. |
| „Pause", „stopp", „stop", „anhalten", „halt", „Ruhe", „aus", „Musik aus" | Pause. Also stops a ringing timer or alarm. |
| „weiter", „weiterspielen", „fortsetzen", „Musik", „Musik an", „spiel Musik", „spiel weiter" | Play / continue. |
| „nächstes Lied", „nächster Song", „überspringen", „skip" | Next track. |
| „vorheriges Lied", „zurück", „nochmal", „das letzte Lied" | Previous track. |
| „lauter" / „viel lauter" | Volume up by 2 / 4 steps. |
| „leiser" / „viel leiser" | Volume down by 2 / 4 steps. |

### Radio

| You say (German) | What happens |
|---|---|
| „Radio SWR drei", „spiel Radio Bayern drei", „Sender Eins Live", „schalte das Radio an Antenne Bayern", „mach Radio …", „starte Radio …" | Searches the station and plays it. |
| „Radio aus", „Radio stopp", „das Radio aus" | Stops the radio. |

### Timer

| You say (German) | Timer |
|---|---|
| „Timer zehn Minuten", „zehn Minuten" | 10 min |
| „stell einen Timer auf fünf Minuten" | 5 min |
| „Timer fünfundzwanzig Minuten" | 25 min |
| „Timer neunzig Sekunden" | 1 min 30 s |
| „Timer halbe Stunde", „Timer Viertelstunde" | 30 min, 15 min |
| „Timer anderthalb Stunden" | 1 h 30 min |
| „Timer eine Stunde und fünf Minuten" | 1 h 5 min |
| „in zwei Stunden" | 2 h |
| „Timer aus", „Timer abbrechen", „Timer stopp", „lösche den Timer", „stoppe den Timer" | Cancels the running timer. |

The parser also accepts the words „Countdown", „Stoppuhr" and „Kurzzeitwecker" (and the common mis-hearings „Teimer", „Taimer"). The longest timer is 24 hours.

### Alarm (Wecker)

| You say (German) | Alarm |
|---|---|
| „Wecker um sieben Uhr", „Wecker um 7 Uhr", „Wecker um sieben" | 07:00 |
| „Wecker um sieben Uhr dreißig" | 07:30 |
| „weck mich um halb sieben" | 06:30 |
| „Wecker Viertel nach acht" | 08:15 |
| „Wecker Viertel vor acht" | 07:45 |
| „Wecker auf sechs" | 06:00 |
| „Wecker um neunzehn Uhr" | 19:00 |
| „Wecker aus", „Wecker ausschalten", „Wecker abbrechen", „lösche den Wecker" | Cancels the alarm. |

Note the German convention: „halb sieben" is **6:30** (half *to* seven), while English "half past seven" is 7:30. German times are 24-hour: for the evening say „neunzehn Uhr".

---

## Internet radio: how stations are found

Stations come from [radio-browser.info](https://www.radio-browser.info/), a free, open directory of internet radio stations (no account, no key). Only the spoken station name is sent there.

1. **Spoken words become a search text.** Number words become digits, and single spoken letters are joined:

   | You say | Search text |
   |---|---|
   | "s w r drei" | `swr3` |
   | "Bayern drei" | `bayern 3` |
   | "Eins Live" | `1 live` |
   | "b b c radio one" | `bbc radio 1` |
   | "k e x p" | `kexp` |

   The Spot searches with and without spaces (`swr 3` and `swr3`).
2. **Country first.** German commands search German stations first. English commands search the country of the Spot's language setting first (for `en-US`: the USA). If nothing is found there, it searches worldwide.
3. **Picking the station.**
   - "**radio** …" (or „Radio …"): a station whose name matches exactly, ignoring case, spaces and punctuation. Otherwise the most popular station whose name starts with your words. Otherwise the most popular search result. If there is nothing at all, a low error tone plays.
   - "**play** …" (or „spiel …"): a station only if its name matches **exactly** and at least **20** radio-browser.info users voted for it. Otherwise the request goes to Spotify. So „spiel SWR3" can play the well-known station, while "play Queen" goes to Spotify. When you want the radio, say "radio …".

The radio plays inside Spot Voice. "pause", "resume" and the volume commands work as for any player. With notification access (chapter 7), the clock shows the station name and logo. Starting a Spotify search stops the radio.

> [!TIP]
> Abbreviations are recognised best letter by letter: "s w r drei", "b b c radio one", "n d r zwei". If you want a specific station, start with "radio": that search is more forgiving than "play".

---

## Spotify

Requirements:

1. Spotify is installed. The installer asks during `./install.sh apps`. To add it later:

   ```bash
   ./install.sh apps --spotify
   ```

2. You have opened Spotify on the Spot once and **logged in** there (tap the clock, then Spotify).

"Play …" sends your words to the Spotify app as a search ("play from search"). Spotify comes to the front to start playback, and after about 6 seconds the Spot shows the clock again. What exactly Spotify plays for a search is decided by the Spotify app; this project has no influence on it. Pause, resume, next, previous and volume work through the normal media controls, for Spotify as well as for the radio.

If Spotify is not installed or the search fails, a low error tone plays.

---

## Timers and alarms on the clock

Timers and alarms live in Spot Clock. As soon as one is set, the clock shows it:

<p align="center">
  <img src="../images/shot-timer.png" alt="Clock face with an orange timer arc along the rim, 4:53 remaining at the top and the alarm time WECKER 06:30 at the bottom (German UI)" width="280">
</p>
<p align="center"><sub>A timer with 4:53 left (arc along the rim, time at the top) and an alarm at 06:30 (bottom). German UI: "WECKER" = "ALARM".</sub></p>

- **One timer and one alarm** at a time. A new timer replaces the old one, and the same goes for the alarm.
- They work even when the clock is not on screen, for example during a conversation, because Android's alarm service wakes the Spot.
- When a timer or alarm goes off, the clock comes to the front with **pulsing orange rings**, the word **TIMER** or **ALARM** and **"Tap to stop"**. The Spot's alarm sound plays in a loop for at most **2 minutes**.

**Stopping a ringing timer or alarm:**

- **Tap the screen**, or
- say the wake word and **"stop"** (English) or **„stopp"** (German), for example "Hey Claude … stop".

"Timer off" or „Timer aus" cancels a timer that is still counting down. To silence one that is already ringing, use "stop" or tap the screen.

Chapter 7 explains the [timer arc and alarm line](07-clock.md#timer-and-alarm-on-the-face) and the adb broadcasts for timers and alarms.

---

## Tips for good recognition

- **Wait for the beep**, then speak. Words said before the beep can be cut off.
- **Use the phrases from the tables.** The parser understands many variants, but not free sentences.
- **Speak towards the Spot** at a normal volume. The Spot pauses its own music while it listens, but a loud TV next to it still makes recognition harder.
- **Spell abbreviations** letter by letter (radio stations).
- The Vosk models are small and fast, but their vocabulary is limited. Unusual artist names can be misheard. Check what the Spot heard in the log:

  ```bash
  adb logcat -s HeyClaude
  ```

  ```text
  Command heard: 'set a timer for five minutes'
  Voice command 'set a timer for five minutes' -> Timer(300s)
  ```

- **Test without speaking:** the `COMMAND` broadcast runs a text as if it had been spoken:

  ```bash
  adb shell am broadcast -a ai.pocket.heyclaude.COMMAND -p ai.pocket.heyclaude --es text "set a timer for five minutes"
  # with German as the command language:
  adb shell am broadcast -a ai.pocket.heyclaude.COMMAND -p ai.pocket.heyclaude --es text "timer fünf minuten"
  ```

  The text is parsed in the current command language, and the command really runs (the timer above starts). The log shows the result, for example `Test command 'set a timer for five minutes' (en) -> Timer(300s)`. Cancel the test timer with `--es text "cancel the timer"`.

---

## What the installer does for you

The voice commands are set up by the `voice` phase (chapter 5). For commands it:

1. Downloads the small Vosk model of your language: `vosk-model-small-en-us-0.15` or `vosk-model-small-de-0.15`.
2. Checks its SHA-256 checksum, unpacks it and copies the folder to the Spot as `vosk-en` or `vosk-de` (about 50 MB). If the model is already there, it skips this step; `--force` copies it again.
3. Sends `SET_LANG` so Spot Voice loads the model.

Spotify is installed by the `apps` phase if you said yes (or with `--spotify`). The dry-run shows the Vosk steps (`…` stands for the project folder, which the installer prints in full):

```text
==> Offline speech model for voice commands (Vosk, en)
[..] Downloading vosk-model-small-en-us-0.15.zip ...
+ curl -fL --retry 3 -o downloads/vosk-model-small-en-us-0.15.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
+ shasum -a 256 downloads/vosk-model-small-en-us-0.15.zip
+ unzip -q -o …/downloads/vosk-model-small-en-us-0.15.zip -d …/downloads
[..] Copying the model to the Spot (about 50 MB) ...
+ adb shell rm -rf /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-en
+ adb push …/downloads/vosk-model-small-en-us-0.15 /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-en
[ok] downloads/vosk-model-small-en-us-0.15/ -> /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-en/
```

---

## Check

1. Run the checks:

   ```bash
   ./install.sh verify
   ```

   The line `Vosk speech model on the Spot (vosk-en)` (or `vosk-de`) must show ✓.
2. In the Spot Voice app the status line reads `Voice commands (vosk-en): yes`.
3. Say "Hey Claude", wait for the beep, and say "set a timer for one minute". You hear a confirmation tone, and an orange arc appears on the clock's rim. After a minute it rings; tap the screen to stop it.
4. Without speaking, use the `COMMAND` broadcast from the tips above and read the log.

---

## If something goes wrong

| Symptom | See |
|---|---|
| No beep after the wake word, or commands are never recognised | [Voice commands are not recognised](09-troubleshooting.md#voice-commands-are-not-recognised) |
| A station is not found or the wrong one plays | [Radio station not found](09-troubleshooting.md#radio-station-not-found) |
| "Play …" does nothing or Spotify does not start | [Spotify does not play](09-troubleshooting.md#spotify-does-not-play) |
| The wake word itself does not react | [The wake word does not react](09-troubleshooting.md#the-wake-word-does-not-react) |

---
[← 5 · Voice assistant: Claude or ChatGPT](05-voice-assistant.md) · [Overview](../../README.md) · [7 · The clock →](07-clock.md)
