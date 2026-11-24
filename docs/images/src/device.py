"""Echo Spot drawings (front with clock face, rear with ports, top with buttons)."""
import math
from common import *  # noqa: F401,F403

SUN_HORIZON = "M3 17h18M6 13.5a6 6 0 0 1 12 0M12 3.5v3M4.9 6.9l1.4 1.4M19.1 6.9l-1.4 1.4M9.5 20.5h5"
SUN = ("M16 12a4 4 0 1 1 -8 0a4 4 0 1 1 8 0zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4"
       "M17.3 17.3l1.4 1.4M5.3 18.7l1.4 -1.4M17.3 6.7l1.4 -1.4")
DROP = "M12 3.5C12 3.5 6 10.1 6 14.1a6 6 0 0 0 12 0C18 10.1 12 3.5 12 3.5z"


def _icon480(d, cx, cy, size, color, sw):
    k = size / 24.0
    return ('<path transform="translate(%s %s) scale(%s)" d="%s" fill="none" stroke="%s" stroke-width="%s" '
            'stroke-linecap="round" stroke-linejoin="round"/>'
            % (f(cx - size / 2.0), f(cy - size / 2.0), f(round(k, 4)), d, color, f(sw)))


def clock_face(L, uid):
    """The Spot Clock day face in its native 480-unit space, centred on (0, 0).

    Mirrors apps/clock ClockView.drawDay(): ticks, four complications, hands, centre cap.
    """
    o = []
    o.append(circle(0, 0, 240, fill=DARK))
    for i in range(60):
        a = math.radians(i * 6)
        sx, cx_ = math.sin(a), -math.cos(a)
        if i % 5 == 0:
            r0, r1, w, op = 211.5, 228.5, 5, None
        else:
            r0, r1, w, op = 222, 230, 2, 0.3
        o.append('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s" stroke-linecap="round"%s/>'
                 % (f(sx * r0), f(cx_ * r0), f(sx * r1), f(cx_ * r1), IVORY, w,
                    ' stroke-opacity="%s"' % op if op else ""))

    def comp(cx, cy):
        return circle(cx, cy, 68, fill=IVORY, extra='fill-opacity="0.05" stroke="%s" stroke-opacity="0.15" '
                                                     'stroke-width="1"' % IVORY)

    def lab(x, y, s, size, color=IVORY, op=None, weight="500", family="sans", spacing=None):
        return text(x, y, s, size=size, cls=family, fill=color, weight=weight, anchor="middle",
                    opacity=op, spacing=spacing)

    # sun (top)
    o.append(comp(0, -110))
    o.append(_icon480(SUN_HORIZON, 0, -136, 32, ACCENT, 1.7))
    o.append(lab(0, -94, "07:46", 24))
    o.append(lab(0, -74, L["c_sunrise"], 10.5, op=0.62, spacing=1.8))
    # weather (left)
    o.append(comp(-110, 0))
    o.append(_icon480(SUN, -143, -19, 32, ACCENT, 1.7))
    o.append(text(-122, -6, "−6°", size=36, cls="serif", fill=IVORY))
    o.append(lab(-110, 17, "↑−2°  ↓−9°", 13, op=0.78, weight="400"))
    o.append(lab(-110, 35, L["c_clear"], 10.5, op=0.62, spacing=1.8))
    # rain (right)
    o.append(comp(110, 0))
    o.append(circle(110, 0, 56, stroke=IVORY, sw=8, extra='stroke-opacity="0.12"'))
    o.append(_icon480(DROP, 110, -24, 20, ACCENT, 2))
    o.append(lab(110, 9, "0 %", 24))
    o.append(lab(110, 29, L["c_rain"], 10.5, op=0.62, spacing=1.8))
    # date (bottom) with full moon (24 Nov 2026 is a full moon)
    o.append(comp(0, 110))
    o.append(lab(0, 84, L["c_weekday"], 12, color=ACCENT, spacing=2))
    o.append(text(0, 126, "24", size=44, cls="serif", fill=IVORY, anchor="middle"))
    o.append(circle(-17, 144, 7, fill=IVORY))
    o.append(text(-4, 148, "NOV", size=12, cls="sans", fill=IVORY, weight="500", opacity=0.62, spacing=2))

    # hands at 10:09:38
    def hand(deg, w, ln, tail, color, outline):
        g = ['<g transform="rotate(%s)">' % f(deg)]
        if outline:
            g.append(rrect(-w / 2 - 2, -ln - 2, w + 4, ln + tail + 4, w / 2 + 2, fill=DARK))
        g.append(rrect(-w / 2, -ln, w, ln + tail, w / 2, fill=color))
        g.append("</g>")
        return "\n".join(g)

    o.append(hand((10 + 9 / 60.0) * 30, 10, 140, 6, IVORY, True))
    o.append(hand((9 + 38 / 60.0) * 6, 8, 210, 6, IVORY, True))
    o.append(hand(38 * 6, 2, 218, 38, ACCENT, False))
    # centre cap, "listening" state: orange with a halo
    o.append(circle(0, 0, 16, stroke=ACCENT, sw=2, extra='stroke-opacity="0.55"'))
    o.append(circle(0, 0, 24, stroke=ACCENT, sw=1.5, extra='stroke-opacity="0.22"'))
    o.append(circle(0, 0, 11, fill=DARK))
    o.append(circle(0, 0, 9, fill=ACCENT))
    o.append(circle(0, 0, 3, fill=DARK))
    return "\n".join(o)


def ball_defs(svg, uid):
    svg.defn('<radialGradient id="%s-ball" cx="0.36" cy="0.28" r="0.8">\n'
             '  <stop offset="0" stop-color="#55534D"/>\n'
             '  <stop offset="0.42" stop-color="#262523"/>\n'
             '  <stop offset="1" stop-color="#0D0D0C"/>\n'
             '</radialGradient>' % uid)
    svg.defn('<radialGradient id="%s-shadow" cx="0.5" cy="0.5" r="0.5">\n'
             '  <stop offset="0" stop-color="%s" stop-opacity="0.28"/>\n'
             '  <stop offset="1" stop-color="%s" stop-opacity="0"/>\n'
             '</radialGradient>' % (uid, DARK, DARK))


def ball(cx, cy, rx, ry, uid):
    o = []
    o.append('<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="url(#%s-shadow)"/>'
             % (f(cx), f(cy + ry - 6), f(rx * 0.86), f(ry * 0.13), uid))
    o.append('<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="url(#%s-ball)"/>' % (f(cx), f(cy), f(rx), f(ry), uid))
    # faint rim light
    o.append('<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="none" stroke="%s" stroke-opacity="0.07" '
             'stroke-width="2"/>' % (f(cx), f(cy), f(rx - 3), f(ry - 3), WHITE))
    return "\n".join(o)


def spot_front(svg, L, cx, cy, rx, uid="spot"):
    """Front view: flattened sphere, flat round glass with the clock, camera dot, grille."""
    ry = rx * 0.93
    gcy = cy - rx * 0.14            # glass centre sits above the ball centre
    gr = rx * 0.707                 # glass radius
    sr = gr * 0.89                  # screen radius
    ball_defs(svg, uid)
    svg.defn('<pattern id="%s-dots" width="11" height="9.5" patternUnits="userSpaceOnUse">\n'
             '  <circle cx="2.75" cy="2.4" r="1.7" fill="#4A4945"/>\n'
             '  <circle cx="8.25" cy="7.15" r="1.7" fill="#4A4945"/>\n'
             '</pattern>' % uid)
    svg.defn('<mask id="%s-grille" maskUnits="userSpaceOnUse" x="0" y="0" width="%d" height="%d">\n'
             '  <ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="#fff"/>\n'
             '  <circle cx="%s" cy="%s" r="%s" fill="#000"/>\n'
             '  <rect x="0" y="0" width="%d" height="%s" fill="#000"/>\n'
             '</mask>' % (uid, svg.w, svg.h, f(cx), f(cy), f(rx - 16), f(ry - 16), f(cx), f(gcy), f(gr + 14),
                          svg.w, f(gcy + gr * 0.55)))
    svg.defn('<clipPath id="%s-screen"><circle cx="0" cy="0" r="240"/></clipPath>' % uid)
    o = [ball(cx, cy, rx, ry, uid)]
    o.append('<rect x="%s" y="%s" width="%s" height="%s" fill="url(#%s-dots)" mask="url(#%s-grille)"/>'
             % (f(cx - rx), f(cy - ry), f(2 * rx), f(2 * ry), uid, uid))
    # glass
    o.append(circle(cx, gcy, gr + 3, fill="#0B0B0A", stroke="#3A3936", sw=2))
    o.append('<g transform="translate(%s %s) scale(%s)" clip-path="url(#%s-screen)">'
             % (f(cx), f(gcy), f(round(sr / 240.0, 4)), uid))
    o.append(clock_face(L, uid))
    o.append("</g>")
    # glass glare (very soft)
    o.append('<path d="M%s %s A%s %s 0 0 1 %s %s A%s %s 0 0 0 %s %s Z" fill="%s" fill-opacity="0.06"/>'
             % (f(cx - gr * 0.80), f(gcy - gr * 0.38), f(gr), f(gr), f(cx + gr * 0.2), f(gcy - gr * 0.98),
                f(gr * 1.25), f(gr * 1.25), f(cx - gr * 0.80), f(gcy - gr * 0.38), WHITE))
    # camera
    cam_y = gcy - (gr + sr) / 2.0 - 1
    o.append(circle(cx, cam_y, 4.5, fill="#1C1C1B", stroke="#3F3E3A", sw=1.5))
    o.append(circle(cx - 1.2, cam_y - 1.2, 1.2, fill="#7A7872"))
    return "\n".join(o)


# ----------------------------------------------------------- rear view ---
# Port order seen from behind (same in every drawing, confirmed on a real device): DC power (left),
# hidden micro-USB (middle), 3.5 mm audio (right).

def port_audio(x, y, k):
    return "\n".join([
        circle(x, y, 6.5 * k, fill="#0A0A09", stroke="#4E4C47", sw=max(1, 0.5 * k)),
        circle(x, y, 2.8 * k, fill="#000000"),
    ])


def port_power(x, y, k):
    return "\n".join([
        circle(x, y, 11 * k, fill="#0A0A09", stroke="#4E4C47", sw=max(1, 0.5 * k)),
        circle(x, y, 6.4 * k, fill="#000000"),
        circle(x, y, 2 * k, fill=GRAY),
    ])


def port_usb(x, y, k):
    """Micro-USB B socket: trapezoid shell, dark slot, tongue."""
    w, h = 15 * k, 5.6 * k
    c = 1.6 * k  # chamfer at the bottom corners
    shell = ("M%s %s h%s v%s l-%s %s h-%s l-%s -%s z"
             % (f(x - w / 2), f(y - h / 2), f(w), f(h - c), f(c), f(c), f(w - 2 * c), f(c), f(c)))
    i = 0.9 * k
    slot = ("M%s %s h%s v%s l-%s %s h-%s l-%s -%s z"
            % (f(x - w / 2 + i), f(y - h / 2 + i), f(w - 2 * i), f(h - 2 * i - c * 0.7), f(c * 0.7), f(c * 0.7),
               f(w - 2 * i - 1.4 * c), f(c * 0.7), f(c * 0.7)))
    return "\n".join([
        '<path d="%s" fill="#C9C7BE" stroke="#8D8B84" stroke-width="%s" stroke-linejoin="round"/>'
        % (shell, f(max(1, 0.25 * k))),
        '<path d="%s" fill="#151514" stroke-linejoin="round"/>' % slot,
        rrect(x - w * 0.3, y - h / 2 + i + 0.2 * k, w * 0.6, 1.3 * k, 0.4 * k, fill="#8D8B84"),
    ])


def cover(x, y, k):
    """The glued rubber cover in place (small rounded rectangle)."""
    return rrect(x - 13 * k, y - 6 * k, 26 * k, 12 * k, 3 * k, fill="#2A2927", stroke="#45433F",
                 sw=max(1, 0.4 * k))


def spot_rear(svg, cx, cy, rx, uid="rear", ports=True, cover_on=True):
    """Rear view: plain flattened sphere with the port row near the bottom."""
    ry = rx * 0.93
    ball_defs(svg, uid)
    o = [ball(cx, cy, rx, ry, uid)]
    # soft seam of the flat base
    o.append('<path d="M%s %s Q%s %s %s %s" fill="none" stroke="%s" stroke-opacity="0.08" stroke-width="2"/>'
             % (f(cx - rx * 0.78), f(cy + ry * 0.62), f(cx), f(cy + ry * 0.98), f(cx + rx * 0.78),
                f(cy + ry * 0.62), WHITE))
    py = cy + ry * 0.62
    k = rx / 170.0
    if ports:
        o.append(port_audio(cx + 40 * k, py, k))
        o.append(cover(cx, py, k) if cover_on else port_usb(cx, py, k))
        o.append(port_power(cx - 42 * k, py, k))
    return "\n".join(o), (cx + 40 * k, py), (cx, py), (cx - 42 * k, py)
