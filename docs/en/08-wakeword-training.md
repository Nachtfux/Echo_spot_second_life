<sub>**English** · [Deutsch](../de/08-wakeword-training.md)</sub>

# 8 · Train your own wake word

> **Goal:** train a wake word and a stop word on your computer, measure how well they work and put them on the Spot · **Time:** 1.5–2.5 h of computer time for a full run (30–45 min for a smoke test), about 15 min of your own · **Installer:** `./install.sh train <set>`

The repository already contains trained models for "Hey Claude" and "Danke Claude" in `models/`.
You do **not** need this chapter to use the Spot. Come here when you want a different word, when you use
ChatGPT and need its models, or when the wake word does not fit your home.

Everything in this chapter runs on your computer. The Spot is only needed at the very end, to install
the result.

## Why train your own word

| Situation | What training gives you |
|---|---|
| You use ChatGPT and `models/` has no `hey_chatgpt.onnx` yet | Without that file Spot Voice listens for its built-in fallback word **"Alexa"**. `./install.sh train chatgpt` trains "Hey ChatGPT" and "Danke ChatGPT" from the word set in the repository. |
| You want another word, for example "Hey Computer" | A word set of your own, in a small text file. |
| The Spot wakes up on something in your home (a name, a TV show) | Add that phrase as a *negative* and train again. See also [The Spot wakes up by itself](09-troubleshooting.md#the-spot-wakes-up-by-itself). |
| The word is not recognised well the way you say it | Add spellings that sound like your pronunciation. |

> [!NOTE]
> The training uses **synthetic voices only**. More than 1,100 English and German [Piper](https://github.com/rhasspy/piper-sample-generator)
> speakers say your phrase, and the model learns to tell it apart from about 1,000 hours of other audio.
> Recordings of your own voice are not used for training. You can use them to **test** a model, see
> [Evaluate a model](#evaluate-a-model).

## What you need

| | |
|---|---|
| Computer | A Mac with Apple Silicon (tested on macOS 26). Linux should work but is untested. Windows is not supported. |
| Free disk space | About **20 GB**: 9 GB of training data, 2 GB of tools, about 4 GB of generated clips per model. |
| Download | About 10 GB the first time. Later runs reuse it. |
| Time | A full run takes **1.5 to 2.5 hours**. The `--quick` smoke test takes 30 to 45 minutes. |
| Tools | `git`, `curl`, `patch` and [uv](https://docs.astral.sh/uv/). uv installs the right Python (3.11) by itself. |
| Folder path | The path of the `training/` folder may have at most 93 characters (a limit of espeak-ng, which the voices use). |
| The Spot | Not needed for training. Only for the last step, [Deploy](#deploy-the-model-to-the-spot). |

Install the tools once:

1. On a Mac, install the Command Line Tools (they contain `git` and `patch`). Skip this if `git --version` already works.

   ```bash
   xcode-select --install
   ```

2. Install uv:

   ```bash
   curl -LsSf https://astral.sh/uv/install.sh | sh
   ```

3. Open a **new** terminal window, so that `uv` is found.

> [!TIP]
> Keep the Mac on its power supply and the lid open. The training script keeps the Mac awake with
> `caffeinate`, but closing the lid can still stop it. If that happens, just run the same command again.

## Train with one command

You run everything from the repository folder. `./install.sh train` passes all its arguments to
`training/train.sh`, so `./install.sh train claude` is the same as `cd training && ./train.sh claude`.

| Command | Word file | Result in `training/out/models/<set>/` |
|---|---|---|
| `./install.sh train claude` | `training/words/claude.yaml` | `hey_claude.onnx` + `danke_claude.onnx` |
| `./install.sh train chatgpt` | `training/words/chatgpt.yaml` | `hey_chatgpt.onnx` + `danke_chatgpt.onnx` |
| `./install.sh train training/words/computer.yaml` | your own file | the model names from your file |
| add `--quick` to any of them | | 5,000 samples and 10,000 steps instead of 20,000 and 50,000. Only proves that everything works; these models are not good enough for daily use. |

The path to your own word file is relative to the folder you are in.

1. Open a terminal in the repository folder.
2. Optional, but a good idea the first time: run the smoke test.

   ```bash
   ./install.sh train claude --quick
   ```

3. Start the real training:

   ```bash
   ./install.sh train claude
   ```

4. Wait. The script prints a status line whenever a model starts a new step (see below).
5. At the end the script prints where the models are, a table of measurements and the next steps.

A dry run shows what would be started, without starting it:

```
$ ./install.sh --dry-run train claude --quick
+ bash training/train.sh claude --quick
```

### What you see while it runs

The script works in five steps. The output looks like this (times and paths are examples):

```
== 1/5 Setup
== 2/5 Training data in …/training/data
== 3/5 Configs for set 'claude' (20000 samples, 50000 steps)
== 4/5 Training hey_claude danke_claude in parallel
hey_claude: log in out/claude/train_hey_claude.log
danke_claude: log in out/claude/train_danke_claude.log
14:05  hey_claude: --generate_clips  danke_claude: --generate_clips
14:41  hey_claude: --augment_clips  danke_claude: --generate_clips
…
15:52  hey_claude: done  danke_claude: done
== 5/5 Results
out/models/claude/hey_claude.onnx
out/models/claude/danke_claude.onnx
```

| Step | What happens |
|---|---|
| 1/5 Setup | First time only (`setup.sh`): creates `training/.venv/` with the exact tested package versions (`constraints.txt`), clones openWakeWord and piper-sample-generator at pinned commits, applies the patches in `training/patches/`, downloads the two Piper voices and the openWakeWord feature models. Every download is checked with sha256. |
| 2/5 Training data | Downloads about 11 hours of negative audio features (used to count false alarms), two 4 GB parts of the ACAV100M negative features, room impulse responses and 4 hours of music. Checks free disk space first. |
| 3/5 Configs | Writes one openWakeWord config per model to `training/out/<set>/`. |
| 4/5 Training | Both models train at the same time. For each: `--generate_clips` (Piper speaks the phrases and sound-alike phrases), `--augment_clips` (mixes in noise, music and room echo), `--train_model` (trains the small classifier). |
| 5/5 Results | Copies the models to `training/out/models/<set>/`, runs `evaluate.py` and prints the next steps. |

### Stop and continue

You can stop with **Ctrl+C** at any time. Run the same command again to continue: finished downloads, clips and
features are reused. The final classifier training (about 10 minutes) always runs again.
The full logs are in `training/out/<set>/train_<model>.log`.

### Settings for experts

| Variable | Default | Meaning |
|---|---|---|
| `OWW_DATA_DIR` | `training/data` | Where the 9 GB of training data live. Can be shared between word sets, for example on an external disk. |
| `OWW_LAYER` | `64` | Size of the classifier layer. |
| `OWW_AUG_ROUNDS` | `2` | How often each clip is mixed with noise, music and echo. |
| `OWW_NEG_WEIGHT` | `2000` | Maximum weight of the negative examples (higher = fewer false alarms, lower recall). |

Example: `OWW_DATA_DIR=/Volumes/Data/oww ./install.sh train claude`.

## How a word file works

A word set is a small YAML file in `training/words/`. The repository has three:
`claude.yaml`, `chatgpt.yaml` and the commented template `example-custom.yaml`.

1. Copy the template:

   ```bash
   cp training/words/example-custom.yaml training/words/computer.yaml
   ```

2. Open `training/words/computer.yaml` in a text editor and change the phrases (details below).
3. Check that everything works with a smoke test:

   ```bash
   ./install.sh train training/words/computer.yaml --quick
   ```

4. Train for real:

   ```bash
   ./install.sh train training/words/computer.yaml
   ```

This is the template, shortened:

```yaml
name: computer                  # output goes to out/computer/ and out/models/computer/

models:
  hey_computer:                 # wake word -> hey_computer.onnx
    target_phrase:
      - hey computer
      - hey, computer
      - hey computer!
      - hey kompjuter           # spoken by the German voice (see german_words)
    custom_negative_phrases:
      - hey
      - computer
      - hey commuter
      - hey compute
      - hey peter
      - okay google
      - alexa
      - hey siri
  danke_computer:               # stop word -> danke_computer.onnx
    target_phrase:
      - danke kompjuter
      - danke, kompjuter
      - computer stop
      - thank you computer
    custom_negative_phrases:
      - danke
      - danke schoen
      - stopp
      - thank you
      - stop
      - hey computer

german_words: [danke, schoen, dir, stopp, hallo, tschuess, nein, bitte, kompjuter]

pronunciations:
  kompjuter: K AH0 M P Y UW1 T ER0
  danke: D AA1 NG K AH0
```

| Key | Meaning |
|---|---|
| `name` | Name of the set. Used for `training/out/<name>/` and `training/out/models/<name>/`. Optional, the default is the file name. Only letters, digits, `_` and `-`. |
| `models` | One or two models. Each name becomes a file name (`hey_computer` → `hey_computer.onnx`). Only letters, digits, `_` and `-`. |
| `models.<model>.target_phrase` | The ways the phrase can be written. Every spelling is spoken by many voices. |
| `models.<model>.custom_negative_phrases` | Phrases that must **not** trigger the model. |
| `german_words` | A phrase that contains one of these words is spoken by the German voice (236 speakers). All other phrases are spoken by the English voice (904 speakers). Leave it out to use the built-in list. |
| `pronunciations` | How to pronounce words that the English pronunciation dictionary (CMUdict) does not know. Also overrides CMUdict. |

### Spellings and phonetic tricks

The voices read what you write. If a voice pronounces your word wrongly, write it the way it should sound:

| Spelling | Effect |
|---|---|
| `hey klohd` | The German voice says "Claude" the way German speakers say it (`klohd` is in `german_words`). |
| `hey claude`, `hey clode` | The English voice says it the English way. |
| `hey tschett dschi pi ti` | German pronunciation of "ChatGPT", read by the German voice. |
| `hey chat g p t`, `hey chat gee pee tee` | The English voice spells the letters. |
| `hey, computer!` | Punctuation changes rhythm and intonation. |

Rules:

- Use **lower-case ASCII only**. Umlauts and digits are dropped by the pronunciation lookup, so write
  `schoen`, `tschuess` and `g p t` instead of `schön`, `tschüss` and `GPT`.
- Several spellings of the same phrase are better than one.

### Negative phrases

List what the model should ignore:

- the parts of your phrase on their own (`hey`, `computer`),
- similar-sounding words (`hey commuter`, `hey peter`),
- other assistants' wake words (`alexa`, `okay google`, `hey siri`),
- names or words that are said often in your home and sound similar.

Sound-alike phrases are also generated automatically from the pronunciation of your target phrase, so
you do not have to think of everything.

### Pronunciations for unknown words

The automatic sound-alikes need the pronunciation of every word in your target phrases. Words that are not
in CMUdict (like `klohd`, `tschett` or `kompjuter`) need an entry in [ARPAbet](https://en.wikipedia.org/wiki/ARPABET)
notation. Without it, the training stops with:

```
No pronunciation for 'kompjuter': add it under 'pronunciations:' in the word file (ARPAbet, e.g. 'klohd: K L OW1 D')
```

Look at `claude.yaml` and `chatgpt.yaml` for more examples (`klohd: K L OW1 D`, `tschett: CH EH1 T`, `dschi: JH IY1`).

### Name the models for Spot Voice

Spot Voice loads exactly two files for the active assistant: `hey_<assistant>.onnx` (wake word) and
`danke_<assistant>.onnx` (stop word), where `<assistant>` is `claude` or `chatgpt`. To use "Hey Computer"
with the Claude profile, either call the models `hey_claude` and `danke_claude` in your word file, or
rename the files when you [deploy](#deploy-the-model-to-the-spot) them.

> [!NOTE]
> The stop word model is optional. Without it, saying the wake word again during a conversation ends it.
> Spoken goodbyes such as "Danke Claude" or "Thank you Claude" are also recognised in the assistant's
> on-screen transcript, independent of the model (see [chapter 5](05-voice-assistant.md)).

If you change the phrases, the outdated clips are removed automatically on the next run. If you change the
sample count, the features are removed. To start a set completely from scratch, delete `training/out/<set>/`.

## Evaluate a model

`train.sh` evaluates the new models at the end. You can run the evaluation again at any time, for example
with other thresholds. It needs the environment and the data that `train.sh` has set up (one run, even with
`--quick`, is enough).

```bash
cd training
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx out/models/claude/danke_claude.onnx
```

`evaluate.py` runs the models exactly the way the app does on the Spot: melspectrogram, then embedding,
then the classifier on the last 16 embeddings, one step per 80 ms of audio. The output has one row per
threshold:

```
hey_claude.onnx  (input 16 x 96; trigger: 2 frames > threshold)
  2000 positive clips from out/claude/hey_claude/positive_test
  2000 negative clips from out/claude/hey_claude/negative_test
  10.7 h of negative features from data/validation_set_features.npy
  threshold          recall       near-miss  false alarms/h
       0.30          …
```

| Column | What it means |
|---|---|
| **recall** | Share of the test clips with your phrase that wake the model. These clips were generated during training but never used to train. They are clean synthetic voices, so read recall as an **upper bound**: a real room with distance and echo is harder. Use it to compare settings and models. |
| **near-miss** | Share of the sound-alike test clips (such as "hey klaus") that wake the model. Lower is better. |
| **false alarms/h** | How often per hour the model wakes up on 10.7 hours of real-world audio without the wake word. 0.28 per hour means about once every 3.5 hours of non-stop sound (talk, TV, music). |

> [!IMPORTANT]
> `evaluate.py` counts a trigger after **2** frames above the threshold by default. Spot Voice uses more
> frames (see the table below; default 4). To compare with the Spot, pass the same values, for example
> `--thresholds 0.6 --frames 4`. The stop word uses 2 frames on the Spot, so its default evaluation already matches.

### Sensitivity: what the numbers mean on the Spot

The wake word fires when the score stays above the threshold for several frames in a row (one frame = 80 ms).
The number of frames matters much more than the threshold. The installer offers three settings
(`./install.sh voice --sensitivity high|normal|low`). Measured with `evaluate.py` for **Hey Claude**
(v2 model): false alarms on the 10.7 h of real-world audio, recall on 400 fresh synthetic "Hey Claude" clips.

| Sensitivity | Threshold / frames | Recall | False wakes per hour |
|---|---|---|---|
| `high` | 0.6 / 3 | 63 % | 0.93 |
| `normal` (default) | 0.6 / 4 | 55 % | 0.28 (about once every 3.5 h) |
| `low` | 0.6 / 5 | 45 % | 0 in 10.7 h |

An older setting (0.7 with 2 frames) had 70 % recall but 1.9 false wakes per hour: the Spot "switched itself on"
in a room with voices. That is why the frame rule exists. The stop word "Danke Claude" (0.5, 2 frames) has
0.47 false triggers per hour, and it only listens during a conversation.

Values for ChatGPT will follow once its models are trained and measured.

### Test with your own voice

Record yourself saying the phrase a few times (also from across the room), as 16 kHz mono 16-bit WAV files,
and pass the folder with `--positive`:

```bash
brew install ffmpeg            # once, to convert recordings
mkdir -p my-voice
ffmpeg -i my-recording.m4a -ac 1 -ar 16000 -sample_fmt s16 my-voice/hey-1.wav
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx --positive my-voice --thresholds 0.6 --frames 4
```

For a harder test with music in the background:

```bash
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx --background data/fma --snr 5
```

| Option | Default | Meaning |
|---|---|---|
| `--thresholds` | `0.3 … 0.9` (steps of 0.1) | thresholds to test |
| `--frames` | `2` | consecutive frames above the threshold for a trigger |
| `--refractory` | `2.0` | seconds without a new trigger after one |
| `--positive DIR` | the model's `positive_test` clips | WAVs that should trigger |
| `--negative DIR` | the model's `negative_test` clips | WAVs that should not trigger |
| `--max-clips` | `2000` | random sample per folder, `0` = all |
| `--background DIR`, `--snr` | none, `10` | mix background audio into the clips at this speech-to-background ratio (dB) |

### Choose a threshold

Pick the row where the false alarms are low enough for your room. Then set it on the Spot without reinstalling
(the Spot must be connected over adb, see [chapter 4](04-setup.md)):

```bash
adb shell am broadcast -a ai.pocket.heyclaude.SET_THRESHOLD -p ai.pocket.heyclaude --ef wake 0.6 --ef stop 0.5 --ei frames 4
```

> [!NOTE]
> Every run of `./install.sh voice` sets the threshold back to 0.6 (stop word 0.5) and the frames to your
> `--sensitivity`. If your own model needs another threshold, send the broadcast again after `./install.sh voice`.

## Deploy the model to the Spot

### With the installer (recommended)

1. Copy the models into `models/` in the repository. Run these commands in the repository folder (if you are still
   in `training/` from the evaluation above, go back with `cd ..`). For the Claude or ChatGPT word sets the names
   already fit:

   ```bash
   cp training/out/models/claude/*.onnx models/
   ```

   For your own word set, rename them to the assistant you use (here: Claude):

   ```bash
   cp training/out/models/computer/hey_computer.onnx   models/hey_claude.onnx
   cp training/out/models/computer/danke_computer.onnx models/danke_claude.onnx
   ```

2. Install them on the Spot:

   ```bash
   ./install.sh voice
   ```

The voice phase pushes `hey_<assistant>.onnx` and `danke_<assistant>.onnx` from `models/` to the Spot.

> [!WARNING]
> This replaces the models that came with the repository. If you cloned it with git, `git checkout -- models/`
> brings the original files back; then run `./install.sh voice` again.

### By hand with adb

The app looks in its files folder first, and the `SET_ASSISTANT` broadcast reloads the models without a reinstall:

```bash
adb push training/out/models/claude/hey_claude.onnx   /sdcard/Android/data/ai.pocket.heyclaude/files/hey_claude.onnx
adb push training/out/models/claude/danke_claude.onnx /sdcard/Android/data/ai.pocket.heyclaude/files/danke_claude.onnx
adb shell am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant claude
```

For ChatGPT use `hey_chatgpt.onnx`, `danke_chatgpt.onnx` and `--es assistant chatgpt`. Over Wi-Fi, add
`-s 192.168.x.y:5555` after `adb`.

> [!NOTE]
> The next `./install.sh voice` pushes the files from `models/` again and overwrites what you pushed by hand.
> Keep `models/` up to date if you want to keep your model.

## Licences

| Part | Licence |
|---|---|
| Code in `training/` (and the rest of this project) | MIT |
| openWakeWord (training code) | Apache 2.0 |
| piper-sample-generator | MIT |
| **Trained models** (yours too) | **CC BY-NC-SA 4.0**: non-commercial, share alike. They are built on openWakeWord's ACAV100M negative features and feature models (`melspectrogram.onnx`, `embedding_model.onnx`), which carry this licence. |
| Piper voices `en_US-libritts_r-medium`, `de_DE-mls-medium` | trained on LibriTTS-R and Multilingual LibriSpeech, both CC BY 4.0 |
| Augmentation data (MIT room impulse responses, Free Music Archive `fma_small`) | used for training only, not distributed; FMA tracks have their own Creative Commons licences |

Details and credits: [THIRD_PARTY.md](../../THIRD_PARTY.md).

## What the installer does for you

`./install.sh train …` starts `training/train.sh` with your arguments. That script then does everything:

- sets up the Python environment with pinned versions and patches (first time only),
- downloads and checks the training data (sha256),
- checks free disk space before it fills the disk,
- writes the configs and removes outdated clips and features,
- trains wake word and stop word in parallel and keeps the Mac awake,
- copies the results to `training/out/models/<set>/`, evaluates them and prints the next steps.

It does **not** copy the models into `models/` or onto the Spot. You decide that in
[Deploy the model to the Spot](#deploy-the-model-to-the-spot).

## Check

1. After `./install.sh voice`, the check table of `./install.sh verify` shows
   `✓ Wake-word models on the Spot (hey_claude.onnx, danke_claude.onnx)`.
2. Open **Spot Voice** on the Spot. The status shows `Model: hey_claude (external)`. "external" means the
   file you pushed is used. `alexa_v0.1 (fallback)` means no usable model was found.

   <p align="center">
     <img src="../images/shot-voice-app.png" alt="Spot Voice app on the round screen, German UI: status lines with Modell: hey_claude (external), the button Zuhören stoppen and the assistant choice Claude or ChatGPT" width="260">
   </p>

3. Or look at the log:

   ```bash
   adb logcat -d -s HeyClaude OpenWakeWord | grep -E "Detector ready|extra model"
   ```

   You should see `Detector ready for claude, model=hey_claude (external)`. The stop word is loaded at the first
   conversation: `extra model: danke_claude (external) …`.
4. Say your wake word towards the Spot. The centre of the clock pulses and, when the offline voice commands are
   installed, you hear a short beep.

## If something goes wrong

| Message or symptom | What to do |
|---|---|
| `uv not found. Install it …` | Install uv (see [What you need](#what-you-need)) and open a new terminal. |
| `'git' is missing, please install it` (or `curl`, `patch`) | macOS: `xcode-select --install`. Linux: install it with your package manager. |
| `the path of this folder is too long for espeak-ng (… characters, at most 93)` or `Error processing file '…/espeak-ng-data/phontab'` | Move the repository to a shorter path, for example `~/Echo_spot_second_life`. |
| `No pronunciation for '<word>' …` | Add the word under `pronunciations:` in your word file. |
| `word file not found: …` | The path is relative to the folder you are in. From the repository folder: `training/words/<file>.yaml`. |
| `set name '…' may only contain letters, digits, _ and -` (or the same for a model name) | Rename it in the word file. |
| `out/ needs ~8 GB free disk, …` or `… needs ~N GB free disk, …` | Free some space, or put the data elsewhere with `OWW_DATA_DIR`. After a successful run you can delete `training/out/<set>/<model>/` (the clips). |
| `checksum mismatch …` or `download failed: …` | Check the internet connection and run the same command again. |
| `… does not apply to openwakeword/ (local changes? delete the folder and re-run)` | Delete `training/openwakeword/` and run again. |
| `training failed (logs: out/<set>/train_*.log). Re-run the same command to continue.` | Read the end of the log, fix the cause, run the same command again. |
| Training stopped (lid closed, Ctrl+C) | Run the same command again. It continues. |
| The model works badly on the Spot | [Wake word not recognised](09-troubleshooting.md#wake-word-not-recognised) and [The Spot wakes up by itself](09-troubleshooting.md#the-spot-wakes-up-by-itself) |

More help: [9 · Troubleshooting and FAQ](09-troubleshooting.md).

---
[← 7 · The clock](07-clock.md) · [Overview](../../README.md) · [9 · Troubleshooting and FAQ →](09-troubleshooting.md)
