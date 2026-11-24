import math
from common import *  # noqa: F401,F403

STRINGS = {
    "en": {
        "title": "Entering fastboot mode on the Echo Spot",
        "desc": "Top view of an Echo Spot with its three top buttons: volume down, microphone off in the "
                "middle, volume up. Three fingers press all three at once. Steps: 1 unplug the power supply, "
                "2 press and hold all three buttons, 3 plug the power back in while still holding them. The "
                "screen then shows fastboot mode.",
        "h": "Fastboot: hold all three buttons",
        "sub": "Echo Spot seen from above",
        "front": "front (screen)",
        "b": ["vol −", "mic off", "vol +"],
        "s1": "Unplug the power supply", "s1d": "The Spot must be completely off.",
        "s2": "Press and hold all three buttons", "s2d": "vol −, mic off and vol + at the same time",
        "s3": "Plug the power back in, keep holding", "s3d": "Let go when the screen shows fastboot mode.",
        "r": "fastboot mode",
        "r2": ["Now the computer can talk to the", "Spot: fastboot devices lists it."],
    },
    "de": {
        "title": "Fastboot-Modus am Echo Spot starten",
        "desc": "Draufsicht auf einen Echo Spot mit seinen drei Tasten oben: leiser, Mikrofon aus in der "
                "Mitte, lauter. Drei Finger drücken alle drei gleichzeitig. Schritte: 1 Netzteil abziehen, "
                "2 alle drei Tasten gedrückt halten, 3 Netzteil wieder einstecken und dabei weiter halten. "
                "Das Display zeigt dann fastboot mode.",
        "h": "Fastboot: alle drei Tasten halten",
        "sub": "Echo Spot von oben gesehen",
        "front": "Vorderseite (Display)",
        "b": ["leiser", "Mikro aus", "lauter"],
        "s1": "Netzteil abziehen", "s1d": "Der Spot muss komplett aus sein.",
        "s2": "Alle drei Tasten gedrückt halten", "s2d": "Leiser, Mikro aus und Lauter gleichzeitig",
        "s3": "Netzteil einstecken, weiter halten", "s3d": "Loslassen, sobald fastboot mode erscheint.",
        "r": "fastboot mode",
        "r2": ["Jetzt erreicht der Rechner den Spot:", "fastboot devices listet ihn auf."],
    },
}


def finger(x, tip_y, w=44, top=118, tilt=0):
    """A fingertip seen from above, pointing down onto (x, tip_y)."""
    h = tip_y - top
    g = ['<g transform="rotate(%s %s %s)">' % (f(tilt), f(x), f(tip_y))]
    g.append('<path d="M%s %s V%s a%s %s 0 0 0 %s 0 V%s" fill="%s" stroke="%s" stroke-width="2.5" '
             'stroke-linejoin="round"/>'
             % (f(x - w / 2), f(top), f(tip_y - w / 2), f(w / 2), f(w / 2), f(w), f(top), "#F2E7DD", GRAY))
    # nail
    g.append(rrect(x - w * 0.29, tip_y - w * 0.98, w * 0.58, w * 0.62, w * 0.26, fill="#FBF6F1", stroke=GRAY,
                   sw=2))
    # knuckle crease
    g.append(line([(x - w * 0.2, tip_y - w * 1.6), (x + w * 0.2, tip_y - w * 1.6)], color=GRAY, sw=2))
    g.append("</g>")
    return "\n".join(g)


def build(lang):
    L = STRINGS[lang]
    s = Svg(1200, 700, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 78, L["h"], size=34, cls="serif"))
    s.add(text(56, 112, L["sub"], size=18, fill=INK2))

    # ---- top view of the Spot
    cx, cy, r = 316, 424, 192
    chord = 0.64 * r                      # the flat front face, seen edge-on
    half = math.sqrt(r * r - chord * chord)
    s.defn('<radialGradient id="fb-top" cx="0.5" cy="0.42" r="0.62">\n'
           '  <stop offset="0" stop-color="#4A4944"/>\n'
           '  <stop offset="0.55" stop-color="#242321"/>\n'
           '  <stop offset="1" stop-color="#0F0F0E"/>\n'
           '</radialGradient>')
    body = ("M%s %s A%s %s 0 1 1 %s %s Z"
            % (f(cx - half), f(cy + chord), f(r), f(r), f(cx + half), f(cy + chord)))
    s.add('<path d="%s" transform="translate(6 10)" fill="%s" fill-opacity="0.10"/>' % (body, DARK))
    s.add('<path d="%s" fill="url(#fb-top)"/>' % body)
    # glass edge of the front face
    s.add(rrect(cx - half + 6, cy + chord - 9, 2 * half - 12, 9, 4.5, fill="#080808", stroke="#3A3936", sw=1.5))
    s.add(arrow([(cx, cy + chord + 22), (cx, cy + chord + 52)], color=GRAY, sw=2, head=9))
    s.add(text(cx, cy + chord + 80, L["front"], size=17, fill=INK2, anchor="middle"))

    # buttons
    by = cy - 30
    bxs = [cx - 76, cx, cx + 76]
    for i, bx in enumerate(bxs):
        # press ripples
        s.add(circle(bx, by, 37, stroke=ACCENT, sw=2.5, extra='stroke-opacity="0.9"'))
        s.add(circle(bx, by, 48, stroke=ACCENT, sw=2, extra='stroke-opacity="0.35"'))
        s.add(circle(bx, by, 26, fill="#33322F", stroke="#5A5853", sw=2))
    # symbols (drawn below the fingertips, visible in the lower half)
    s.add(line([(bxs[0] - 9, by + 9), (bxs[0] + 9, by + 9)], color=IVORY, sw=3))
    s.add(icon("mic_off", bxs[1], by + 8, 20, color=IVORY, sw=2))
    s.add(line([(bxs[2] - 9, by + 9), (bxs[2] + 9, by + 9)], color=IVORY, sw=3))
    s.add(line([(bxs[2], by), (bxs[2], by + 18)], color=IVORY, sw=3))
    # fingertips from behind the device, fading out towards the top
    s.defn('<linearGradient id="fb-fade" x1="0" y1="%s" x2="0" y2="%s" gradientUnits="userSpaceOnUse">\n'
           '  <stop offset="0" stop-color="#fff" stop-opacity="0"/>\n'
           '  <stop offset="1" stop-color="#fff" stop-opacity="1"/>\n'
           '</linearGradient>' % (214, 272))
    s.defn('<mask id="fb-fingers" maskUnits="userSpaceOnUse" x="0" y="0" width="1200" height="700">\n'
           '  <rect x="0" y="214" width="1200" height="486" fill="url(#fb-fade)"/>\n'
           '</mask>')
    s.add('<g mask="url(#fb-fingers)">')
    s.add(finger(bxs[0], by - 2, top=196, tilt=7))
    s.add(finger(bxs[1], by - 6, top=186))
    s.add(finger(bxs[2], by - 2, top=196, tilt=-7))
    s.add("</g>")
    # button labels on the device surface
    for bx, lab in zip(bxs, L["b"]):
        s.add(text(bx, by + 76, lab, size=16, fill=IVORY, anchor="middle", weight="600",
                   box=(bx - 38, by + 58, 76, 24)))

    # ---- steps
    x0 = 650
    ys = [196, 300, 404]
    s.add(line([(x0, ys[0]), (x0, ys[2])], color=LINE, sw=3))
    steps = [("s1", "plug", True), ("s2", "hand", False), ("s3", "plug", False)]
    for (key, ic, crossed), y in zip(steps, ys):
        s.add(badge(x0, y, ys.index(y) + 1, r=20, fill=ACCENT, color=WHITE, size=20))
        s.add(text(x0 + 40, y - 2, L[key], size=21, weight="700", box=(x0 + 40, y - 26, 1150 - x0 - 40, 32)))
        s.add(text(x0 + 40, y + 26, L[key + "d"], size=17, fill=INK2, box=(x0 + 40, y + 8, 1150 - x0 - 40, 26)))

    # ---- result: the Spot's screen in fastboot mode
    rx_, ry_ = x0 + 116, 582
    s.add(arrow([(x0, ys[2] + 30), (x0, ry_), (rx_ - 84, ry_)], color=ACCENT, sw=2.5, head=10, corner=16))
    s.add(circle(rx_ + 3, ry_ + 6, 76, fill=DARK, extra='fill-opacity="0.08"'))
    s.add(circle(rx_, ry_, 76, fill="#0B0B0A", stroke="#3A3936", sw=3))
    s.add(circle(rx_, ry_, 68, fill=DARK))
    s.add(text(rx_, ry_ + 6, L["r"], size=16, cls="mono", fill=IVORY, anchor="middle",
               box=(rx_ - 64, ry_ - 20, 128, 40)))
    s.add(text(rx_ + 100, ry_ - 4, L["r2"], size=17, fill=INK2, lh=24,
               box=(rx_ + 100, ry_ - 24, 1150 - rx_ - 100, 56)))
    return s.render()
