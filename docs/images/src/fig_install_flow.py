from common import *  # noqa: F401,F403

STRINGS = {
    "en": {
        "title": "./install.sh: the nine phases",
        "desc": "Flow of the nine phases of ./install.sh. doctor checks the tools on your computer. Group "
                "Unlock and ROM, done once with the USB cable: unlock (you download the amonet zip and do the "
                "three-button trick), backup, flash. Group Android setup: setup (you enable developer "
                "options), apps (you log in). Group Spot Second Life, repeatable and over Wi-Fi: voice, "
                "clock, verify. Phases marked with a hand need you to do something.",
        "h": "One script, nine phases",
        "sub": "./install.sh runs them in order and skips phases that are already done.",
        "legend_hand": "needs your hands: download, buttons, taps",
        "legend_dry": "prints every command, touches nothing",
        "groups": [
            ("Unlock & ROM", "once · needs the USB cable", "usb"),
            ("Android setup", "once · on the Spot's screen", "gear"),
            ("Spot Second Life", "repeatable · works over Wi-Fi", "wifi"),
        ],
        "doctor": "Checks the tools on your computer",
        "phases": [
            ("unlock", "Download amonet zip, button trick", True),
            ("backup", "Copies every partition to backups/", False),
            ("flash", "Wipes and installs LineageOS 18.1", False),
            ("setup", "Wizard, Wi-Fi, developer options", True),
            ("apps", "Claude or ChatGPT, then log in", True),
            ("voice", "Spot Voice, wake-word models, Vosk", False),
            ("clock", "Spot Clock as home screen", False),
            ("verify", "Checks everything, live wake test", False),
        ],
        "login": ["Claude login: send the e-mail link", "to the Spot with"],
        "foot1": " re-runs single phases",
        "foot2": " shows what is done",
    },
    "de": {
        "title": "./install.sh: die neun Phasen",
        "desc": "Ablauf der neun Phasen von ./install.sh. doctor prüft die Tools auf deinem Rechner. "
                "Gruppe Entsperren und ROM, einmalig mit USB-Kabel: unlock (du lädst das amonet-Zip und "
                "machst den Drei-Tasten-Trick), backup, flash. Gruppe Android einrichten: setup (du schaltest "
                "die Entwickleroptionen ein), apps (du meldest dich an). Gruppe Spot Second Life, wiederholbar "
                "und über WLAN: voice, clock, verify. Phasen mit Hand brauchen dich.",
        "h": "Ein Skript, neun Phasen",
        "sub": "./install.sh führt sie der Reihe nach aus und überspringt erledigte Phasen.",
        "legend_hand": "hier bist du gefragt: Download, Tasten, Tippen",
        "legend_dry": "zeigt jeden Befehl, ändert nichts",
        "groups": [
            ("Entsperren & ROM", "einmalig · braucht das USB-Kabel", "usb"),
            ("Android einrichten", "einmalig · am Display des Spot", "gear"),
            ("Spot Second Life", "wiederholbar · läuft über WLAN", "wifi"),
        ],
        "doctor": "Prüft die Tools auf deinem Rechner",
        "phases": [
            ("unlock", "amonet-Zip laden, Tastentrick", True),
            ("backup", "Sichert jede Partition in backups/", False),
            ("flash", "Löscht, installiert LineageOS 18.1", False),
            ("setup", "Assistent, WLAN, Entwickleroptionen", True),
            ("apps", "Claude oder ChatGPT + Anmeldung", True),
            ("voice", "Spot Voice, Wakeword-Modelle, Vosk", False),
            ("clock", "Spot Clock als Startbildschirm", False),
            ("verify", "Prüft alles, Live-Test Wakeword", False),
        ],
        "login": ["Claude-Login: den Link aus der Mail", "schickst du so an den Spot:"],
        "foot1": " führt einzelne Phasen erneut aus",
        "foot2": " zeigt, was erledigt ist",
    },
}

COLS = [48, 496, 944]
CW = 408
PANEL_Y, PANEL_H = 262, 420
CARD_H, CARD_GAP = 88, 16


def hand_badge(x, y):
    return "\n".join([
        circle(x, y, 19, fill=ACC_S, stroke=ACC_T, sw=2),
        icon("hand", x, y, 24, color=ACCENT, sw=2),
    ])


def card(x, y, w, n, name, desc, hand, hl=False):
    o = [rrect(x, y, w, CARD_H, 16, fill=WHITE, stroke=ACC_T if hand else LINE, sw=2)]
    o.append(badge(x + 34, y + CARD_H / 2, n, r=17, fill=DARK, color=IVORY, size=17))
    o.append(text(x + 66, y + 39, name, size=20, cls="mono", weight="700", box=(x + 66, y + 16, w - 120, 30)))
    o.append(text(x + 66, y + 66, desc, size=16, fill=INK2, box=(x + 66, y + 50, w - 66 - 12, 24)))
    if hand:
        o.append(hand_badge(x + w - 30, y + 30))
    return "\n".join(o)


def build(lang):
    L = STRINGS[lang]
    s = Svg(1400, 800, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 78, L["h"], size=34, cls="serif"))
    s.add(text(56, 112, L["sub"], size=18, fill=INK2, box=(56, 92, 1000, 28)))
    # the command
    s.add(rrect(1144, 50, 208, 52, 14, fill=DARK))
    s.add(text(1168, 83, "$", size=20, cls="mono", fill=ACCENT, weight="700"))
    s.add(text(1192, 83, "./install.sh", size=20, cls="mono", fill=IVORY, weight="700"))

    # doctor, above the first group
    dx = COLS[0] + 16
    s.add(card(dx, 146, CW - 32, 1, "doctor", L["doctor"], False))
    s.add(arrow([(COLS[0] + CW / 2, 146 + CARD_H + 2), (COLS[0] + CW / 2, PANEL_Y - 4)], color=GRAY, sw=2.5,
                head=9))

    # legend, above groups two and three
    lx, ly = COLS[1] + 16, 146
    s.add(rrect(lx, ly, COLS[2] + CW - 16 - lx, CARD_H, 16, fill=PANEL))
    s.add(hand_badge(lx + 36, ly + 30))
    s.add(text(lx + 68, ly + 36, L["legend_hand"], size=17, box=(lx + 68, ly + 16, 780, 26)))
    s.add(text(lx + 18, ly + 70, "--dry-run", size=17, cls="mono", weight="700", fill=DARK))
    s.add(text(lx + 128, ly + 70, L["legend_dry"], size=17, box=(lx + 128, ly + 50, 700, 26)))

    # groups
    n = 2
    pi = 0
    counts = [3, 2, 3]
    for gi, (gname, gnote, gicon) in enumerate(L["groups"]):
        x = COLS[gi]
        last = gi == 2
        s.add(rrect(x, PANEL_Y, CW, PANEL_H, 22, fill=ACC_S if last else PANEL,
                    stroke=ACC_T if last else None, sw=2))
        s.add(text(x + 24, PANEL_Y + 44, gname, size=25, cls="serif", box=(x + 24, PANEL_Y + 18, CW - 80, 34)))
        s.add(text(x + 24, PANEL_Y + 72, gnote, size=16, fill=INK2, weight="500",
                   box=(x + 24, PANEL_Y + 56, CW - 48, 24)))
        s.add(icon(gicon, x + CW - 38, PANEL_Y + 40, 26, color=ACCENT if last else INK2, sw=2))
        y = PANEL_Y + 100
        for j in range(counts[gi]):
            name, desc, hand = L["phases"][pi]
            if j:
                s.add(line([(x + 50, y - CARD_GAP - 2), (x + 50, y + 2)], color=GRAY, sw=2))
            s.add(card(x + 16, y, CW - 32, n, name, desc, hand))
            y += CARD_H + CARD_GAP
            n += 1
            pi += 1
        if gi == 1:
            ny = y
            nh = PANEL_Y + PANEL_H - 16 - ny
            s.add(rrect(x + 16, ny, CW - 32, nh, 16, fill="none", stroke=GRAY, sw=2, dash="2 6"))
            s.add(icon("hand", x + 46, ny + 26, 22, color=INK2, sw=2))
            s.add(text(x + 70, ny + 31, L["login"], size=16, fill=INK2, lh=22,
                       box=(x + 70, ny + 12, CW - 32 - 70, 48)))
            s.add(text(x + 70, ny + 80, "./install.sh open-link", size=17, cls="mono", weight="700",
                       box=(x + 70, ny + 62, CW - 32 - 70, 26)))
        if gi < 2:
            my = PANEL_Y + PANEL_H / 2
            s.add(arrow([(x + CW + 6, my), (COLS[gi + 1] - 6, my)], color=GRAY, sw=2.5, head=10))

    # footer: running single phases
    fy = 740
    s.add('<text x="56" y="%d" class="sans" font-size="17" fill="%s"><tspan class="mono" fill="%s" '
          'font-weight="700">./install.sh voice clock</tspan><tspan dx="10">%s</tspan></text>'
          % (fy, INK2, DARK, esc(L["foot1"].strip())))
    s.add('<text x="%d" y="%d" class="sans" font-size="17" fill="%s"><tspan class="mono" fill="%s" '
          'font-weight="700">./install.sh status</tspan><tspan dx="10">%s</tspan></text>'
          % (COLS[1] + 140, fy, INK2, DARK, esc(L["foot2"].strip())))
    return s.render()
