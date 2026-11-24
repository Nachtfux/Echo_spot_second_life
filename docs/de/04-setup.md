<sub>[English](../en/04-setup.md) · **Deutsch**</sub>

# 4 · Android einrichten

> **Ziel:** den ersten Start von LineageOS abschließen, die Entwickleroptionen einschalten und den Installer Sprache, Zeitzone, Anzeige und Aurora Store einrichten lassen · **Zeit:** etwa 20 Minuten · **Installer:** `./install.sh setup`

LineageOS ist installiert und zeigt seinen Einrichtungsassistenten ([Kapitel 3](03-lineageos.md)). In diesem
Kapitel tippst du dich selbst durch ein paar Bildschirme auf dem Spot, bis dein Computer mit ihm sprechen darf. Dann
übernimmt der Installer.

Ein paar Dinge helfen auf dem kleinen runden Display:

- Das Display hat 480 × 480 Pixel und ist rund. Die Ecken von Menüs sind also abgeschnitten. Scroll ein wenig, wenn
  etwas verdeckt ist.
- Die **mittlere Taste oben** arbeitet unter LineageOS als Ein/Aus-Taste: Ein kurzer Druck schaltet das Display ein
  oder aus.
- Lass USB-Kabel und Netzteil angeschlossen.

> [!TIP]
> Du kannst `./install.sh setup` gleich jetzt starten. Er zeigt dieselben Schritte wie unten auf deinem Computer an
> und wartet bis zu 30 Minuten, bis der Spot mit USB-Debugging auftaucht.

## Schritt 1: Der Einrichtungsassistent

1. Warte, bis der Assistent erscheint. Der erste Start dauert etwa 5 Minuten.
2. **Sprache:** Wähl irgendeine Sprache. Der Installer stellt später Deutsch oder Englisch ein, je nach `--lang`.
3. **WLAN:** Wähl dein Netzwerk und gib das Passwort ein.
4. **Datum und Uhrzeit:** Übernimm, was angezeigt wird. Die Zeitzone stellt der Installer später ein.
5. Überspring alles Optionale. Bietet der Assistent eine Displaysperre an (PIN, Muster, Passwort), überspring sie.
   Warum, steht unter [Displaysperre auf „Keine“ stellen](#displaysperre-auf-keine-stellen).
6. Am Ende siehst du den Startbildschirm von LineageOS.

> [!TIP]
> Ein langes WLAN-Passwort auf der winzigen Tastatur einzutippen ist mühsam. Du kannst das WLAN im Assistenten
> überspringen, die Schritte 2 und 3 unten erledigen und das Netzwerk dann mit dem Android-11-Befehl
> `adb shell cmd wifi connect-network "<Netzwerkname>" wpa2 "<Passwort>"` von deinem Computer aus hinzufügen.
> Bedenke, dass das Passwort dann in deinem Shell-Verlauf landet. Am Spot selbst wurde dieser Weg nicht ausprobiert.

## Schritt 2: Die Entwickleroptionen einschalten

Android versteckt die Entwickleroptionen, bis du sie freischaltest. Die Menünamen unten sind die der deutschen
Oberfläche von LineageOS 18.1; dein Spot sagt vielleicht „Über das Telefon“ statt „Über das Tablet“.

1. Wisch auf dem Startbildschirm nach oben, um die App-Liste zu öffnen, und öffne **Einstellungen**.
2. Scroll nach unten und öffne **Über das Tablet**.
3. Scroll ganz nach unten und tipp siebenmal auf **Build-Nummer**. Nach ein paar Tippern zählt eine Meldung
   herunter. Am Ende steht da **„Du bist jetzt Entwickler!“**
4. Geh zurück zu **Einstellungen → System → Erweitert → Entwickleroptionen**. („Erweitert“ ist eventuell
   zugeklappt; tipp darauf, um es aufzuklappen.)
5. Schalt **Android-Debugging** ein. (Andere Android-Systeme nennen es „USB-Debugging“.) Bestätige mit **OK**.
6. Schalt **Debugging als Root** ein (englisch „Rooted debugging“). Der Installer braucht es, um Sprache und
   Zeitzone einzustellen.
7. Lass **ADB über Netzwerk** (englisch „ADB over network“) vorerst aus. Mehr dazu unter
   [adb per WLAN](#adb-per-wlan).

> [!NOTE]
> Die deutschen Menünamen auf dem Spot können je nach Übersetzung leicht abweichen. Im Zweifel helfen die
> englischen Namen in Klammern oder die [englische Fassung dieses Kapitels](../en/04-setup.md).

## Schritt 3: Deinen Computer zulassen

1. Achte darauf, dass das USB-Kabel den versteckten Anschluss mit deinem Computer verbindet.
2. Auf dem Spot erscheint ein Dialog: **„USB-Debugging zulassen?“** mit dem Fingerabdruck des RSA-Schlüssels deines
   Computers.
3. Setz den Haken bei **„Von diesem Computer immer zulassen“**.
4. Tipp auf **Zulassen**.

Prüf am Computer:

```bash
adb devices
```

Der Spot muss mit dem Status `device` in der Liste stehen:

```
List of devices attached
XXXXXXXXXXXXXXXX	device
```

Steht dort `unauthorized`, wurde der Dialog übersehen oder abgelehnt. Zieh das USB-Kabel ab und steck es wieder
ein; dann kommt der Dialog noch einmal. Erscheint gar kein Dialog, schalt **Android-Debugging** aus und wieder ein.

## Schritt 4: Den Installer die Einrichtung abschließen lassen

```bash
./install.sh setup
```

Hast du die Sprache noch nicht festgelegt, fragt der Installer zuerst danach (`de` oder `en`). Unterwegs stellt er
dann zwei Fragen:

1. **Zeitzone.** Vorgabe ist die Zeitzone deines Computers. Drück Enter, um sie zu übernehmen, oder gib eine im
   Format `Region/Stadt` ein, zum Beispiel `Europe/Berlin` oder `America/New_York`.
2. **adb über WLAN.** Antworte mit `J` (die Vorgabe), wenn du das USB-Kabel danach abziehen willst.

Währenddessen wird das Display des Spot kurz schwarz: Die Android-Oberfläche startet mit der neuen Sprache neu.

Die Ausgabe sieht so aus (gekürzt):

```
==> Phase 5/9: setup - Ersteinrichtung, Entwickleroptionen, Sprache, Aurora Store
     Mehr Hilfe: docs/de/04-setup.md
[..] Warte auf den Spot mit USB-Debugging (bis zu 30 Minuten) ...
[ok] rook, LineageOS 18.1-20251108-UNOFFICIAL-rook

==> Sprache, Zeitzone, Anzeige
?? Zeitzone des Spot [Europe/Berlin]:
[..] Sprache de-DE, Zeitzone Europe/Berlin
[..] Warte 6 s, damit die Einstellungen gespeichert sind, dann startet die Android-Oberfläche neu (Display kurz schwarz) ...
[ok] Android-Oberfläche ist wieder da
[..] Anzeigedichte 120 (macht Handy-Apps auf dem runden 480-px-Display nutzbar), Bildschirm aus nach 60 s

==> Aurora Store (App-Store ohne Google-Konto)
[..] Lade com.aurora.store_76.apk ...
[ok] sha256 geprüft: downloads/com.aurora.store_76.apk
[ok] Signatur von Aurora Store geprüft
[..] Installiere downloads/com.aurora.store_76.apk (com.aurora.store) ...
[ok] com.aurora.store installiert

==> Bildschirmsperre
Empfohlen - sonst kann das Wake-Word den Assistenten nicht öffnen, solange der Spot gesperrt ist:
    1. Auf dem Spot: Einstellungen > Sicherheit > Displaysperre.
    2. "Keine" wählen.

==> adb über WLAN
?? Ab jetzt adb über WLAN nutzen (dann kann das USB-Kabel ab)? [J/n]
[ok] adb über WLAN: 192.168.x.y:5555 (gespeichert). Das USB-Kabel kann jetzt ab - das Netzteil bleibt dran.
     Nach einem Neustart des Spot ist das wieder aus; Kabel anstecken und einen beliebigen ./install.sh-Befehl starten.
[ok] Phase "setup" erledigt.
```

## Was der Installer für dich erledigt

`./install.sh setup` (Phase 5 von 9):

| Was | Wie | Warum |
|---|---|---|
| Prüft den Spot | `getprop ro.product.device` muss `rook` sein, `ro.lineage.version` muss existieren | Stellt sicher, dass LineageOS aus Kapitel 3 auf dem richtigen Gerät läuft. |
| Root-Zugriff für adb | `adb root` | Sprache und Zeitzone sind Systemeinstellungen. Dafür braucht es „Debugging als Root“. |
| Zeitzone | `settings put global auto_time_zone 0`, `setprop persist.sys.timezone <Region/Stadt>` | Uhr, Wecker und Wetter brauchen die richtige Ortszeit. |
| Sprache | `settings put system system_locales de-DE` (oder `en-US`), `setprop persist.sys.locale …`, 6 s warten, `setprop ctl.restart zygote` | Android übernimmt die Sprache erst, wenn seine Oberfläche startet. Deshalb wartet der Installer, bis die Einstellungen gespeichert sind, und startet sie dann neu. |
| Anzeigedichte | `wm density 120` | Apps wie Claude und ChatGPT sind für Handydisplays gebaut. Bei Dichte 120 ist das 480 Pixel breite Display des Spot 640 dp breit (480 × 160 / 120). So zeigen diese Apps ihr volles Layout statt einer gequetschten Ansicht. Die Schrift wird dabei kleiner. |
| Bildschirm-Timeout | `settings put system screen_off_timeout 60000` | Das Display geht nach 60 Sekunden ohne Berührung aus. In [Kapitel 7](07-clock.md) wird die Uhr zum Bildschirmschoner. |
| Aurora Store | lädt `com.aurora.store_76.apk` von F-Droid, prüft SHA-256 `fd9c75d9…2c4d11` und, wenn `apksigner` vorhanden ist, das Signaturzertifikat `5c83c767…15f532`, und installiert die App dann | Aurora Store installiert Apps aus Google Play ohne Google-Konto und ohne Google-Dienste. Du brauchst ihn in [Kapitel 5](05-voice-assistant.md). |
| Installationsrecht für Aurora | `appops set com.aurora.store REQUEST_INSTALL_PACKAGES allow` | Aurora kann Apps installieren, ohne dass erst der Bildschirm „Apps aus dieser Quelle zulassen“ kommt. |
| Displaysperre | zeigt die Schritte unten | Das Wake-Word muss den Assistenten öffnen können. |
| adb über WLAN | liest die WLAN-Adresse des Spot, `adb tcpip 5555`, `adb connect <ip>:5555`, speichert die Adresse als `DEVICE` in `.state/config.env` | Alle späteren Phasen laufen ohne Kabel. |

Ist `apksigner` nicht installiert (siehe [Kapitel 1](01-hardware.md#dein-computer)), entfällt die
Signaturprüfung. Die SHA-256-Prüfung läuft immer.

## Displaysperre auf „Keine“ stellen

1. Öffne auf dem Spot **Einstellungen → Sicherheit → Displaysperre**.
2. Wähl **Keine**.

Warum: Wenn du das Wake-Word sagst, weckt Spot Voice das Display und öffnet Claude oder ChatGPT. Mit PIN oder Muster
würde sich der Assistent hinter dem Sperrbildschirm öffnen, und nichts passiert, bis jemand entsperrt.

> [!NOTE]
> Ohne Displaysperre kann jeder im Raum den Spot benutzen, auch die Apps, in denen du angemeldet bist. Für ein Gerät
> im Küchenregal ist das meist in Ordnung. Entscheide selbst, was zu deinem Zuhause passt.

## adb per WLAN

Nach der Einrichtung spricht der Installer über dein WLAN mit dem Spot, unter einer Adresse wie `192.168.x.y:5555`.
Das USB-Kabel kannst du abziehen; das Netzteil bleibt dran.

**Nach einem Neustart des Spot ist adb über WLAN wieder aus.** So arbeitet Android. Merken musst du dir das nicht:
Der nächste `./install.sh`-Befehl bemerkt, dass der Spot nicht antwortet, bittet dich einmal, das USB-Kabel
einzustecken, und bietet an, adb über WLAN wieder einzuschalten. Dasselbe passiert, wenn dein Router dem Spot eine
neue Adresse gegeben hat.

```
[!!] Der Spot antwortet nicht unter 192.168.x.y:5555.
     adb über WLAN schaltet sich bei jedem Neustart des Spot ab (oder die IP-Adresse hat sich geändert). Einmal per Kabel:
```

Zwei Wege, wie du ohne Kabel auskommst:

- In den **Entwickleroptionen** hat LineageOS einen Schalter **ADB über Netzwerk**. Ist er an, wartet der Spot auch
  ohne den Schritt `adb tcpip` im Netzwerk auf adb. Nutz das nur in einem Heimnetz, dem du vertraust.
- Gib dem Spot in deinem Router eine feste Adresse (oft „DHCP-Reservierung“ oder „immer dieselbe IP-Adresse
  zuweisen“ genannt). Dann bleibt die gespeicherte Adresse gültig.

Von Hand verbinden, zum Beispiel für eigene `adb`-Befehle:

```bash
adb connect 192.168.x.y:5555
```

Ersetze `192.168.x.y` durch die Adresse, die `./install.sh status` unter „Gerät“ anzeigt.

## Prüfen

1. `./install.sh status` zeigt einen Haken bei `setup` und unter „Gerät“ die Adresse des Spot,
   `192.168.x.y:5555`.
2. Am Computer:

   ```bash
   adb devices -l
   ```

   listet den Spot mit seiner WLAN-Adresse und dem Status `device` auf.
3. Auf dem Spot: Die Menüs sind in deiner Sprache, alles wirkt kleiner, und **Aurora Store** steht in der App-Liste.
4. Einstellungen → Sicherheit → Displaysperre zeigt **Keine**.

## Wenn etwas schiefgeht

| Meldung oder Problem | Was du tust |
|---|---|
| `Der Spot ist nicht mit USB-Debugging aufgetaucht.` | Geh die Schritte 2 und 3 noch einmal durch. Siehe [Fehlerbehebung: adb zeigt unauthorized oder gar nichts](09-troubleshooting.md#adb-shows-unauthorized-or-no-device). |
| `adb devices` zeigt `unauthorized` | Zieh das USB-Kabel ab, steck es wieder ein und lass den Dialog auf dem Spot zu. |
| `Das ist kein LineageOS (kein ro.lineage.version).` | Das Flashen ist nicht durchgelaufen. Geh zurück zu [Kapitel 3](03-lineageos.md) und starte `./install.sh flash`. |
| `adb konnte nicht auf root umschalten` oder `Sprache und Zeitzone brauchen "Debugging als Root"` | Schalt in den Entwickleroptionen **Debugging als Root** ein und starte `./install.sh setup` noch einmal. |
| `Android braucht lange - mache trotzdem weiter.` | Warte eine Minute. Bleibt das Display schwarz, starte den Spot neu (Netzteil kurz abziehen) und führ `./install.sh setup` noch einmal aus. |
| `Signatur von Aurora Store falsch` | Installier die Datei nicht. Lösch `downloads/com.aurora.store_76.apk` und starte `./install.sh setup` noch einmal. Passiert es wieder, eröffne bitte ein Issue. |
| `Der Spot hat noch keine WLAN-Adresse.` | Verbinde den Spot mit dem WLAN: Einstellungen → Netzwerk & Internet → WLAN. Starte dann `./install.sh setup` noch einmal. |
| `adb über WLAN antwortet nicht - bleibe bei USB.` | Ist dein Computer im selben Netz? Gastnetze verhindern oft, dass sich Geräte gegenseitig sehen. Du kannst auch bei USB bleiben. |
| Später: `Der Spot antwortet nicht unter 192.168.x.y:5555.` | Steck das USB-Kabel ein und folge dem Installer. Siehe [Fehlerbehebung: adb per WLAN ist nach einem Neustart weg](09-troubleshooting.md#adb-over-wi-fi-is-gone-after-a-reboot). |

---
[← 3 · LineageOS: Sicherung und Installation](03-lineageos.md) · [Übersicht](../../README.de.md) · [5 · Sprachassistent: Claude oder ChatGPT →](05-voice-assistant.md)
