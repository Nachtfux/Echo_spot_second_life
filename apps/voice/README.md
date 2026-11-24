# Spot Voice (Android app)

Wake-word companion app for an Echo Spot (1st gen) running LineageOS 18.1 (Android 11).
It listens on the device with [openWakeWord](https://github.com/dscripka/openWakeWord) (ONNX Runtime),
wakes the screen, opens **Claude** or **ChatGPT** and taps its voice-mode button through an
accessibility service. Short offline voice commands (Spotify, radio, timer, alarm) are recognised with
[Vosk](https://alphacephei.com/vosk/) in German or English.

* Package `ai.pocket.heyclaude`, launcher label "Spot Voice", minSdk 26, targetSdk 30, `armeabi-v7a` only.
* End users don't need this file: `./install.sh` in the repo root builds or downloads the APK and sets
  everything up (phase `voice`). See [`docs/en/05-voice-assistant.md`](../../docs/en/05-voice-assistant.md)
  and [`docs/en/06-voice-commands.md`](../../docs/en/06-voice-commands.md).

## Build

Needs JDK 17 and the Android SDK (platform 34, build-tools). Set `ANDROID_HOME` or create
`local.properties` with `sdk.dir=…`.

```bash
./gradlew testDebugUnitTest assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

* The first build downloads the openWakeWord base models (`melspectrogram.onnx`, `embedding_model.onnx`,
  `hey_jarvis_v0.1.onnx`, `alexa_v0.1.onnx`) into `app/src/main/assets/models/`. They are
  CC BY-NC-SA 4.0 and therefore not committed.
* Release signing: set `ESL_KEYSTORE`, `ESL_KEYSTORE_PASS`, `ESL_KEY_ALIAS` and `ESL_KEY_PASS` to sign
  with your own key. Without all four the release APK is signed with the debug key (fine for sideloading).
* Unit tests (`app/src/test`, plain JVM): German/English command parsers, duration and clock parsing,
  assistant label regexes, openWakeWord windowing.

## Model files on the device

Everything lives in `/sdcard/Android/data/ai.pocket.heyclaude/files/`:

| File / folder | Purpose |
|---|---|
| `hey_claude.onnx`, `hey_chatgpt.onnx` | wake word of the assistant (`hey_<id>.onnx`). Missing → pre-trained "Alexa". |
| `danke_claude.onnx`, `danke_chatgpt.onnx` | optional stop word that ends a conversation (`danke_<id>.onnx`). |
| `vosk-de/`, `vosk-en/` | unpacked Vosk model of the command language (`vosk-<lang>/am/…`). Missing → no voice commands. |

Wake/stop models are also looked up in `assets/models/`. After pushing new files, send `SET_ASSISTANT`
(even with the same id) to reload them; no reinstall needed.

## Assistants

`Assistant.kt` holds one profile per assistant (`claude` → `com.anthropic.claude`,
`chatgpt` → `com.openai.chatgpt`): voice-button and dictation-exclusion regexes, mic mute/unmute, end
button, end notification action, the name used in spoken stop phrases and the model file names. The
Claude labels were calibrated on a real Spot. The ChatGPT labels are best guesses: check them with
`DUMP_NODES` and adjust the profile.

For quick experiments on a rooted device the prefs (`shared_prefs/hey_claude.xml`) can override
`<id>_package`, `<id>_voice_button_pattern` and `<id>_new_chat_pattern`. Claude also still honours the older
`voice_button_pattern` and `new_chat_pattern` keys.

## Debug broadcasts

All of them: `adb shell am broadcast -p ai.pocket.heyclaude -a ai.pocket.heyclaude.<ACTION> …`

| Action | Extras | Effect |
|---|---|---|
| `SET_ASSISTANT` | `--es assistant claude\|chatgpt` | switch assistant, reload wake + stop models |
| `SET_LANG` | `--es lang de\|en` | switch command language, load `vosk-<lang>` |
| `SET_THRESHOLD` | `--ef wake 0.7 --ef stop 0.5` | detection thresholds |
| `COMMAND` | `--es text "play queen"` | parse + run a voice command as if spoken |
| `RESUME` | – | listen for the wake word again |
| `QUERY_STATE` | – | resend `STATE` to the clock |
| `MIC_MUTE` | `--ez on true\|false` | press the assistant's mic mute button (EchoGuard test) |
| `LEVEL_LOG` | `--ez on true\|false` | log mic and speaker levels |
| `DUMP_NODES` | – | dump the accessibility tree of all windows to logcat |

`SET_ASSISTANT` and `SET_LANG` are also stored while the service is stopped (`SettingsReceiver`).

Trigger a session without speaking:
`adb shell am start-foreground-service -n ai.pocket.heyclaude/.WakeWordService -a ai.pocket.heyclaude.TRIGGER_NOW`

To the clock (`ai.pocket.spotclock`) the app sends `ai.pocket.heyclaude.STATE` with
`--es state off|listening|active` and `--es assistant Claude|ChatGPT`.

## Logs

```bash
adb logcat -s HeyClaude OpenWakeWord
```

`HeyClaude` is the app's tag. `A11y: voice-mode button tapped` means the auto-tap worked.

## Code map

| Path | What |
|---|---|
| `WakeWordService.kt` | foreground service: mic → SilenceGate → openWakeWord → command or assistant launch |
| `Assistant.kt` | assistant profiles (all assistant-specific labels and regexes) |
| `EchoGuard.kt` | half-duplex echo guard, ends conversations (10 s silence, stop phrase/word, wake word) |
| `launch/` | `AssistantLauncher` (opens the app), `VoiceModeA11yService` (taps buttons, notices leaving) |
| `wake/` | openWakeWord port (mel → embedding → classifier) |
| `command/` | Vosk recognition, `GermanParser` / `EnglishParser` → `Action`, radio player |
| `MainActivity.kt` | status and setup screen (assistant and language switch, threshold, mic test) |
