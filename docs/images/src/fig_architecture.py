from common import *  # noqa: F401,F403

STRINGS = {
    "en": {
        "title": "How Spot Voice works",
        "desc": "Architecture. Microphone, then SilenceGate, which skips the neural networks in a quiet room, "
                "then openWakeWord on the Spot: melspectrogram.onnx, embedding_model.onnx and two classifiers "
                "sharing the embeddings, wake (Hey Claude, Hey ChatGPT) and stop (Danke Claude, Danke "
                "ChatGPT). After the wake word, Vosk offline speech recognition (German or English) and a "
                "command parser run local commands: Spotify, internet radio from radio-browser.info, timer "
                "and alarm on Spot Clock. Without a command, the accessibility service opens Claude or "
                "ChatGPT and taps its voice-mode button; only this voice mode runs in the cloud. EchoGuard "
                "watches the speaker output, mutes the assistant's microphone while it talks and ends the "
                "conversation after 10 s of silence, the stop word or the wake word. Spot Clock receives the "
                "STATE broadcast (listening or active) and shows now playing, timers and weather.",
        "h": "How Spot Voice works",
        "sub": "Everything runs offline on the Spot – only the assistant's voice mode uses the cloud.",
        "lg_dev": "on the Spot, offline", "lg_cloud": "cloud",
        "mic": "Microphone",
        "sg": ["skips the neural nets", "in a quiet room"],
        "oww_note": "two classifiers share one embedding",
        "mel": "Mel spectrogram", "emb": "Embedding",
        "stop": "Stop classifier", "stopq": "“Danke Claude” · “Danke ChatGPT”",
        "wake": "Wake classifier", "wakeq": "“Hey Claude” · “Hey ChatGPT”",
        "wake_arrow": "wake word",
        "vosk": ["offline speech recognition", "German or English"],
        "parser": "Command parser", "parser2": ["play, radio, timer,", "alarm, louder …"],
        "cmd": "command", "nocmd": "no command",
        "a11y": "Accessibility service", "a11y2": ["opens Claude or ChatGPT,", "taps the voice-mode button"],
        "vm": "Voice mode", "vm2": ["Claude or ChatGPT,", "speech to speech"], "cloud": "cloud",
        "link": "mutes · ends",
        "eg": [["watches the speaker output", "(Visualizer)"],
               ["mutes the assistant's mic", "while it talks"],
               ["ends after 10 s silence,", "the stop word or the wake word"]],
        "spotify2": "“play Queen”", "radio": "Internet radio", "radio2": "radio-browser.info",
        "timer": "Timer", "timer2": "“timer 5 minutes”", "alarm": "Alarm", "alarm2": "“wake me at 7”",
        "clock2": "shows now playing, timers, alarms, weather and the assistant state",
        "state": "STATE broadcast: listening / active",
    },
    "de": {
        "title": "So funktioniert Spot Voice",
        "desc": "Architektur. Mikrofon, dann SilenceGate, das die neuronalen Netze im stillen Raum "
                "überspringt, dann openWakeWord auf dem Spot: melspectrogram.onnx, embedding_model.onnx "
                "und zwei Klassifikatoren mit denselben Embeddings, Wake (Hey Claude, Hey ChatGPT) und Stopp "
                "(Danke Claude, Danke ChatGPT). Nach dem Wakeword erkennen Vosk (offline, Deutsch oder "
                "Englisch) und ein Befehlsparser lokale Befehle: Spotify, Internetradio über "
                "radio-browser.info, Timer und Wecker auf der Spot-Uhr. Ohne Befehl öffnet der "
                "Bedienungshilfe-Dienst Claude oder ChatGPT und tippt auf die Sprachmodus-Taste; nur dieser "
                "Sprachmodus läuft in der Cloud. EchoGuard beobachtet den Lautsprecher, stummt das Mikrofon "
                "des Assistenten, solange er spricht, und beendet das Gespräch nach 10 s Stille, mit dem "
                "Stoppwort oder dem Wakeword. Die Spot-Uhr empfängt den STATE-Broadcast (listening oder "
                "active) und zeigt Now Playing, Timer und Wetter.",
        "h": "So funktioniert Spot Voice",
        "sub": "Alles läuft offline auf dem Spot – nur der Sprachmodus des Assistenten nutzt die Cloud.",
        "lg_dev": "auf dem Spot, offline", "lg_cloud": "Cloud",
        "mic": "Mikrofon",
        "sg": ["spart die neuronalen", "Netze im stillen Raum"],
        "oww_note": "zwei Klassifikatoren, ein Embedding",
        "mel": "Mel-Spektrogramm", "emb": "Embedding",
        "stop": "Stopp-Klassifikator", "stopq": "„Danke Claude“ · „Danke ChatGPT“",
        "wake": "Wake-Klassifikator", "wakeq": "„Hey Claude“ · „Hey ChatGPT“",
        "wake_arrow": "Wakeword",
        "vosk": ["Offline-Spracherkennung", "Deutsch oder Englisch"],
        "parser": "Befehlsparser", "parser2": ["spiel, Radio, Timer,", "Wecker, lauter …"],
        "cmd": "Befehl", "nocmd": "kein Befehl",
        "a11y": "Bedienungshilfe-Dienst", "a11y2": ["öffnet Claude oder ChatGPT,", "tippt auf den Sprachmodus"],
        "vm": "Sprachmodus", "vm2": ["Claude oder ChatGPT,", "Sprache zu Sprache"], "cloud": "Cloud",
        "link": "stumm · beenden",
        "eg": [["beobachtet den Lautsprecher", "(Visualizer)"],
               ["stummt das Mikro des Assistenten,", "solange er spricht"],
               ["beendet nach 10 s Stille,", "mit Stoppwort oder Wakeword"]],
        "spotify2": "„spiel Queen“", "radio": "Internetradio", "radio2": "radio-browser.info",
        "timer": "Timer", "timer2": "„Timer 5 Minuten“", "alarm": "Wecker", "alarm2": "„weck mich um 7“",
        "clock2": "zeigt Now Playing, Timer, Wecker, Wetter und den Status des Assistenten",
        "state": "STATE-Broadcast: listening / active",
    },
}


def box(x, y, w, h, fill=WHITE, stroke=LINE, r=16, dash=None):
    return rrect(x, y, w, h, r, fill=fill, stroke=stroke, sw=2, dash=dash)


def build(lang):
    L = STRINGS[lang]
    s = Svg(1400, 900, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 78, L["h"], size=34, cls="serif"))
    s.add(text(56, 112, L["sub"], size=18, fill=INK2, box=(56, 92, 900, 28)))
    # legend
    s.add(box(1016, 63, 26, 20, r=6))
    s.add(text(1052, 79, L["lg_dev"], size=16, fill=INK2, box=(1052, 60, 180, 26)))
    s.add(box(1250, 63, 26, 20, fill=ACC_S, stroke=ACCENT, r=6, dash="4 4"))
    s.add(text(1286, 79, L["lg_cloud"], size=16, fill=INK2, box=(1286, 60, 70, 26)))

    # ---------------------------------------------------------- row 1
    s.add(circle(104, 262, 38, fill=WHITE, stroke=LINE, sw=2))
    s.add(icon("mic", 104, 262, 32, color=DARK, sw=2.2))
    s.add(text(104, 326, L["mic"], size=16, fill=INK2, anchor="middle", weight="500", box=(40, 308, 128, 24)))
    s.add(arrow([(144, 262), (174, 262)], color=GRAY, sw=2.5, head=9))

    s.add(box(178, 207, 222, 110))
    s.add(text(200, 244, "SilenceGate", size=19, weight="700", box=(200, 224, 186, 28)))
    s.add(text(200, 271, L["sg"], size=16, fill=INK2, lh=22, box=(200, 255, 190, 50)))
    s.add(arrow([(402, 262), (432, 262)], color=GRAY, sw=2.5, head=9))

    gx, gy, gw, gh = 436, 150, 908, 214
    s.add(rrect(gx, gy, gw, gh, 22, fill=PANEL))
    s.add(text(gx + 24, gy + 36, "openWakeWord", size=22, cls="serif"))
    s.add(text(gx + gw - 24, gy + 34, L["oww_note"], size=16, fill=INK2, anchor="end", style="italic",
               box=(gx + gw - 380, gy + 16, 356, 24)))
    for bx, bw, title, fn in ((460, 222, L["mel"], "melspectrogram.onnx"), (708, 240, L["emb"], "embedding_model.onnx")):
        s.add(box(bx, 207, bw, 110))
        s.add(text(bx + bw / 2, 248, title, size=18, weight="700", anchor="middle", box=(bx + 10, 228, bw - 20, 28)))
        s.add(text(bx + bw / 2, 280, fn, size=16, cls="mono", fill=INK2, anchor="middle",
                   box=(bx + 8, 263, bw - 16, 24)))
    s.add(arrow([(682, 262), (706, 262)], color=GRAY, sw=2.5, head=8))
    # fork into the two classifiers
    s.add(line([(950, 262), (970, 262)], color=GRAY, sw=2.5))
    s.add(arrow([(970, 262), (970, 224), (994, 224)], color=GRAY, sw=2.5, head=8, corner=10))
    s.add(arrow([(970, 262), (970, 300), (994, 300)], color=GRAY, sw=2.5, head=8, corner=10))
    cx0, cw = 998, 322
    s.add(box(cx0, 194, cw, 62, r=14))
    s.add(text(cx0 + 18, 220, L["stop"], size=17, weight="700", box=(cx0 + 18, 202, cw - 30, 24)))
    s.add(text(cx0 + 18, 244, L["stopq"], size=16, fill=INK2, box=(cx0 + 18, 228, cw - 30, 24)))
    s.add(box(cx0, 270, cw, 62, fill=WHITE, stroke=ACCENT, r=14))
    s.add(text(cx0 + 18, 296, L["wake"], size=17, weight="700", box=(cx0 + 18, 278, cw - 30, 24)))
    s.add(text(cx0 + 18, 320, L["wakeq"], size=16, fill=INK2, box=(cx0 + 18, 304, cw - 30, 24)))

    # ---------------------------------------------------------- row 2 (right to left)
    s.add(arrow([(1159, 333), (1159, 418)], color=ACCENT, sw=2.5, head=10))
    s.add(text(1173, 398, L["wake_arrow"], size=16, fill=ACC_D, weight="600", box=(1173, 380, 160, 24)))
    vx = 1004
    s.add(box(vx, 422, 340, 108))
    s.add(text(vx + 22, 458, "Vosk", size=19, weight="700"))
    s.add(text(vx + 22, 484, L["vosk"], size=16, fill=INK2, lh=22, box=(vx + 22, 468, 300, 50)))
    s.add(arrow([(vx - 2, 476), (968, 476)], color=GRAY, sw=2.5, head=9))
    px = 744
    s.add(box(px, 422, 220, 108))
    s.add(text(px + 20, 458, L["parser"], size=19, weight="700", box=(px + 20, 438, 186, 28)))
    s.add(text(px + 20, 484, L["parser2"], size=16, fill=INK2, lh=22, box=(px + 20, 468, 186, 50)))
    # no command -> accessibility
    s.add(arrow([(px - 2, 476), (638, 476)], color=GRAY, sw=2.5, head=9))
    s.add(text(691, 464, L["nocmd"], size=16, fill=INK2, anchor="middle", box=(638, 446, 106, 24)))
    ax_ = 364
    s.add(box(ax_, 422, 270, 108))
    s.add(text(ax_ + 20, 458, L["a11y"], size=18, weight="700", box=(ax_ + 20, 438, 236, 28)))
    s.add(text(ax_ + 20, 484, L["a11y2"], size=16, fill=INK2, lh=22, box=(ax_ + 20, 468, 240, 50)))
    s.add(arrow([(ax_ - 2, 476), (334, 476)], color=ACCENT, sw=2.5, head=10))
    # the only cloud part
    s.add(box(56, 404, 274, 144, fill=ACC_S, stroke=ACCENT, r=20, dash="6 6"))
    s.add(icon("cloud", 294, 436, 30, color=ACCENT, sw=2.2))
    s.add(text(80, 446, L["vm"], size=24, cls="serif", box=(80, 422, 190, 32)))
    s.add(text(80, 476, L["vm2"], size=16, fill=INK2, lh=22, box=(80, 460, 236, 50)))
    tw_ = round(max(tw(STRINGS[l]["cloud"], 16, bold=True) for l in STRINGS) + 26)
    s.add(rrect(80, 510, tw_, 26, 13, fill=ACCENT))
    s.add(text(80 + tw_ / 2, 528, L["cloud"], size=16, fill=WHITE, weight="700", anchor="middle",
               box=(84, 510, tw_ - 8, 26)))

    # ---------------------------------------------------------- row 3
    # EchoGuard, next to the voice mode
    s.add(arrow([(150, 552), (150, 592)], color=ACCENT, sw=2.5, head=9, both=True))
    s.add(text(166, 578, L["link"], size=16, fill=ACC_D, weight="600", box=(166, 560, 200, 24)))
    ex, ey, ew, eh = 56, 596, 420, 252
    s.add(box(ex, ey, ew, eh, r=20))
    s.add(icon("speaker", ex + 36, ey + 36, 26, color=ACCENT, sw=2.2))
    s.add(text(ex + 62, ey + 44, "EchoGuard", size=21, weight="700"))
    yy = ey + 86
    for item in L["eg"]:
        s.add(circle(ex + 30, yy - 5, 3.5, fill=ACCENT))
        s.add(text(ex + 46, yy, item, size=16, fill=DARK, lh=22, box=(ex + 46, yy - 18, ew - 46 - 16, 48)))
        yy += 60

    # local actions
    s.add(arrow([(854, 532), (854, 574)], color=GRAY, sw=2.5, head=9))
    s.add(text(868, 560, L["cmd"], size=16, fill=INK2, box=(868, 542, 120, 24)))
    chips = [("music", "Spotify", L["spotify2"]), ("radio", L["radio"], L["radio2"]),
             ("timer", L["timer"], L["timer2"]), ("alarm", L["alarm"], L["alarm2"])]
    chx0, chw, chg, chy, chh = 516, 196, 12, 604, 76
    centers = [chx0 + i * (chw + chg) + chw / 2 for i in range(4)]
    s.add(line([(centers[0], 586), (centers[-1], 586)], color=GRAY, sw=2.5, corner=0))
    for i, (ic, t1, t2) in enumerate(chips):
        x = chx0 + i * (chw + chg)
        s.add(arrow([(centers[i], 586), (centers[i], chy - 2)], color=GRAY, sw=2.5, head=8))
        s.add(box(x, chy, chw, chh, r=14))
        s.add(icon(ic, x + 30, chy + 27, 24, color=ACCENT, sw=2))
        s.add(text(x + 52, chy + 33, t1, size=17, weight="700", box=(x + 52, chy + 14, chw - 60, 26)))
        s.add(text(x + 18, chy + 60, t2, size=16, fill=INK2, box=(x + 14, chy + 44, chw - 24, 24)))

    # Spot Clock
    kx, ky, kw, kh = 516, 760, 816, 88
    for i in (2, 3):
        s.add(arrow([(centers[i], chy + chh + 2), (centers[i], ky - 2)], color=GRAY, sw=2.5, head=8))
    s.add(box(kx, ky, kw, kh, r=20))
    s.add(icon("clock", kx + 36, ky + 44, 28, color=ACCENT, sw=2.2))
    s.add(text(kx + 66, ky + 38, "Spot Clock", size=20, weight="700"))
    s.add(text(kx + 66, ky + 64, L["clock2"], size=16, fill=INK2, box=(kx + 66, ky + 48, kw - 80, 24)))
    # STATE broadcast along the right margin
    s.add(arrow([(gx + gw, 300), (1370, 300), (1370, ky + 32), (kx + kw + 2, ky + 32)], color=ACCENT, sw=2,
                head=9, dash="5 6", corner=14))
    s.add(text(kx + kw - 22, ky + 38, L["state"], size=16, fill=ACC_D, weight="600", anchor="end",
               box=(kx + 300, ky + 18, kw - 322, 26)))
    return s.render()
