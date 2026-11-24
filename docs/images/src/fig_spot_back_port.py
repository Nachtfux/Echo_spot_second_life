import math
from common import *  # noqa: F401,F403
from device import spot_rear, port_audio, port_power, port_usb

STRINGS = {
    "en": {
        "title": "Back of the Echo Spot: the hidden micro-USB port",
        "desc": "Rear view of an Echo Spot. Near the bottom are a 3.5 mm audio jack, a small glued rubber "
                "cover and the round DC power jack (seen from behind: power left, audio right). A magnified view shows the cover peeled back, revealing a "
                "micro-USB port between the audio jack and the power jack. Keep the original power supply "
                "connected; use a thin plastic tool, no metal, to lift the cover.",
        "h": "The back of the Echo Spot",
        "sub": "A micro-USB port is hidden between the two jacks.",
        "audio": "Audio out", "audio2": "3.5 mm jack",
        "power": "Power", "power2": ["keep the original", "PSU connected"],
        "usb": "Hidden micro-USB", "usb2": "under a glued rubber cover",
        "note": "Use a thin plastic tool, no metal",
        "cover": "cover, peeled back",
    },
    "de": {
        "title": "Rückseite des Echo Spot: der versteckte Micro-USB-Port",
        "desc": "Rückansicht eines Echo Spot. Unten sitzen eine 3,5-mm-Audiobuchse, eine kleine "
                "verklebte Gummiabdeckung und die runde Strombuchse. Eine Vergrößerung zeigt die "
                "abgezogene Abdeckung und darunter den Micro-USB-Port zwischen Audio- und Strombuchse. Das "
                "Original-Netzteil bleibt angeschlossen; die Abdeckung mit einem dünnen Plastikwerkzeug "
                "lösen, kein Metall.",
        "h": "Die Rückseite des Echo Spot",
        "sub": "Zwischen den beiden Buchsen versteckt sich ein Micro-USB-Port.",
        "audio": "Audio-Ausgang", "audio2": "3,5-mm-Klinke",
        "power": "Strom", "power2": ["Original-Netzteil", "bleibt dran"],
        "usb": "Versteckter Micro-USB", "usb2": "unter verklebter Abdeckung",
        "note": "Dünnes Plastikwerkzeug, kein Metall",
        "cover": "Abdeckung, abgezogen",
    },
}


def tangents(c1, c2):
    (x1, y1, r1), (x2, y2, r2) = c1, c2
    th = math.atan2(y2 - y1, x2 - x1)
    d = math.hypot(x2 - x1, y2 - y1)
    a = math.acos((r1 - r2) / d)
    res = []
    for s in (1, -1):
        t = th + s * a
        res.append(((x1 + r1 * math.cos(t), y1 + r1 * math.sin(t)), (x2 + r2 * math.cos(t), y2 + r2 * math.sin(t))))
    return res


def build(lang):
    L = STRINGS[lang]
    s = Svg(1200, 700, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 78, L["h"], size=34, cls="serif"))
    s.add(text(56, 112, L["sub"], size=18, fill=INK2))

    # the device, seen from behind
    body, pa, pu, pp = spot_rear(s, 250, 410, 170, uid="back")
    s.add(body)
    ring = (pu[0], pu[1], 58)
    bub = (742, 392, 212)
    k = 3.6

    # magnifier: tangents first (behind), then ring and bubble
    for p, q in tangents(ring, bub):
        s.add(line([p, q], color=GRAY, sw=2, dash="2 7"))
    s.add(circle(ring[0], ring[1], ring[2], stroke=ACCENT, sw=2.5))

    s.defn('<clipPath id="bp-zoom"><circle cx="%s" cy="%s" r="%s"/></clipPath>' % bub)
    s.defn('<radialGradient id="bp-zoombg" cx="0.4" cy="0.25" r="0.9">\n'
           '  <stop offset="0" stop-color="#3A3935"/>\n'
           '  <stop offset="1" stop-color="#121211"/>\n'
           '</radialGradient>')
    s.defn('<pattern id="bp-glue" width="7" height="7" patternUnits="userSpaceOnUse">\n'
           '  <circle cx="2" cy="2" r="1.1" fill="#6A6862"/>\n'
           '  <circle cx="5.5" cy="5.5" r="0.9" fill="#5A5853"/>\n'
           '</pattern>')
    s.add(circle(bub[0], bub[1], bub[2] + 7, fill=WHITE, stroke=LINE, sw=2))
    s.add('<g clip-path="url(#bp-zoom)">')
    s.add(circle(bub[0], bub[1], bub[2], fill="url(#bp-zoombg)"))
    # base curve of the ball inside the zoom
    s.add('<path d="M%s %s Q%s %s %s %s" fill="none" stroke="%s" stroke-opacity="0.07" stroke-width="3"/>'
          % (f(bub[0] - 260), f(bub[1] + 120), f(bub[0]), f(bub[1] + 250), f(bub[0] + 260), f(bub[1] + 120), WHITE))
    py = bub[1] + 34
    ax, ux, px = bub[0] + 40 * k, bub[0], bub[0] - 42 * k
    s.add(port_audio(ax, py, k))
    s.add(port_power(px, py, k))
    # recess left by the cover, port inside
    s.add(rrect(ux - 14 * k, py - 6.8 * k, 28 * k, 13.6 * k, 3.2 * k, fill="#0E0E0D", stroke="#3D3C38", sw=2))
    s.add(port_usb(ux, py, k))
    # the peeled-back cover, hinged at the top edge of the recess (underside with glue showing)
    top = py - 6.8 * k
    flap = ("M%s %s L%s %s Q%s %s %s %s L%s %s Q%s %s %s %s Z"
            % (f(ux - 13 * k), f(top - 2), f(ux + 13 * k), f(top - 2),
               f(ux + 15 * k), f(top - 8 * k), f(ux + 14 * k), f(top - 15 * k),
               f(ux - 10 * k), f(top - 17 * k),
               f(ux - 14 * k), f(top - 10 * k), f(ux - 13 * k), f(top - 2)))
    # thin plastic pry tool lifting the cover
    tx, ty = ux + 9 * k, top - 9 * k          # tool tip, under the cover
    ang = math.radians(-33)
    ln, w0, w1 = 230, 5, 15
    dx, dy = math.cos(ang), math.sin(ang)
    nx_, ny_ = -dy, dx
    pts = [(tx, ty), (tx + dx * 26 + nx_ * w1 / 2, ty + dy * 26 + ny_ * w1 / 2),
           (tx + dx * ln + nx_ * w1 / 2, ty + dy * ln + ny_ * w1 / 2),
           (tx + dx * ln - nx_ * w1 / 2, ty + dy * ln - ny_ * w1 / 2),
           (tx + dx * 26 - nx_ * w1 / 2, ty + dy * 26 - ny_ * w1 / 2)]
    s.add('<path d="M%s Z" fill="%s" stroke="%s" stroke-width="2" stroke-linejoin="round"/>'
          % (" L".join("%s %s" % (f(a), f(b)) for a, b in pts), PANEL2, GRAY))
    # rubber thickness, then the sticky underside
    s.add('<path d="%s" transform="translate(0 6)" fill="#0E0E0D"/>' % flap)
    s.add('<path d="%s" fill="#3E3D39" stroke="#5C5A55" stroke-width="2" stroke-linejoin="round"/>' % flap)
    s.add('<path d="%s" fill="url(#bp-glue)" opacity="0.55"/>' % flap)
    s.add("</g>")
    s.add(circle(bub[0], bub[1], bub[2], stroke=ACCENT, sw=2.5))
    s.add(text(ux - 4, top - 17 * k - 16, L["cover"], size=16, fill=IVORY, anchor="middle", opacity=0.75,
               weight="500"))

    # callouts
    lc = ACCENT
    # power (left jack seen from behind): up to the left of the bubble
    s.add(line([(px, py - 9 * k - 6), (px, 212), (px - 28, 212)], color=lc, sw=2, corner=10))
    s.add(circle(px, py - 9 * k - 6, 4, fill=lc))
    s.add(text(px - 40, 207, L["power"], size=20, weight="700", anchor="end", box=(300, 182, px - 40 - 300, 30)))
    s.add(text(px - 40, 233, L["power2"], size=17, fill=INK2, anchor="end", lh=23, box=(300, 214, px - 40 - 300, 50)))
    # audio (right jack): right column
    s.add(line([(ax + 6.5 * k, py - 6.5 * k), (ax + 64, 268), (980, 268)], color=lc, sw=2, corner=10))
    s.add(circle(ax + 6.5 * k, py - 6.5 * k, 4, fill=lc))
    s.add(text(992, 262, L["audio"], size=20, weight="700", box=(992, 238, 168, 30)))
    s.add(text(992, 290, L["audio2"], size=17, fill=INK2, box=(992, 272, 168, 26)))
    # usb: below the bubble
    s.add(line([(ux, py + 6.8 * k + 8), (ux, bub[1] + bub[2] + 24)], color=lc, sw=2))
    s.add(circle(ux, py + 6.8 * k + 8, 4, fill=lc))
    s.add(text(ux + 14, 640, L["usb"], size=20, weight="700", box=(ux + 14, 618, 1150 - ux - 14, 30)))
    s.add(text(ux + 14, 666, L["usb2"], size=17, fill=INK2, box=(ux + 14, 648, 1150 - ux - 14, 26)))

    # note
    nx, ny, nw, nh = 56, 618, 432, 48
    s.add(rrect(nx, ny, nw, nh, 24, fill=ACC_S, stroke=ACC_T, sw=2))
    s.add(icon("warn", nx + 30, ny + nh / 2, 24, color=ACCENT, sw=2.2))
    s.add(text(nx + 54, ny + 30, L["note"], size=17, weight="600", box=(nx + 54, ny, nw - 66, nh)))
    return s.render()
