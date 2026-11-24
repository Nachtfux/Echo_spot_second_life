# Wake-word training

Spot Voice listens with [openWakeWord](https://github.com/dscripka/openWakeWord) models. Each assistant
has two: a **wake word** that starts voice mode and a **stop word** that ends it:

| Set | Wake word | Stop word |
|---|---|---|
| `claude` | `hey_claude.onnx` ("Hey Claude") | `danke_claude.onnx` ("Danke Claude", "Claude stopp") |
| `chatgpt` | `hey_chatgpt.onnx` ("Hey ChatGPT") | `danke_chatgpt.onnx` ("Danke ChatGPT", "ChatGPT stop", "Thanks ChatGPT") |

The trained models live in `../models/`; you only need this folder to retrain them or to train
your own words. Everything runs locally on a Mac. The speech is synthetic: more than 1,100 English and
German [Piper](https://github.com/rhasspy/piper-sample-generator) speakers say the phrase, and the
model learns to tell it apart from about 1,000 hours of other audio (half of openWakeWord's
ACAV100M feature set).

## Requirements

- A Mac with Apple Silicon (tested on macOS 26 with an M-series chip). Linux should work but is untested.
- `git` (Xcode Command Line Tools on macOS), `curl`, `patch` and [uv](https://docs.astral.sh/uv/)
  (`curl -LsSf https://astral.sh/uv/install.sh | sh`). uv installs Python 3.11 by itself.
- **About 20 GB of free disk**: 9 GB of training data, 2 GB of tools, about 4 GB of generated clips per model.
- **About 1.5 to 2.5 hours** for a full run (the `--quick` smoke test takes roughly 30 to 45 minutes).
  About 10 GB is downloaded the first time. The Mac stays awake while training (`caffeinate`).
- The repository path should be short (at most 93 characters for `training/`): espeak-ng, the phonemizer
  Piper uses, fails on longer paths.

## One command

```bash
cd training
./train.sh claude            # or: ./train.sh chatgpt
./train.sh claude --quick    # smoke test: 5000 samples, 10000 steps; these models are not good enough for daily use
```

`train.sh` does everything, and you can stop it and run it again (completed steps are skipped):

1. **Setup** (`setup.sh`, first time only). Creates `.venv/` with the exact tested package versions
   (`constraints.txt`) and clones openWakeWord and piper-sample-generator at pinned commits.
   It applies `patches/`, then downloads the two Piper voices and the openWakeWord feature models.
2. **Data** (in `data/`). Downloads `validation_set_features.npy` (about 11 h of negative audio,
   used to measure false alarms) and two disjoint 4 GB parts of the ACAV100M negative features
   (`fetch_features.py` uses HTTP range requests, so the full 16.5 GB file is never downloaded).
   It also fetches room impulse responses and 4 h of music for augmentation (`prep_data.py`).
3. **Configs** (`make_configs.py`). Writes one openWakeWord config per model to `out/<set>/`.
4. **Training**. Both models train in parallel, logging to `out/<set>/train_<model>.log`.
   First Piper speaks the phrases and the generated sound-alike phrases, then the clips are mixed
   with noise, music and room echo, and finally the classifier trains.
5. **Results**. The models are copied to `out/models/<set>/` and evaluated (`evaluate.py`), and the
   next steps are printed.

Default settings (our "v2" models): 20000 clips per model, 50000 steps, layer size 64, 2 augmentation
rounds, max negative weight 2000. You can override them with `OWW_LAYER`, `OWW_AUG_ROUNDS` and
`OWW_NEG_WEIGHT`. To keep the large data somewhere else, set `OWW_DATA_DIR=/path/to/data`.

## Your own word

A word set is a small YAML file. Start from the commented template:

```bash
cp words/example-custom.yaml words/computer.yaml    # edit it
./train.sh words/computer.yaml --quick              # check that everything works
./train.sh words/computer.yaml
```

| Key | Meaning |
|---|---|
| `name` | Name of the set, used for `out/<name>/` (default: the file name) |
| `models.<model>.target_phrase` | Spellings of the phrase. Phonetic spellings steer the voices: `klohd` makes the German voice say "Claude" the way Germans say it, `chat g p t` makes the English voice spell the letters |
| `models.<model>.custom_negative_phrases` | Phrases that must **not** trigger it: parts of your phrase, similar words, other assistants. Sound-alike phrases are added automatically |
| `german_words` | A phrase that contains one of these words is spoken by the German voice (236 speakers), every other phrase by the English one (904 speakers) |
| `pronunciations` | [ARPAbet](https://en.wikipedia.org/wiki/ARPABET) for words in your phrases that are not in CMUdict (needed for the sound-alike phrases). Without it training stops with `No pronunciation for '<word>'` |

Use lower-case ASCII (`schoen`, not `schön`). One or two models per set. Spot Voice loads
`hey_<assistant>.onnx` and `danke_<assistant>.onnx`. To use your words with the Claude profile, either
name the models `hey_claude`/`danke_claude` in the YAML or rename the files when you deploy them.
If you change the phrases, `make_configs.py` removes the clips that are now outdated. If you change the
sample count, it removes the features. To start over completely, delete `out/<set>/`.

## Evaluate

```bash
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx out/models/claude/danke_claude.onnx
```

openWakeWord's own `Model` class does not work with the pinned versions, so `evaluate.py` runs the
ONNX pipeline itself: `melspectrogram.onnx` → `embedding_model.onnx` → classifier on the last
16 × 96 embeddings, one step per 1280-sample frame at 16 kHz. This is exactly the streaming code that
the app runs on the Spot. A detection counts as a trigger under the app's rule: the score must be
above the threshold on **2 consecutive frames**. After each trigger, 2 s are ignored.

```
hey_claude.onnx  (input 16 x 96; trigger: 2 frames > threshold)
  2000 positive clips from out/claude/hey_claude/positive_test
  2000 negative clips from out/claude/hey_claude/negative_test
  10.7 h of negative features from data/validation_set_features.npy
  threshold          recall       near-miss  false alarms/h
       0.50          ...
```

- **recall**: share of the held-out positive test clips that train.py generated and never trained on.
  These are clean synthetic voices, so the recall is an upper bound. For a harder test, mix in music
  with `--background data/fma --snr 5`. To test your own recordings (16 kHz mono WAV), use `--positive DIR`.
- **near-miss**: share of the sound-alike negative test clips (such as "hey klaus") that trigger.
- **false alarms/h**: triggers per hour on the 10.7 h validation set.

Choose the threshold where the false alarms are low enough for your room, then set it on the device
(`adb shell am broadcast -a ai.pocket.heyclaude.SET_THRESHOLD -p ai.pocket.heyclaude --ef wake 0.7 --ef stop 0.5`).
Options: `--thresholds 0.5 0.6`, `--max-clips 0` (all clips), `--refractory`, `--frames`.

## Deploy

Using the installer (recommended):

```bash
cp out/models/claude/*.onnx ../models/
cd .. && ./install.sh voice
```

Or copy the files yourself. The app looks in its external files folder first, and the assistant
broadcast reloads the models without a reinstall:

```bash
adb push out/models/claude/hey_claude.onnx   /sdcard/Android/data/ai.pocket.heyclaude/files/hey_claude.onnx
adb push out/models/claude/danke_claude.onnx /sdcard/Android/data/ai.pocket.heyclaude/files/danke_claude.onnx
adb shell am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant claude
```

For ChatGPT, use `hey_chatgpt.onnx` / `danke_chatgpt.onnx` and `--es assistant chatgpt`.

## Files

| File | Purpose |
|---|---|
| `train.sh` | the one command (runs everything below) |
| `setup.sh` | creates the environment; idempotent, run it again to repair the environment |
| `constraints.txt` | exact package versions of the tested environment (the reasons for each pin are in `setup.sh`) |
| `patches/` | changes to openWakeWord: no DeepPhonemizer (its download is gone), pronunciations from the word file, macOS DataLoader fix, no accidental TFLite export |
| `piper_shim/` | lets openWakeWord's `train.py` use piper-sample-generator v3 and the English and German voices, writes 16 kHz clips |
| `words/` | word sets: `claude.yaml`, `chatgpt.yaml`, `example-custom.yaml` |
| `make_configs.py`, `fetch_features.py`, `prep_data.py`, `evaluate.py` | individual steps; each one explains its usage in its docstring |

Generated folders (git-ignored): `.venv/`, `openwakeword/`, `piper-sample-generator/`, `data/`, `out/`.

## Troubleshooting

- **`No pronunciation for 'xyz'`**: add `xyz: <ARPAbet>` under `pronunciations:` in your word file.
- **`Error processing file '.../espeak-ng-data/phontab'`**: the folder path is too long, so move the repository.
- **Training stopped** (closed the lid, Ctrl-C, error): run the same command again. Downloads, clips
  and features are reused. The logs are in `out/<set>/train_<model>.log`.
- **Out of disk**: delete `out/<set>/<model>/` (clips) after a successful run. Keep `data/` if you plan to train again.

## Licences

- Code in this folder: MIT (see `../LICENSE`). openWakeWord: Apache 2.0. piper-sample-generator: MIT.
- **Trained models: CC BY-NC-SA 4.0** (non-commercial). The ACAV100M negative features and openWakeWord's
  pretrained feature models (`melspectrogram.onnx`, `embedding_model.onnx`) they are built on carry
  that licence.
- Piper voices: `en_US-libritts_r-medium` is trained on LibriTTS-R, `de_DE-mls-medium` on Multilingual
  LibriSpeech, both CC BY 4.0.
- Augmentation data (used for training only, not distributed): MIT environmental impulse responses and the
  Free Music Archive (`fma_small`, Creative Commons licences per track).
