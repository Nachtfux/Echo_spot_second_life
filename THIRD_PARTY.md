# Third-party software, models and services

Echo Spot Second Life's own code (`install.sh`, `scripts/`, `apps/`, `training/`) is MIT-licensed (see `LICENSE`).
It builds on, downloads or talks to the following projects. Thank you to everyone behind them.

## Downloaded or used by `install.sh`

| Project | Used for | Licence | Where it comes from |
|---|---|---|---|
| [amonet](https://github.com/R0rt1z2/amonet) (branch `mt8163-rook`) by R0rt1z2 and contributors, incl. the original amonet authors (xyz`, k4y0z) | bootrom exploit, unlock, TWRP for the Spot | GPL-3.0 (see the amonet repository) | XDA thread "[UNLOCK][ROOT][TWRP][UNBRICK] Amazon Echo Spot 2017 (rook)", downloaded manually by you |
| [TWRP](https://twrp.me) / [amazon-oss twrp_bootable_recovery](https://github.com/amazon-oss/twrp_bootable_recovery) | recovery | GPL-3.0 (TWRP) / Apache-2.0 (AOSP recovery parts) | bundled with amonet |
| [LineageOS 18.1 for rook](https://github.com/amazon-oss/releases/releases/tag/lineage-18.1-rook-v0.3) (amazon-oss, unofficial) | the operating system | Apache-2.0 and others (LineageOS / AOSP) | GitHub release, sha256-verified |
| [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) | installing Claude / ChatGPT / Spotify from Google Play without Google services | GPL-3.0 | F-Droid, sha256- and signature-verified |
| [Vosk](https://alphacephei.com/vosk/) + `vosk-model-small-de-0.15`, `vosk-model-small-en-us-0.15` | offline speech recognition for voice commands | Apache-2.0 | alphacephei.com, sha256-verified |
| [Android SDK platform-tools](https://developer.android.com/tools/releases/platform-tools) | `adb`, `fastboot` | Android SDK licence | Homebrew / apt |

## Inside the apps

| Project | Used for | Licence |
|---|---|---|
| [openWakeWord](https://github.com/dscripka/openWakeWord) by David Scripka | wake-word pipeline; `melspectrogram.onnx`, `embedding_model.onnx` and the fallback model `alexa_v0.1.onnx` are downloaded at build time | code Apache-2.0; pre-trained models **CC BY-NC-SA 4.0** |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | running the wake-word models | MIT |
| [Vosk Android](https://github.com/alphacep/vosk-api) + [JNA](https://github.com/java-native-access/jna) | offline recognition | Apache-2.0 / Apache-2.0 or LGPL-2.1 |

## Trained models in `models/`

`hey_claude.onnx`, `danke_claude.onnx`, `hey_chatgpt.onnx`, `danke_chatgpt.onnx` were trained with openWakeWord's
training code on synthetic speech from [Piper sample generator](https://github.com/rhasspy/piper-sample-generator)
voices (`en_US-libritts_r-medium`, based on LibriTTS-R, CC BY 4.0; `de_DE-mls-medium`, based on Multilingual
LibriSpeech, CC BY 4.0) and openWakeWord's pre-computed negative features (ACAV100M subset, CC BY-NC-SA 4.0).
Because of that, the models are released under **CC BY-NC-SA 4.0** (non-commercial, share alike).

## Online services the apps talk to

| Service | Used for |
|---|---|
| [Open-Meteo](https://open-meteo.com) | weather and geocoding for the clock (free, no key; attribution: weather data by Open-Meteo.com, CC BY 4.0) |
| [radio-browser.info](https://www.radio-browser.info) | finding internet radio stations by name |
| Claude app (Anthropic), ChatGPT app (OpenAI), Spotify | used as they are, with your own account |

## Trademarks

Amazon, Echo and Alexa are trademarks of Amazon.com, Inc. Claude is a trademark of Anthropic, PBC. ChatGPT is a
trademark of OpenAI. Spotify is a trademark of Spotify AB. This project is not affiliated with, endorsed or
sponsored by any of them.
