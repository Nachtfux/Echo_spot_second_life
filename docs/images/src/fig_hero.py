from common import *  # noqa: F401,F403
from device import spot_front

STRINGS = {
    "en": {
        "title": "Echo Spot Second Life",
        "desc": "An Amazon Echo Spot (1st gen) showing the Spot Clock face: hour ticks, sunrise 07:46, "
                "weather minus 6 degrees, rain 0 percent, date Tuesday 24 November with a full moon, and an "
                "orange centre cap that shows the voice assistant is listening. Next to it the project name "
                "and tagline.",
        "eyebrow": "OPEN SOURCE · AMAZON ECHO SPOT, 1ST GEN",
        "tag": ["Your Echo Spot, reborn: wake word →",
                "Claude or ChatGPT voice mode, offline",
                "voice commands, a beautiful clock."],
        "chips": ["LineageOS 18.1", "Claude · ChatGPT", "one install script"],
        "bubble": "“Hey Claude”",
        "c_sunrise": "SUNRISE", "c_clear": "CLEAR", "c_rain": "RAIN", "c_weekday": "TUE",
    },
    "de": {
        "title": "Echo Spot Second Life",
        "desc": "Ein Amazon Echo Spot (1. Generation) mit dem Zifferblatt der Spot-Uhr: Stundenstriche, "
                "Sonnenaufgang 07:46, Wetter minus 6 Grad, Regen 0 Prozent, Datum Dienstag 24. November mit "
                "Vollmond und eine orange Mitte, die zeigt, dass der Sprachassistent zuhört. Daneben "
                "Projektname und Slogan.",
        "eyebrow": "OPEN SOURCE · AMAZON ECHO SPOT, 1. GENERATION",
        "tag": ["Dein Echo Spot, neu geboren: Wakeword →",
                "Sprachmodus von Claude oder ChatGPT,",
                "Offline-Sprachbefehle, eine schöne Uhr."],
        "chips": ["LineageOS 18.1", "Claude · ChatGPT", "ein Installationsskript"],
        "bubble": "„Hey Claude“",
        "c_sunrise": "AUFGANG", "c_clear": "KLAR", "c_rain": "REGEN", "c_weekday": "DI",
    },
}

# chip widths: same in both languages, sized for the longer text
CHIP_W = [round(max(tw(STRINGS[l]["chips"][i], 17) for l in STRINGS) * 1.05 + 32) for i in range(3)]


def build(lang):
    L = STRINGS[lang]
    s = Svg(1280, 640, L["title"], L["desc"], lang)
    s.card()
    # soft stage behind the device
    s.add(circle(348, 318, 262, fill=PANEL))
    s.add(circle(348, 318, 262, stroke=LINE, sw=2))
    s.add(spot_front(s, L, 348, 338, 226, uid="hero"))

    # speech bubble
    bx, by, bw, bh = 520, 92, 186, 52
    s.add('<path d="M%d %d h%d a14 14 0 0 1 14 14 v%d a14 14 0 0 1 -14 14 h%d l-16 14 l2 -14 h-%d '
          'a14 14 0 0 1 -14 -14 v-%d a14 14 0 0 1 14 -14 z" fill="%s" stroke="%s" stroke-width="2"/>'
          % (bx + 14, by, bw - 28, bh - 28, -(bw - 28 - 40), 26, bh - 28, WHITE, LINE))
    s.add(text(bx + bw / 2, by + 34, L["bubble"], size=22, cls="serif", style="italic", anchor="middle"))

    x = 664
    s.add(text(x, 172, L["eyebrow"], size=16, fill=ACC_D, weight="600", spacing=1.4, box=(x, 150, 1244 - x, 30)))
    s.add(text(x, 250, "Echo Spot", size=78, cls="serif"))
    s.add(text(x, 330, "Second Life", size=78, cls="serif", fill=ACCENT))
    s.add(text(x, 392, L["tag"], size=24, fill=INK2, lh=35, box=(x, 360, 1240 - x, 120)))
    cx = x
    for i, c in enumerate(L["chips"]):
        w = CHIP_W[i]
        s.add(rrect(cx, 492, w, 40, 20, fill=WHITE, stroke=LINE, sw=2))
        s.add(text(cx + w / 2, 518, c, size=17, fill=DARK, weight="500", anchor="middle",
                   box=(cx + 8, 492, w - 16, 40)))
        cx += w + 10
    return s.render()
