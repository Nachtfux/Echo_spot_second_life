import math
from common import *  # noqa: F401,F403

STRINGS = {
    "en": {
        "title": "Timeline of one voice conversation",
        "desc": "Timeline: you say Hey Claude. The Spot wakes up, the app opens and voice mode starts within "
                "about 3 seconds. Claude speaks while EchoGuard keeps your microphone muted, then you speak, "
                "and so on. The conversation ends with Danke Claude, 10 seconds of silence, saying the wake "
                "word again or the power button. Then the Spot returns to the clock and listens again. "
                "ChatGPT works the same way.",
        "h": "One conversation",
        "sub": "Example with Claude – ChatGPT works the same way.",
        "bubble": "“Hey Claude”",
        "span": "voice mode",
        "a": ["you say", "the wake word"],
        "b": "Spot wakes up", "b2": ["screen on, app opens,", "voice mode starts"], "b_bar": "~3 s",
        "c": "Claude speaks", "c2": ["your mic is muted", "by EchoGuard"], "c_bar": "Claude",
        "d": "You speak", "d2": ["mic open again"], "d_bar": "you",
        "e": "… and so on", "e2": ["taking turns"],
        "g": "Ends with", "g2": ["“Danke Claude”", "or 10 s silence", "or the wake word again",
                                "or the power button"],
        "h_": "Back to the clock", "h2": ["listening again"],
        "foot": "The centre of the clock shows the state: orange with a halo = listening, pulsing = in a "
                "conversation, gray = off.",
    },
    "de": {
        "title": "Ablauf eines Sprachgesprächs",
        "desc": "Zeitleiste: Du sagst Hey Claude. Der Spot wacht auf, die App öffnet sich und der "
                "Sprachmodus startet in etwa 3 Sekunden. Claude spricht, während EchoGuard dein Mikrofon "
                "stumm hält, dann sprichst du und so weiter. Das Gespräch endet mit Danke Claude, "
                "10 Sekunden Stille, dem erneuten Wakeword oder der Power-Taste. Danach zeigt der Spot wieder "
                "die Uhr und hört zu. Mit ChatGPT genauso.",
        "h": "Ein Gespräch",
        "sub": "Beispiel mit Claude – mit ChatGPT läuft es genauso.",
        "bubble": "„Hey Claude“",
        "span": "Sprachmodus",
        "a": ["du sagst", "das Wakeword"],
        "b": "Spot wacht auf", "b2": ["Display an, App öffnet,", "Sprachmodus startet"], "b_bar": "~3 s",
        "c": "Claude spricht", "c2": ["dein Mikro ist stumm", "(EchoGuard)"], "c_bar": "Claude",
        "d": "Du sprichst", "d2": ["Mikro wieder offen"], "d_bar": "du",
        "e": "… und so weiter", "e2": ["abwechselnd"],
        "g": "Ende durch", "g2": ["„Danke Claude“", "oder 10 s Stille", "oder nochmal Wakeword",
                                 "oder Power-Taste"],
        "h_": "Zurück zur Uhr", "h2": ["hört wieder zu"],
        "foot": "Die Mitte der Uhr zeigt den Zustand: orange mit Ring = hört zu, pulsierend = im "
                "Gespräch, grau = aus.",
    },
}


def mini_clock(cx, cy, r):
    o = [circle(cx, cy, r + 5, fill="#0B0B0A", stroke="#3A3936", sw=2), circle(cx, cy, r, fill=DARK)]
    for i in range(12):
        a = math.radians(i * 30)
        r0, r1 = r * 0.78, r * 0.92
        o.append('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="2" stroke-linecap="round"/>'
                 % (f(cx + math.sin(a) * r0), f(cy - math.cos(a) * r0), f(cx + math.sin(a) * r1),
                    f(cy - math.cos(a) * r1), IVORY))
    for deg, ln, w in ((305, 0.45, 3.5), (55, 0.68, 3)):
        a = math.radians(deg)
        o.append('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s" stroke-linecap="round"/>'
                 % (f(cx), f(cy), f(cx + math.sin(a) * r * ln), f(cy - math.cos(a) * r * ln), IVORY, w))
    o.append(circle(cx, cy, 9, stroke=ACCENT, sw=1.5, extra='stroke-opacity="0.6"'))
    o.append(circle(cx, cy, 4.5, fill=ACCENT))
    return "\n".join(o)


def build(lang):
    L = STRINGS[lang]
    s = Svg(1400, 500, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 74, L["h"], size=34, cls="serif"))
    s.add(text(56, 108, L["sub"], size=18, fill=INK2))

    ty, th = 250, 40           # track centre line and bar height
    top = ty - th / 2
    cy1, cy2 = 314, 340        # caption title / first caption line
    # faint baseline through everything
    s.add(line([(112, ty), (1280, ty)], color=PANEL2, sw=3))

    # A: wake word
    ax = 112
    bt = 148
    s.add('<path d="M%s %s h%s a14 14 0 0 1 14 14 v12 a14 14 0 0 1 -14 14 h-%s l-10 12 l-10 -12 h-%s '
          'a14 14 0 0 1 -14 -14 v-12 a14 14 0 0 1 14 -14 z" fill="%s" stroke="%s" stroke-width="2"/>'
          % (f(ax - 56), f(bt), f(112), f(46), f(46), WHITE, LINE))
    s.add(text(ax, bt + 32, L["bubble"], size=20, cls="serif", style="italic", anchor="middle",
               box=(ax - 70, bt + 6, 140, 34)))
    s.add(circle(ax, ty, 24, fill=ACCENT))
    s.add(icon("mic", ax, ty, 24, color=WHITE, sw=2.2))
    s.add(text(ax, cy1 + 2, L["a"], size=17, fill=INK2, anchor="middle", lh=24, box=(44, cy1 - 17, 136, 52)))

    def bar(x0, x1, fill, label=None, ic=None, color=WHITE, key=None):
        o = [rrect(x0, top, x1 - x0, th, th / 2, fill=fill)]
        if label and ic:
            # same icon/text position in both languages: centre on the wider label
            w = max(tw(STRINGS[l][key], 17, bold=True) for l in STRINGS) + 30
            o.append(icon(ic, (x0 + x1) / 2 - w / 2 + 10, ty, 20, color=color, sw=2))
            o.append(text((x0 + x1) / 2 - w / 2 + 28, ty + 6, label, size=17, fill=color, weight="700",
                          box=(x0 + 6, top, x1 - x0 - 12, th)))
        elif label:
            o.append(text((x0 + x1) / 2, ty + 6, label, size=17, fill=color, weight="700", anchor="middle",
                          box=(x0 + 6, top, x1 - x0 - 12, th)))
        elif ic:
            o.append(icon(ic, (x0 + x1) / 2, ty, 20, color=color, sw=2))
        return "\n".join(o)

    def caption(x, title, lines, w):
        return "\n".join([
            text(x, cy1, title, size=18, weight="700", box=(x, cy1 - 19, w, 26)),
            text(x, cy2, lines, size=17, fill=INK2, lh=24, box=(x, cy2 - 17, w, 24 * len(lines) + 4)),
        ])

    # B: launch
    s.add(bar(196, 380, PANEL2, L["b_bar"], color=INK2))
    s.add(caption(196, L["b"], L["b2"], 194))
    # C/D/E/F: the conversation
    s.add(bar(396, 596, ACCENT, L["c_bar"], "speaker", key="c_bar"))
    s.add(caption(396, L["c"], L["c2"], 206))
    s.add(bar(612, 772, DARK, L["d_bar"], "mic", key="d_bar"))
    s.add(caption(612, L["d"], L["d2"], 166))
    s.add(bar(788, 868, ACCENT, None, "speaker"))
    s.add(bar(880, 940, DARK, None, "mic"))
    for i in range(3):
        s.add(circle(956 + i * 12, ty, 3, fill=GRAY))
    s.add(caption(788, L["e"], L["e2"], 196))
    # bracket over the voice-mode span
    by = top - 22
    s.add(line([(396, by + 9), (396, by), (990, by), (990, by + 9)], color=GRAY, sw=2, corner=4))
    s.add(rrect(693 - 72, by - 14, 144, 28, 14, fill=IVORY))
    s.add(text(693, by + 6, L["span"], size=16, fill=INK2, anchor="middle", weight="600",
               box=(693 - 70, by - 14, 140, 28)))

    # G: end
    gx = 1036
    s.add(circle(gx, ty, 24, fill=WHITE, stroke=DARK, sw=2.5))
    s.add(rrect(gx - 8, ty - 8, 16, 16, 3, fill=DARK))
    s.add(text(1006, cy1, L["g"], size=18, weight="700", box=(1006, cy1 - 19, 190, 26)))
    s.add(text(1006, cy2, L["g2"], size=17, fill=INK2, lh=24, box=(1006, cy2 - 17, 200, 100)))

    # H: back to the clock
    hx = 1288
    s.add(arrow([(gx + 30, ty), (hx - 46, ty)], color=GRAY, sw=2.5, head=10))
    s.add(mini_clock(hx, ty, 34))
    s.add(text(hx, cy1, L["h_"], size=18, weight="700", anchor="middle", box=(1206, cy1 - 19, 164, 26)))
    s.add(text(hx, cy2, L["h2"], size=17, fill=INK2, anchor="middle", box=(1206, cy2 - 17, 164, 26)))

    # footer: what the clock's centre shows
    s.add(line([(56, 432), (1344, 432)], color=LINE, sw=2))
    s.add(circle(68, 462, 7, fill=ACCENT))
    s.add(circle(68, 462, 12, stroke=ACCENT, sw=1.5, extra='stroke-opacity="0.5"'))
    s.add(text(92, 468, L["foot"], size=17, fill=INK2, box=(92, 450, 1250, 26)))
    return s.render()
