<sub>[English](../en/06-voice-commands.md) · **Deutsch**</sub>

# 6 · Sprachbefehle

> **Ziel:** Musik, Internetradio, Timer und Wecker per Stimme steuern, offline auf dem Spot erkannt, ohne den Assistenten zu öffnen. · **Zeit:** 5 Minuten zum Lesen · **Installer:** `./install.sh voice`

Nicht jede Bitte braucht Claude oder ChatGPT. „Stell einen Timer auf zehn Minuten“ oder „spiel Radio SWR drei“ soll einfach passieren, schnell und ohne Cloud. Dafür gibt es die Offline-Sprachbefehle. Die Spracherkennung läuft auf dem Spot selbst, mit einem kleinen [Vosk](https://alphacephei.com/vosk/)-Modell, auf Deutsch oder Englisch.

---

## So funktioniert ein Befehl

1. Sag das Wake-Word, zum Beispiel „Hey Claude“.
2. Du hörst einen kurzen **Piepton**. Läuft auf dem Spot gerade Musik, hält er sie an, und die Mitte der Uhr beginnt zu pulsieren.
3. Sag deinen Befehl innerhalb von etwa **2 Sekunden**, zum Beispiel „stell einen Timer auf fünf Minuten“. Der Spot hört zu, bis du aufhörst zu sprechen, insgesamt aber höchstens etwa 7 Sekunden.
4. Vosk macht aus dem Gesagten Text, und zwar **auf dem Spot**. Der Befehls-Parser gleicht ihn mit den Tabellen unten ab.

Was danach passiert, hängt davon ab, was der Spot gehört hat:

| Der Spot hat gehört | Ergebnis |
|---|---|
| einen Befehl | Er wird ausgeführt, und ein kurzer Bestätigungston erklingt. Bei „spiel …“ und „Radio …“ ist die startende Musik die Bestätigung (klappt das nicht, kommt ein tiefer Fehlerton). Der Assistent öffnet sich **nicht**. |
| nichts innerhalb von 2 Sekunden | Der Assistent öffnet sich im Sprachmodus (Kapitel 5). |
| ein oder zwei Wörter, die kein Befehl sind | Der Assistent öffnet sich. Die Wörter selbst werden nicht weitergegeben. |
| mehr als zwei Wörter, die kein Befehl sind | Das gilt als **Fehlalarm**: Nach einem echten Wake-Word kommt eine Pause. Wird einfach weitergeredet, hat das Wake-Word auf ein Gespräch im Raum reagiert. Ein tiefer Fehlerton erklingt, nichts öffnet sich, und Musik, die lief, spielt weiter. |

Musik nach einem Befehl: Nach einem Lautstärke-Befehl, „nächstes Lied“ oder „vorheriges Lied“ läuft die Musik von selbst weiter. Nach anderen Befehlen (zum Beispiel einem Timer) bleibt sie pausiert. Sag dann das Wake-Word und „weiter“, um sie fortzusetzen.

> [!TIP]
> Warte auf den Piepton und sag den Befehl dann in einem Zug. Nimm die Formulierungen aus den Tabellen unten. Der Parser versteht viele Varianten, aber keine freien Sätze.

> [!NOTE]
> Ohne Vosk-Modell auf dem Spot sind die Sprachbefehle aus. Das Wake-Word öffnet den Assistenten dann sofort, ohne Piepton.

---

## Sprache der Sprachbefehle

Sprachbefehle funktionieren auf **Deutsch** oder **Englisch**, immer in einer Sprache. Standard ist die Systemsprache des Spot. Die Sprache der Befehle hat nichts mit dem Assistenten zu tun: Claude kann in jeder Sprache mit dir reden, aber die Offline-Befehle brauchen die Sprache, die du hier gewählt hast.

Jede Sprache braucht ihr eigenes Vosk-Modell auf dem Spot (`vosk-de` oder `vosk-en`). Der Installer kopiert nur das Modell deiner Sprache. So wechselst du:

| Weg | Befehl oder Schritt | Hinweise |
|---|---|---|
| Installer (empfohlen) | `./install.sh voice --lang de` (oder `--lang en`) | Lädt das Modell der Sprache, kopiert es auf den Spot und schaltet um. `--lang` wird gespeichert und legt auch die Sprache des Installers fest und, beim nächsten Lauf von `setup`, die Systemsprache des Spot. |
| App Spot Voice | **„Sprache der Sprachbefehle“** → Deutsch / English | Klappt nur, wenn das Modell dieser Sprache schon auf dem Spot liegt. Das verrät dir die Statuszeile `Sprachbefehle (vosk-de): ja`. |
| Broadcast | `adb shell am broadcast -a ai.pocket.heyclaude.SET_LANG -p ai.pocket.heyclaude --es lang de` | Wie die Auswahl in der App. |

---

## Englische Befehle (English)

Stell die Sprache der Sprachbefehle dafür erst auf Englisch. Wörter wie „please“, „can you“, „could you“, „just“, „for me“, „on Spotify“ und ein „hey Claude“ oder „ok ChatGPT“ am Anfang werden ignoriert. „can you play Queen on Spotify please“ wirkt also wie „play Queen“. Zahlen dürfen Wörter oder Ziffern sein.

### Musik

| Du sagst (englisch) | Was passiert |
|---|---|
| „play Queen“, „play Queen on Spotify“, „play some jazz“, „play songs by Adele“, „play the album Thriller“, „put on …“, „listen to …“ | Ein Radiosender mit genau diesem Namen, falls es einen gibt (siehe [Internetradio](#internetradio-so-werden-sender-gefunden)), sonst eine Suche in Spotify. |
| „pause“, „stop“, „stop the music“, „pause the music“, „be quiet“, „stop playing“ | Pause (in jedem Player). Stoppt auch einen klingelnden Timer oder Wecker. |
| „resume“, „continue“, „play“, „play some music“, „keep playing“, „unpause“, „music on“ | Abspielen / weiter. |
| „next“, „skip“, „next song“, „skip this song“, „play the next song“ | Nächster Titel. |
| „previous“, „go back“, „previous song“, „last song“, „play the previous song“ | Vorheriger Titel. |
| „louder“, „volume up“, „turn it up“, „increase the volume“ | Medienlautstärke 2 Stufen lauter. |
| „much louder“, „a lot louder“ | 4 Stufen lauter. |
| „quieter“, „softer“, „volume down“, „turn it down“, „lower the volume“ | 2 Stufen leiser. |
| „much quieter“ | 4 Stufen leiser. |

### Radio

| Du sagst (englisch) | Was passiert |
|---|---|
| „radio BBC one“, „play radio KEXP“, „turn on radio Antenne Bayern“, „turn on the radio station KEXP“, „start radio …“, „listen to radio …“ | Sucht den Sender und spielt ihn. |
| „radio off“, „turn off the radio“, „stop the radio“, „turn the radio off“ | Stoppt das Radio. |

### Timer

| Du sagst (englisch) | Timer |
|---|---|
| „set a timer for 5 minutes“, „set a timer for five minutes“ | 5 min |
| „timer ten minutes“, „timer 10 minutes“, „ten minutes“ | 10 min |
| „timer twenty five minutes“ | 25 min |
| „timer for 90 seconds“ | 1 min 30 s |
| „set a timer for half an hour“, „half an hour“ | 30 min |
| „a quarter of an hour“, „three quarters of an hour“ | 15 min, 45 min |
| „timer for one and a half hours“, „set a timer for an hour and a half“ | 1 h 30 min |
| „1 hour 30 minutes“ | 1 h 30 min |
| „wake me up in twenty minutes“ | 20 min (ein Timer, wegen „in“) |
| „cancel the timer“, „stop the timer“, „timer off“, „delete the timer“ | Bricht den laufenden Timer ab. |

Einheiten: Stunden, Minuten und Sekunden („hours“, „minutes“, „seconds“, auch „hrs“, „mins“, „secs“). Der längste Timer dauert 24 Stunden.

### Wecker

| Du sagst (englisch) | Wecker |
|---|---|
| „wake me up at 7“, „wake me up at seven“, „wake me up at seven in the morning“ | 07:00 |
| „set an alarm for 6:30“, „set an alarm for 6.30“, „alarm at six thirty“ | 06:30 |
| „alarm at seven oh five“ | 07:05 |
| „set an alarm for half past seven“ | 07:30 |
| „alarm at quarter past eight“ | 08:15 |
| „wake me at quarter to eight“ | 07:45 |
| „wake me up at ten past six“, „alarm at twenty to seven“ | 06:10, 06:40 |
| „alarm at 6 am“, „wake me up at 6 a.m.“ | 06:00 |
| „alarm at 6 pm“, „set an alarm for 7pm“, „set an alarm for six thirty p m“, „alarm at seven in the evening“ | 18:00, 19:00, 18:30, 19:00 |
| „alarm at noon“, „alarm at midnight“ | 12:00, 00:00 |
| „cancel the alarm“, „turn off the alarm“, „alarm off“, „don't wake me“ | Bricht den Wecker ab. |

Ohne „am“, „pm“, „in the morning“ oder „in the evening“ zählt die Stunde so, wie sie im 24-Stunden-Format dasteht: „alarm at seven“ ist 07:00, „alarm at nineteen“ ist 19:00. Der Wecker klingelt beim nächsten Mal, wenn diese Uhrzeit kommt, also heute oder morgen. Er klingelt einmal und wiederholt sich nicht.

---

## Deutsche Befehle

Stell die Sprache der Sprachbefehle dafür auf Deutsch. Füllwörter wie „bitte“, „mal“, „doch“, „auf Spotify“ und der Name des Assistenten werden ignoriert. Zahlen dürfen Wörter oder Ziffern sein. „fünf und zwanzig“ und „fünfundzwanzig“ klappen beide.

### Musik

| Du sagst | Was passiert |
|---|---|
| „spiel Queen“, „spiel Queen auf Spotify“, „spiele Musik von Queen“, „spiel bitte die Beatles“, „spiel das Album Thriller“ | Ein Radiosender mit genau diesem Namen, falls es einen gibt, sonst eine Suche in Spotify. |
| „Pause“, „stopp“, „stop“, „anhalten“, „halt“, „Ruhe“, „aus“, „Musik aus“ | Pause. Stoppt auch einen klingelnden Timer oder Wecker. |
| „weiter“, „weiterspielen“, „fortsetzen“, „Musik“, „Musik an“, „spiel Musik“, „spiel weiter“ | Abspielen / weiter. |
| „nächstes Lied“, „nächster Song“, „überspringen“, „skip“ | Nächster Titel. |
| „vorheriges Lied“, „zurück“, „nochmal“, „das letzte Lied“ | Vorheriger Titel. |
| „lauter“ / „viel lauter“ | 2 / 4 Stufen lauter. |
| „leiser“ / „viel leiser“ | 2 / 4 Stufen leiser. |

### Radio

| Du sagst | Was passiert |
|---|---|
| „Radio SWR drei“, „spiel Radio Bayern drei“, „Sender Eins Live“, „schalte das Radio an Antenne Bayern“, „mach Radio …“, „starte Radio …“ | Sucht den Sender und spielt ihn. |
| „Radio aus“, „Radio stopp“, „das Radio aus“ | Stoppt das Radio. |

### Timer

| Du sagst | Timer |
|---|---|
| „Timer zehn Minuten“, „zehn Minuten“ | 10 min |
| „stell einen Timer auf fünf Minuten“ | 5 min |
| „Timer fünfundzwanzig Minuten“ | 25 min |
| „Timer neunzig Sekunden“ | 1 min 30 s |
| „Timer halbe Stunde“, „Timer Viertelstunde“ | 30 min, 15 min |
| „Timer anderthalb Stunden“ | 1 h 30 min |
| „Timer eine Stunde und fünf Minuten“ | 1 h 5 min |
| „in zwei Stunden“ | 2 h |
| „Timer aus“, „Timer abbrechen“, „Timer stopp“, „lösche den Timer“, „stoppe den Timer“ | Bricht den laufenden Timer ab. |

Statt „Timer“ versteht der Parser auch „Countdown“, „Stoppuhr“ und „Kurzzeitwecker“ (und die häufigen Verhörer „Teimer“ und „Taimer“). Der längste Timer dauert 24 Stunden.

### Wecker

| Du sagst | Wecker |
|---|---|
| „Wecker um sieben Uhr“, „Wecker um 7 Uhr“, „Wecker um sieben“ | 07:00 |
| „Wecker um sieben Uhr dreißig“ | 07:30 |
| „weck mich um halb sieben“ | 06:30 |
| „Wecker Viertel nach acht“ | 08:15 |
| „Wecker Viertel vor acht“ | 07:45 |
| „Wecker auf sechs“ | 06:00 |
| „Wecker um neunzehn Uhr“ | 19:00 |
| „Wecker aus“, „Wecker ausschalten“, „Wecker abbrechen“, „lösche den Wecker“ | Bricht den Wecker ab. |

Achtung, Unterschied zum Englischen: „halb sieben“ ist **6:30**, das englische „half past seven“ dagegen 7:30. Deutsche Uhrzeiten zählen im 24-Stunden-Format: Für abends sagst du „neunzehn Uhr“.

---

## Internetradio: so werden Sender gefunden

Die Sender kommen von [radio-browser.info](https://www.radio-browser.info/), einem freien, offenen Verzeichnis von Internetradios (kein Konto, kein Schlüssel). Dorthin geht nur der gesprochene Sendername.

1. **Aus den gesprochenen Wörtern wird ein Suchtext.** Zahlwörter werden zu Ziffern, und einzeln gesprochene Buchstaben werden zusammengezogen:

   | Du sagst | Suchtext |
   |---|---|
   | „s w r drei“ | `swr3` |
   | „Bayern drei“ | `bayern 3` |
   | „Eins Live“ | `1 live` |
   | „b b c radio one“ | `bbc radio 1` |
   | „k e x p“ | `kexp` |

   Der Spot sucht mit und ohne Leerzeichen (`swr 3` und `swr3`).
2. **Erst das eigene Land.** Deutsche Befehle suchen zuerst deutsche Sender. Englische Befehle suchen zuerst im Land der Spracheinstellung des Spot (bei `en-US`: in den USA). Findet sich dort nichts, sucht er weltweit.
3. **Den Sender auswählen.**
   - „**Radio** …“ (oder englisch „radio …“): ein Sender, dessen Name genau passt, ohne Rücksicht auf Groß- und Kleinschreibung, Leerzeichen und Satzzeichen. Sonst der beliebteste Sender, dessen Name mit deinen Wörtern beginnt. Sonst das beliebteste Suchergebnis. Gibt es gar nichts, erklingt ein tiefer Fehlerton.
   - „**spiel** …“ (oder englisch „play …“): ein Sender nur, wenn sein Name **genau** passt und mindestens **20** Nutzer von radio-browser.info für ihn gestimmt haben. Sonst geht die Bitte an Spotify. So kann „spiel SWR3“ den bekannten Sender spielen, während „spiel Queen“ bei Spotify landet. Willst du Radio, sag „Radio …“.

Das Radio spielt in Spot Voice. „Pause“, „weiter“ und die Lautstärke-Befehle wirken wie bei jedem Player. Mit Benachrichtigungszugriff (Kapitel 7) zeigt die Uhr Sendername und Logo. Startet eine Spotify-Suche, stoppt das Radio.

> [!TIP]
> Abkürzungen werden am besten Buchstabe für Buchstabe erkannt: „s w r drei“, „n d r zwei“, „b b c radio one“. Willst du einen bestimmten Sender, fang mit „Radio“ an: Diese Suche ist großzügiger als „spiel“.

---

## Spotify

Voraussetzungen:

1. Spotify ist installiert. Der Installer fragt danach bei `./install.sh apps`. Nachholen kannst du das so:

   ```bash
   ./install.sh apps --spotify
   ```

2. Du hast Spotify auf dem Spot einmal geöffnet und dich dort **angemeldet** (auf die Uhr tippen, dann Spotify).

„spiel …“ schickt deine Wörter als Suche an die Spotify-App („play from search“). Spotify kommt nach vorn, um die Wiedergabe zu starten, und nach etwa 6 Sekunden zeigt der Spot wieder die Uhr. Was Spotify bei einer Suche genau spielt, entscheidet die Spotify-App. Darauf hat dieses Projekt keinen Einfluss. Pause, weiter, nächster und vorheriger Titel und die Lautstärke laufen über die normale Mediensteuerung, für Spotify genauso wie fürs Radio.

Ist Spotify nicht installiert oder scheitert die Suche, erklingt ein tiefer Fehlerton.

---

## Timer und Wecker auf der Uhr

Timer und Wecker wohnen in Spot Clock. Sobald einer gestellt ist, zeigt ihn die Uhr:

<p align="center">
  <img src="../images/shot-timer.png" alt="Zifferblatt mit einem orangen Timer-Bogen am Rand, oben 4:53 Restzeit und unten die Weckzeit WECKER 06:30 (deutsche Oberfläche)" width="280">
</p>
<p align="center"><sub>Ein Timer mit 4:53 Restzeit (Bogen am Rand, Zeit oben) und ein Wecker um 06:30 (unten).</sub></p>

- Es gibt immer **einen Timer und einen Wecker**. Ein neuer Timer ersetzt den alten, und genauso ist es beim Wecker.
- Beide funktionieren auch, wenn die Uhr gerade nicht zu sehen ist, zum Beispiel während eines Gesprächs. Der Alarmdienst von Android weckt den Spot.
- Läuft ein Timer oder Wecker ab, kommt die Uhr mit **pulsierenden orangen Ringen** nach vorn und zeigt **TIMER** oder **WECKER** und **„Tippen zum Beenden“**. Der Alarmton des Spot spielt in Schleife, höchstens **2 Minuten** lang.

**Einen klingelnden Timer oder Wecker stoppen:**

- **Tippe auf den Bildschirm**, oder
- sag das Wake-Word und **„stopp“** (deutsch) bzw. **„stop“** (englisch), zum Beispiel „Hey Claude … stopp“.

„Timer aus“ bzw. „timer off“ bricht einen Timer ab, der noch herunterzählt. Einen, der schon klingelt, bringst du mit „stopp“ oder einem Tippen auf den Bildschirm zum Schweigen.

Kapitel 7 erklärt den [Timer-Bogen und die Weckerzeile](07-clock.md#timer-und-wecker-auf-dem-zifferblatt) und die adb-Broadcasts für Timer und Wecker.

---

## Tipps für gute Erkennung

- **Warte auf den Piepton**, dann sprich. Was du vor dem Piepton sagst, kann abgeschnitten werden.
- **Nimm die Formulierungen aus den Tabellen.** Der Parser versteht viele Varianten, aber keine freien Sätze.
- **Sprich in normaler Lautstärke in Richtung Spot.** Der Spot hält seine eigene Musik an, während er zuhört. Ein lauter Fernseher daneben erschwert die Erkennung trotzdem.
- **Buchstabiere Abkürzungen** (bei Radiosendern).
- Die Vosk-Modelle sind klein und schnell, aber ihr Wortschatz ist begrenzt. Ungewöhnliche Künstlernamen werden leicht falsch verstanden. Was der Spot gehört hat, siehst du im Log:

  ```bash
  adb logcat -s HeyClaude
  ```

  ```text
  Command heard: 'timer fünf minuten'
  Voice command 'timer fünf minuten' -> Timer(300s)
  ```

- **Testen ohne Sprechen:** Der Broadcast `COMMAND` führt einen Text so aus, als wäre er gesprochen worden:

  ```bash
  adb shell am broadcast -a ai.pocket.heyclaude.COMMAND -p ai.pocket.heyclaude --es text "timer fünf minuten"
  # mit Englisch als Sprache der Sprachbefehle:
  adb shell am broadcast -a ai.pocket.heyclaude.COMMAND -p ai.pocket.heyclaude --es text "set a timer for five minutes"
  ```

  Der Text wird in der aktuell eingestellten Sprache der Sprachbefehle ausgewertet, und der Befehl läuft wirklich (der Timer oben startet). Das Log zeigt das Ergebnis, zum Beispiel `Test command 'timer fünf minuten' (de) -> Timer(300s)`. Den Test-Timer brichst du mit `--es text "timer aus"` wieder ab.

---

## Was der Installer für dich erledigt

Die Sprachbefehle richtet die Phase `voice` ein (Kapitel 5). Für die Befehle:

1. Lädt er das kleine Vosk-Modell deiner Sprache: `vosk-model-small-de-0.15` oder `vosk-model-small-en-us-0.15`.
2. Prüft er die SHA-256-Prüfsumme, entpackt das Modell und kopiert den Ordner als `vosk-de` oder `vosk-en` auf den Spot (etwa 50 MB). Liegt das Modell schon dort, überspringt er diesen Schritt. `--force` kopiert es neu.
3. Schickt er `SET_LANG`, damit Spot Voice das Modell lädt.

Spotify installiert die Phase `apps`, wenn du Ja gesagt hast (oder mit `--spotify`). Der Probelauf zeigt die Vosk-Schritte (`…` steht für den Projektordner, den der Installer vollständig ausgibt):

```text
==> Offline-Sprachmodell für Sprachbefehle (Vosk, de)
[..] Lade vosk-model-small-de-0.15.zip ...
+ curl -fL --retry 3 -o downloads/vosk-model-small-de-0.15.zip https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip
+ shasum -a 256 downloads/vosk-model-small-de-0.15.zip
+ unzip -q -o …/downloads/vosk-model-small-de-0.15.zip -d …/downloads
[..] Kopiere das Modell auf den Spot (ca. 50 MB) ...
+ adb shell rm -rf /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-de
+ adb push …/downloads/vosk-model-small-de-0.15 /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-de
[ok] downloads/vosk-model-small-de-0.15/ -> /sdcard/Android/data/ai.pocket.heyclaude/files/vosk-de/
```

---

## Prüfen

1. Starte die Prüfungen:

   ```bash
   ./install.sh verify
   ```

   Die Zeile `Vosk-Sprachmodell auf dem Spot (vosk-de)` (oder `vosk-en`) muss ein ✓ zeigen.
2. In der App Spot Voice steht in der Statuszeile `Sprachbefehle (vosk-de): ja`.
3. Sag „Hey Claude“, warte auf den Piepton und sag „Timer eine Minute“. Du hörst einen Bestätigungston, und am Rand der Uhr erscheint ein oranger Bogen. Nach einer Minute klingelt es. Tippe auf den Bildschirm, um es zu stoppen.
4. Ohne Sprechen: Nimm den Broadcast `COMMAND` aus den Tipps oben und lies das Log.

---

## Wenn etwas schiefgeht

| Problem | Siehe |
|---|---|
| Kein Piepton nach dem Wake-Word, oder Befehle werden nie erkannt | [Sprachbefehle werden falsch verstanden](09-troubleshooting.md#voice-commands-are-not-recognised) |
| Ein Sender wird nicht gefunden, oder der falsche spielt | [Radiosender nicht gefunden](09-troubleshooting.md#radio-station-not-found) |
| „spiel …“ tut nichts, oder Spotify startet nicht | [Spotify spielt nicht](09-troubleshooting.md#spotify-does-not-play) |
| Das Wake-Word selbst reagiert nicht | [Wake-Word wird nicht erkannt](09-troubleshooting.md#the-wake-word-does-not-react) |

---
[← 5 · Sprachassistent: Claude oder ChatGPT](05-voice-assistant.md) · [Übersicht](../../README.de.md) · [7 · Die Uhr →](07-clock.md)
