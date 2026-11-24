<sub>[English](../en/08-wakeword-training.md) · **Deutsch**</sub>

# 8 · Eigenes Wake-Word trainieren

> **Ziel:** ein Wake-Word und ein Stoppwort auf deinem Computer trainieren, messen, wie gut sie funktionieren, und sie auf den Spot bringen · **Zeit:** 1,5–2,5 h Rechenzeit für einen vollen Lauf (30–45 min für einen Schnelltest), davon etwa 15 min für dich · **Installer:** `./install.sh train <set>`

Im Repository liegen schon fertig trainierte Modelle für „Hey Claude“, „Danke Claude“, „Hey ChatGPT“ und
„Danke ChatGPT“ in `models/`. Für den normalen Betrieb brauchst du dieses Kapitel also **nicht**. Es ist für dich,
wenn du ein anderes Wort willst oder wenn das Wake-Word bei dir zu Hause nicht gut passt.

Alles in diesem Kapitel läuft auf deinem Computer. Den Spot brauchst du erst ganz am Ende, um das Ergebnis aufzuspielen.

<a id="why-train-your-own-word"></a>

## Warum ein eigenes Wort?

| Situation | Was dir das Training bringt |
|---|---|
| Du hast `models/hey_chatgpt.onnx` gelöscht oder willst seine Ausdrücke ändern | Ohne diese Datei hört Spot Voice auf sein eingebautes Ersatzwort **„Alexa“**. `./install.sh train chatgpt` trainiert „Hey ChatGPT“ und „Danke ChatGPT“ neu aus der Wortdatei im Repository. |
| Du willst ein anderes Wort, zum Beispiel „Hey Computer“ | Eine eigene Wortdatei, eine kleine Textdatei. |
| Der Spot wacht bei etwas auf, das bei dir oft fällt (ein Name, eine Fernsehsendung) | Trag diesen Ausdruck als *Negativphrase* ein und trainiere neu. Siehe auch [Der Spot wacht von selbst auf](09-troubleshooting.md#der-spot-wacht-von-selbst-auf). |
| Das Wort wird so, wie du es sagst, schlecht erkannt | Ergänze Schreibweisen, die nach deiner Aussprache klingen. |

> [!NOTE]
> Trainiert wird **nur mit künstlichen Stimmen**. Über 1.100 englische und deutsche [Piper](https://github.com/rhasspy/piper-sample-generator)-Sprecher
> sagen deinen Ausdruck, und das Modell lernt, ihn von rund 1.000 Stunden anderer Audioaufnahmen zu unterscheiden.
> Aufnahmen deiner eigenen Stimme fließen nicht ins Training ein. Du kannst damit aber ein Modell **testen**, siehe
> [Ein Modell messen](#ein-modell-messen).

<a id="what-you-need"></a>

## Was du brauchst

| | |
|---|---|
| Computer | Ein Mac mit Apple Silicon (getestet mit macOS 26). Linux sollte gehen, ist aber ungetestet. Windows wird nicht unterstützt. |
| Freier Speicher | Etwa **20 GB**: 9 GB Trainingsdaten, 2 GB Werkzeuge, etwa 4 GB erzeugte Clips pro Modell. |
| Arbeitsspeicher | **16 GB RAM** oder mehr. Die Sprachclips entstehen in Portionen zu 1500, jede in einem eigenen Prozess, weil das Apple-GPU-Backend von PyTorch für jede neue Satzlänge Speicher belegt und nie freigibt (vor dieser Korrektur bis zu ~50 GB pro Modell). Portionsgröße: `OWW_CLIP_CHUNK`. |
| Download | Beim ersten Mal etwa 10 GB. Spätere Läufe nutzen ihn wieder. |
| Zeit | Ein voller Lauf dauert **1,5 bis 2,5 Stunden**, der Schnelltest mit `--quick` 30 bis 45 Minuten. |
| Werkzeuge | `git`, `curl`, `patch` und [uv](https://docs.astral.sh/uv/). uv installiert das passende Python (3.11) selbst. |
| Ordnerpfad | Der Pfad des Ordners `training/` darf höchstens 93 Zeichen lang sein (eine Grenze von espeak-ng, das die Stimmen nutzen). |
| Der Spot | Für das Training nicht nötig. Nur für den letzten Schritt, [das Modell auf den Spot bringen](#das-modell-auf-den-spot-bringen). |

Die Werkzeuge installierst du einmal:

1. Auf dem Mac die Command Line Tools installieren (darin stecken `git` und `patch`). Klappt `git --version` schon, überspring diesen Schritt.

   ```bash
   xcode-select --install
   ```

2. uv installieren:

   ```bash
   curl -LsSf https://astral.sh/uv/install.sh | sh
   ```

3. Ein **neues** Terminalfenster öffnen, damit `uv` gefunden wird.

> [!TIP]
> Lass den Mac am Netzteil und den Deckel offen. Das Trainingsskript hält den Mac mit `caffeinate` wach,
> aber Zuklappen kann es trotzdem anhalten. Passiert das, starte einfach denselben Befehl noch einmal.

<a id="train-with-one-command"></a>

## Trainieren mit einem Befehl

Du startest alles aus dem Repository-Ordner. `./install.sh train` reicht alle Argumente an
`training/train.sh` weiter. `./install.sh train claude` ist also dasselbe wie `cd training && ./train.sh claude`.

| Befehl | Wortdatei | Ergebnis in `training/out/models/<set>/` |
|---|---|---|
| `./install.sh train claude` | `training/words/claude.yaml` | `hey_claude.onnx` + `danke_claude.onnx` |
| `./install.sh train chatgpt` | `training/words/chatgpt.yaml` | `hey_chatgpt.onnx` + `danke_chatgpt.onnx` |
| `./install.sh train training/words/computer.yaml` | deine eigene Datei | die Modellnamen aus deiner Datei |
| `--quick` an einen der Befehle anhängen | | 5.000 Beispiele und 10.000 Schritte statt 20.000 und 50.000. Zeigt nur, dass alles läuft; für den Alltag sind diese Modelle nicht gut genug. |

Der Pfad zu deiner eigenen Wortdatei gilt relativ zu dem Ordner, in dem du gerade bist.

1. Ein Terminal im Repository-Ordner öffnen.
2. Optional, beim ersten Mal aber sinnvoll: den Schnelltest starten.

   ```bash
   ./install.sh train claude --quick
   ```

3. Das richtige Training starten:

   ```bash
   ./install.sh train claude
   ```

4. Warten. Das Skript schreibt eine Statuszeile, sobald ein Modell einen neuen Schritt beginnt (siehe unten).
5. Am Ende zeigt das Skript, wo die Modelle liegen, eine Tabelle mit Messwerten und die nächsten Schritte.

Ein Probelauf zeigt, was gestartet würde, ohne es zu starten:

```
$ ./install.sh --dry-run train claude --quick
+ bash training/train.sh claude --quick
```

<a id="what-you-see-while-it-runs"></a>

### Was du während des Trainings siehst

Das Skript arbeitet in fünf Schritten. Seine Ausgabe ist englisch und sieht so aus (Zeiten und Pfade sind Beispiele):

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

| Schritt | Was passiert |
|---|---|
| 1/5 Setup | Nur beim ersten Mal (`setup.sh`): legt `training/.venv/` mit genau den getesteten Paketversionen an (`constraints.txt`), klont openWakeWord und piper-sample-generator auf festen Commits, spielt die Patches aus `training/patches/` ein und lädt die beiden Piper-Stimmen und die Merkmalsmodelle von openWakeWord. Jeder Download wird per sha256 geprüft. |
| 2/5 Trainingsdaten | Lädt etwa 11 Stunden Negativ-Audio als fertige Merkmale (damit werden Fehlalarme gezählt), zwei je 4 GB große Teile der ACAV100M-Negativmerkmale, Raumimpulsantworten und 4 Stunden Musik. Prüft vorher den freien Speicher. |
| 3/5 Configs | Schreibt pro Modell eine openWakeWord-Konfiguration nach `training/out/<set>/`. |
| 4/5 Training | Beide Modelle trainieren gleichzeitig. Für jedes: `--generate_clips` (Piper spricht die Ausdrücke und ähnlich klingende Ausdrücke), `--augment_clips` (mischt Rauschen, Musik und Raumhall dazu), `--train_model` (trainiert den kleinen Klassifikator). |
| 5/5 Ergebnisse | Kopiert die Modelle nach `training/out/models/<set>/`, startet `evaluate.py` und zeigt die nächsten Schritte. |

<a id="stop-and-continue"></a>

### Anhalten und weitermachen

Du kannst jederzeit mit **Strg+C** abbrechen. Starte denselben Befehl erneut, um weiterzumachen: Fertige Downloads,
Clips und Merkmale werden wiederverwendet. Nur das abschließende Training des Klassifikators (etwa 10 Minuten) läuft jedes Mal neu.
Die vollständigen Logs liegen in `training/out/<set>/train_<modell>.log`.

<a id="settings-for-experts"></a>

### Einstellungen für Fortgeschrittene

| Variable | Standard | Bedeutung |
|---|---|---|
| `OWW_DATA_DIR` | `training/data` | Wo die 9 GB Trainingsdaten liegen. Mehrere Wortdateien können sie gemeinsam nutzen, zum Beispiel auf einer externen Festplatte. |
| `OWW_LAYER` | `64` | Größe der Klassifikator-Schicht. |
| `OWW_AUG_ROUNDS` | `2` | Wie oft jeder Clip mit Rauschen, Musik und Hall gemischt wird. |
| `OWW_NEG_WEIGHT` | `2000` | Höchstes Gewicht der Negativbeispiele (höher = weniger Fehlalarme, aber geringere Trefferquote). |

Beispiel: `OWW_DATA_DIR=/Volumes/Data/oww ./install.sh train claude`.

<a id="how-a-word-file-works"></a>

## So funktioniert eine Wortdatei

Ein Wortset ist eine kleine YAML-Datei in `training/words/`. Im Repository gibt es drei:
`claude.yaml`, `chatgpt.yaml` und die kommentierte Vorlage `example-custom.yaml`.

1. Die Vorlage kopieren:

   ```bash
   cp training/words/example-custom.yaml training/words/computer.yaml
   ```

2. `training/words/computer.yaml` in einem Texteditor öffnen und die Ausdrücke ändern (Details unten).
3. Mit einem Schnelltest prüfen, ob alles läuft:

   ```bash
   ./install.sh train training/words/computer.yaml --quick
   ```

4. Richtig trainieren:

   ```bash
   ./install.sh train training/words/computer.yaml
   ```

So sieht die Vorlage aus, gekürzt (die Kommentare in der Datei sind englisch):

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

| Schlüssel | Bedeutung |
|---|---|
| `name` | Name des Sets. Daraus werden `training/out/<name>/` und `training/out/models/<name>/`. Optional, Standard ist der Dateiname. Nur Buchstaben, Ziffern, `_` und `-`. |
| `models` | Ein oder zwei Modelle. Jeder Name wird zum Dateinamen (`hey_computer` → `hey_computer.onnx`). Nur Buchstaben, Ziffern, `_` und `-`. |
| `models.<modell>.target_phrase` | Die Schreibweisen des Ausdrucks. Jede Schreibweise wird von vielen Stimmen gesprochen. |
| `models.<modell>.custom_negative_phrases` | Ausdrücke, auf die das Modell **nicht** reagieren soll. |
| `german_words` | Enthält ein Ausdruck eines dieser Wörter, spricht ihn die deutsche Stimme (236 Sprecher). Alle anderen spricht die englische Stimme (904 Sprecher). Lässt du den Schlüssel weg, gilt die eingebaute Liste. |
| `pronunciations` | Aussprache für Wörter, die das englische Aussprachewörterbuch (CMUdict) nicht kennt. Überschreibt auch CMUdict. |

<a id="spellings-and-phonetic-tricks"></a>

### Schreibweisen und Lautschrift-Tricks

Die Stimmen lesen vor, was du schreibst. Spricht eine Stimme dein Wort falsch aus, schreib es so, wie es klingen soll:

| Schreibweise | Wirkung |
|---|---|
| `hey klohd` | Die deutsche Stimme sagt „Claude“ so, wie Deutsche es aussprechen (`klohd` steht in `german_words`). |
| `hey claude`, `hey clode` | Die englische Stimme sagt es englisch. |
| `hey tschett dschi pi ti` | Deutsche Aussprache von „ChatGPT“, gelesen von der deutschen Stimme. |
| `hey chat g p t`, `hey chat gee pee tee` | Die englische Stimme buchstabiert die Buchstaben. |
| `hey, computer!` | Satzzeichen ändern Rhythmus und Betonung. |

Regeln:

- Schreib **nur kleine ASCII-Buchstaben**, also keine Großbuchstaben, Umlaute oder ß. Umlaute und Ziffern fallen bei der
  Aussprache-Suche weg. Schreib deshalb `schoen`, `tschuess` und `g p t` statt `schön`, `tschüss` und `GPT`.
- Mehrere Schreibweisen desselben Ausdrucks sind besser als eine.

<a id="negative-phrases"></a>

### Negativphrasen

Liste auf, was das Modell überhören soll:

- die Teile deines Ausdrucks für sich allein (`hey`, `computer`),
- ähnlich klingende Wörter (`hey commuter`, `hey peter`),
- die Wake-Words anderer Assistenten (`alexa`, `okay google`, `hey siri`),
- Namen oder Wörter, die bei dir zu Hause oft fallen und ähnlich klingen.

Ähnlich klingende Ausdrücke werden zusätzlich automatisch aus der Aussprache deines Ausdrucks erzeugt.
Du musst also nicht an alles selbst denken.

<a id="pronunciations-for-unknown-words"></a>

### Aussprache für unbekannte Wörter

Für die automatisch erzeugten ähnlich klingenden Ausdrücke braucht das Training die Aussprache jedes Worts in deinen
Ausdrücken. Wörter, die nicht in CMUdict stehen (etwa `klohd`, `tschett` oder `kompjuter`), brauchen einen Eintrag in
[ARPAbet](https://en.wikipedia.org/wiki/ARPABET)-Lautschrift. Fehlt er, bricht das Training mit dieser Meldung ab:

```
No pronunciation for 'kompjuter': add it under 'pronunciations:' in the word file (ARPAbet, e.g. 'klohd: K L OW1 D')
```

Weitere Beispiele findest du in `claude.yaml` und `chatgpt.yaml` (`klohd: K L OW1 D`, `tschett: CH EH1 T`, `dschi: JH IY1`).

<a id="name-the-models-for-spot-voice"></a>

### Modelle für Spot Voice benennen

Spot Voice lädt für den aktiven Assistenten genau zwei Dateien: `hey_<assistent>.onnx` (Wake-Word) und
`danke_<assistent>.onnx` (Stoppwort). `<assistent>` ist dabei `claude` oder `chatgpt`. Willst du „Hey Computer“
mit dem Claude-Profil nutzen, nenn die Modelle in deiner Wortdatei `hey_claude` und `danke_claude`, oder benenne die
Dateien um, wenn du [sie auf den Spot bringst](#das-modell-auf-den-spot-bringen).

> [!NOTE]
> Das Stoppwort-Modell ist optional. Ohne es beendest du ein Gespräch, indem du das Wake-Word noch einmal sagst.
> Verabschiedungen wie „Danke Claude“ oder „Thank you Claude“ erkennt Spot Voice außerdem im Transkript, das der
> Assistent auf dem Bildschirm zeigt, ganz unabhängig vom Modell (siehe [Kapitel 5](05-voice-assistant.md)).

Änderst du die Ausdrücke, werden die veralteten Clips beim nächsten Lauf automatisch gelöscht. Änderst du die Zahl der
Beispiele, werden die Merkmale gelöscht. Willst du ein Set ganz von vorn trainieren, lösch `training/out/<set>/`.

<a id="evaluate-a-model"></a>

## Ein Modell messen

`train.sh` misst die neuen Modelle am Ende selbst. Du kannst die Messung jederzeit wiederholen, zum Beispiel mit
anderen Schwellen. Sie braucht die Umgebung und die Daten, die `train.sh` eingerichtet hat (ein Lauf genügt, auch mit
`--quick`).

```bash
cd training
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx out/models/claude/danke_claude.onnx
```

`evaluate.py` rechnet die Modelle genau so wie die App auf dem Spot: erst das Mel-Spektrogramm, dann das Embedding,
dann der Klassifikator über die letzten 16 Embeddings, ein Schritt pro 80 ms Audio. Die Ausgabe (englisch) hat eine
Zeile pro Schwelle:

```
hey_claude.onnx  (input 16 x 96; trigger: 2 frames > threshold)
  2000 positive clips from out/claude/hey_claude/positive_test
  2000 negative clips from out/claude/hey_claude/negative_test
  10.7 h of negative features from data/validation_set_features.npy
  threshold          recall       near-miss  false alarms/h
       0.30          …
```

| Spalte | Was sie bedeutet |
|---|---|
| **recall** (Trefferquote) | Anteil der Testclips mit deinem Ausdruck, bei denen das Modell auslöst. Diese Clips sind beim Training entstanden, wurden aber nie zum Trainieren benutzt. Es sind saubere künstliche Stimmen, lies die Trefferquote also als **Obergrenze**: Ein echter Raum mit Abstand und Hall ist schwieriger. Nutze sie, um Einstellungen und Modelle zu vergleichen. |
| **near-miss** (Verwechsler) | Anteil der ähnlich klingenden Testclips (etwa „hey klaus“), bei denen das Modell auslöst. Je niedriger, desto besser. |
| **false alarms/h** (Fehlalarme pro Stunde) | Wie oft pro Stunde das Modell in 10,7 Stunden echter Audioaufnahmen ohne Wake-Word auslöst. 0,28 pro Stunde heißt: etwa einmal in 3,5 Stunden Dauerbeschallung (Gespräche, Fernsehen, Musik). |

> [!IMPORTANT]
> `evaluate.py` zählt standardmäßig eine Auslösung nach **2** Fenstern über der Schwelle. Spot Voice verlangt mehr
> Fenster (siehe Tabelle unten, Standard 4). Willst du mit dem Spot vergleichen, gib dieselben Werte an, zum Beispiel
> `--thresholds 0.6 --frames 4`. Das Stoppwort nutzt für Claude 2 Fenster und für ChatGPT 3.

<a id="sensitivity-what-the-numbers-mean-on-the-spot"></a>

### Empfindlichkeit: was die Zahlen auf dem Spot bedeuten

Das Wake-Word löst aus, wenn der Wert mehrere Fenster hintereinander über der Schwelle bleibt (ein Fenster = 80 ms).
Die Zahl der Fenster wirkt viel stärker als die Schwelle. Der Installer bietet drei Stufen an
(`./install.sh voice --sensitivity high|normal|low`). Gemessen mit `evaluate.py` für **Hey Claude** (Modell v2)
und **Hey ChatGPT** (Modell v1): Fehlauslösungen in den 10,7 Stunden echter Audioaufnahmen, Trefferquote an neuen
künstlichen Clips (400 für Claude, 2000 für ChatGPT; verschiedene Testsätze, vergleiche also die Stufen, nicht die Modelle).

| Empfindlichkeit | Schwelle / Fenster | Hey Claude: Trefferquote | Fehlauslösungen/h | Hey ChatGPT: Trefferquote | Fehlauslösungen/h |
|---|---|---|---|---|---|
| `high` | 0,6 / 3 | 63 % | 0,93 | 79 % | 0,65 |
| `normal` (Standard) | 0,6 / 4 | 55 % | 0,28 (etwa alle 3,5 Stunden einmal) | 73 % | 0,19 (etwa alle 5 Stunden einmal) |
| `low` | 0,6 / 5 | 45 % | 0 in 10,7 Stunden | 67 % | 0,09 |

Eine ältere Einstellung (0,7 bei 2 Fenstern) erreichte 70 % Trefferquote, löste aber 1,9-mal pro Stunde grundlos aus:
In einem Raum mit Stimmen hat sich der Spot „von selbst eingeschaltet“. Deshalb gibt es die Regel mit den Fenstern.
Das Stoppwort „Danke Claude“ (0,5, 2 Fenster) löst 0,47-mal pro Stunde fälschlich aus, hört aber nur während eines Gesprächs zu.
„Danke ChatGPT“ braucht auf dem Spot 3 Fenster (0,5, 3 Fenster: 0,84 Fehlauslösungen pro Stunde, 69 % Trefferquote;
mit 2 Fenstern wären es 2,8 pro Stunde). Die Fenster für das Stoppwort stehen je Assistent in `Assistant.kt` (`stopFrames`).

<a id="test-with-your-own-voice"></a>

### Mit deiner eigenen Stimme testen

Nimm dich auf, wie du den Ausdruck ein paarmal sagst (auch quer durch den Raum), speichere die Aufnahmen als WAV
(16 kHz, mono, 16 Bit) und gib den Ordner mit `--positive` an:

```bash
brew install ffmpeg            # einmalig, zum Umwandeln der Aufnahmen
mkdir -p my-voice
ffmpeg -i my-recording.m4a -ac 1 -ar 16000 -sample_fmt s16 my-voice/hey-1.wav
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx --positive my-voice --thresholds 0.6 --frames 4
```

Ein härterer Test mit Musik im Hintergrund:

```bash
.venv/bin/python evaluate.py out/models/claude/hey_claude.onnx --background data/fma --snr 5
```

| Option | Standard | Bedeutung |
|---|---|---|
| `--thresholds` | `0.3 … 0.9` (in Schritten von 0.1) | Schwellen, die getestet werden |
| `--frames` | `2` | wie viele Fenster hintereinander über der Schwelle liegen müssen |
| `--refractory` | `2.0` | Sekunden ohne neue Auslösung nach einer Auslösung |
| `--positive DIR` | die `positive_test`-Clips des Modells | WAVs, die auslösen sollen |
| `--negative DIR` | die `negative_test`-Clips des Modells | WAVs, die nicht auslösen sollen |
| `--max-clips` | `2000` | Zufallsauswahl pro Ordner, `0` = alle |
| `--background DIR`, `--snr` | keiner, `10` | Hintergrund-Audio in die Clips mischen, mit diesem Abstand Sprache zu Hintergrund (dB) |

<a id="choose-a-threshold"></a>

### Eine Schwelle wählen

Nimm die Zeile, in der die Fehlalarme für deinen Raum niedrig genug sind. Dann stellst du die Schwelle auf dem Spot ein,
ohne neu zu installieren (der Spot muss per adb verbunden sein, siehe [Kapitel 4](04-setup.md)):

```bash
adb shell am broadcast -a ai.pocket.heyclaude.SET_THRESHOLD -p ai.pocket.heyclaude --ef wake 0.6 --ef stop 0.5 --ei frames 4
```

> [!NOTE]
> Jeder Lauf von `./install.sh voice` setzt die Schwelle wieder auf 0,6 (Stoppwort 0,5) und die Fenster auf deine
> `--sensitivity`. Braucht dein eigenes Modell eine andere Schwelle, schick den Broadcast nach `./install.sh voice` erneut.

<a id="deploy-the-model-to-the-spot"></a>

## Das Modell auf den Spot bringen

<a id="with-the-installer-recommended"></a>

### Mit dem Installer (empfohlen)

1. Die Modelle nach `models/` im Repository kopieren. Diese Befehle laufen im Repository-Ordner (bist du von der
   Auswertung oben noch in `training/`, gehst du mit `cd ..` zurück). Bei den Wortdateien für Claude und ChatGPT
   passen die Namen schon:

   ```bash
   cp training/out/models/claude/*.onnx models/
   ```

   Bei deiner eigenen Wortdatei benennst du sie nach dem Assistenten, den du nutzt (hier: Claude):

   ```bash
   cp training/out/models/computer/hey_computer.onnx   models/hey_claude.onnx
   cp training/out/models/computer/danke_computer.onnx models/danke_claude.onnx
   ```

2. Auf den Spot spielen:

   ```bash
   ./install.sh voice
   ```

Die Phase `voice` kopiert `hey_<assistent>.onnx` und `danke_<assistent>.onnx` aus `models/` auf den Spot.

> [!WARNING]
> Damit ersetzt du die Modelle, die mit dem Repository kamen. Hast du es mit git geklont, holt `git checkout -- models/`
> die Originale zurück; danach `./install.sh voice` noch einmal starten.

<a id="by-hand-with-adb"></a>

### Von Hand mit adb

Die App sucht zuerst in ihrem Dateiordner, und der Broadcast `SET_ASSISTANT` lädt die Modelle ohne Neuinstallation neu:

```bash
adb push training/out/models/claude/hey_claude.onnx   /sdcard/Android/data/ai.pocket.heyclaude/files/hey_claude.onnx
adb push training/out/models/claude/danke_claude.onnx /sdcard/Android/data/ai.pocket.heyclaude/files/danke_claude.onnx
adb shell am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant claude
```

Für ChatGPT nimmst du `hey_chatgpt.onnx`, `danke_chatgpt.onnx` und `--es assistant chatgpt`. Über WLAN setzt du
`-s 192.168.x.y:5555` direkt hinter `adb`.

> [!NOTE]
> Das nächste `./install.sh voice` kopiert die Dateien aus `models/` wieder auf den Spot und überschreibt, was du von Hand
> kopiert hast. Halte `models/` aktuell, wenn du dein Modell behalten willst.

<a id="licences"></a>

## Lizenzen

| Teil | Lizenz |
|---|---|
| Code in `training/` (und im Rest dieses Projekts) | MIT |
| openWakeWord (Trainingscode) | Apache 2.0 |
| piper-sample-generator | MIT |
| **Trainierte Modelle** (auch deine) | **CC BY-NC-SA 4.0**: nicht kommerziell, Weitergabe unter gleichen Bedingungen. Sie bauen auf den ACAV100M-Negativmerkmalen und den Merkmalsmodellen von openWakeWord auf (`melspectrogram.onnx`, `embedding_model.onnx`), die diese Lizenz tragen. |
| Piper-Stimmen `en_US-libritts_r-medium`, `de_DE-mls-medium` | trainiert auf LibriTTS-R und Multilingual LibriSpeech, beide CC BY 4.0 |
| Daten zum Verfremden (Raumimpulsantworten des MIT, Free Music Archive `fma_small`) | nur fürs Training genutzt, nicht weitergegeben; die FMA-Stücke haben eigene Creative-Commons-Lizenzen |

Details und Danksagungen: [THIRD_PARTY.md](../../THIRD_PARTY.md).

<a id="what-the-installer-does-for-you"></a>

## Was der Installer für dich erledigt

`./install.sh train …` startet `training/train.sh` mit deinen Argumenten. Dieses Skript erledigt dann alles:

- richtet die Python-Umgebung mit festen Versionen und Patches ein (nur beim ersten Mal),
- lädt die Trainingsdaten und prüft sie (sha256),
- prüft den freien Speicher, bevor es die Platte füllt,
- schreibt die Konfigurationen und löscht veraltete Clips und Merkmale,
- trainiert Wake-Word und Stoppwort parallel und hält den Mac wach,
- kopiert die Ergebnisse nach `training/out/models/<set>/`, misst sie und zeigt die nächsten Schritte.

Es kopiert die Modelle **nicht** nach `models/` und nicht auf den Spot. Das entscheidest du selbst, siehe
[Das Modell auf den Spot bringen](#das-modell-auf-den-spot-bringen).

<a id="check"></a>

## Prüfen

1. Nach `./install.sh voice` zeigt die Prüftabelle von `./install.sh verify`
   `✓ Wake-Word-Modelle auf dem Spot (hey_claude.onnx, danke_claude.onnx)`.
2. Öffne **Spot Voice** auf dem Spot. Der Status zeigt `Modell: hey_claude (external)`. „external“ heißt: Die Datei,
   die du aufgespielt hast, wird benutzt. `alexa_v0.1 (fallback)` heißt: Es wurde kein brauchbares Modell gefunden.

   <p align="center">
     <img src="../images/shot-voice-app.png" alt="Die App Spot Voice auf dem runden Display, deutsche Oberfläche: Statuszeilen mit Modell: hey_claude (external), der Button Zuhören stoppen und die Auswahl des Assistenten, Claude oder ChatGPT" width="260">
   </p>

3. Oder schau ins Log:

   ```bash
   adb logcat -d -s HeyClaude OpenWakeWord | grep -E "Detector ready|extra model"
   ```

   Dort sollte `Detector ready for claude, model=hey_claude (external)` stehen. Das Stoppwort wird beim ersten
   Gespräch geladen: `extra model: danke_claude (external) …`.
4. Sag dein Wake-Word in Richtung Spot. Die Mitte der Uhr pulsiert, und wenn die Offline-Sprachbefehle installiert sind,
   hörst du einen kurzen Piepton.

<a id="if-something-goes-wrong"></a>

## Wenn etwas schiefgeht

| Meldung oder Anzeichen | Was du tun kannst |
|---|---|
| `uv not found. Install it …` | uv installieren (siehe [Was du brauchst](#was-du-brauchst)) und ein neues Terminal öffnen. |
| `'git' is missing, please install it` (oder `curl`, `patch`) | macOS: `xcode-select --install`. Linux: mit dem Paketmanager installieren. |
| `the path of this folder is too long for espeak-ng (… characters, at most 93)` oder `Error processing file '…/espeak-ng-data/phontab'` | Das Repository an einen kürzeren Pfad verschieben, zum Beispiel `~/Echo_spot_second_life`. |
| `No pronunciation for '<wort>' …` | Das Wort unter `pronunciations:` in deiner Wortdatei eintragen. |
| `word file not found: …` | Der Pfad gilt relativ zum aktuellen Ordner. Aus dem Repository-Ordner: `training/words/<datei>.yaml`. |
| `set name '…' may only contain letters, digits, _ and -` (oder dasselbe für einen Modellnamen) | In der Wortdatei umbenennen. |
| `out/ needs ~8 GB free disk, …` oder `… needs ~N GB free disk, …` | Platz schaffen oder die Daten mit `OWW_DATA_DIR` woanders ablegen. Nach einem erfolgreichen Lauf kannst du `training/out/<set>/<modell>/` (die Clips) löschen. |
| `checksum mismatch …` oder `download failed: …` | Internetverbindung prüfen und denselben Befehl noch einmal starten. |
| `… does not apply to openwakeword/ (local changes? delete the folder and re-run)` | `training/openwakeword/` löschen und neu starten. |
| `training failed (logs: out/<set>/train_*.log). Re-run the same command to continue.` | Das Ende des Logs lesen, die Ursache beheben, denselben Befehl noch einmal starten. |
| Training angehalten (Deckel zu, Strg+C) | Denselben Befehl noch einmal starten. Er macht weiter. |
| Das Modell funktioniert auf dem Spot schlecht | [Wake-Word wird nicht erkannt](09-troubleshooting.md#wake-word-wird-nicht-erkannt) und [Der Spot wacht von selbst auf](09-troubleshooting.md#der-spot-wacht-von-selbst-auf) |

Mehr Hilfe: [9 · Fehlerbehebung und FAQ](09-troubleshooting.md).

---
[← 7 · Die Uhr](07-clock.md) · [Übersicht](../../README.de.md) · [9 · Fehlerbehebung und FAQ →](09-troubleshooting.md)
