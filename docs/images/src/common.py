"""Shared helpers for the hand-tuned SVG illustrations in docs/images/.

Every illustration is one layout function that takes a dict of strings, so the
EN and DE files are guaranteed to differ only in their text.
"""
import math
import os
from xml.sax.saxutils import escape as _esc

IVORY = "#FAF9F5"
DARK = "#141413"
ACCENT = "#D97757"
GRAY = "#B0AEA5"
# soft tints of the four base colours
INK2 = "#5E5D59"      # secondary text (dark softened towards gray)
PANEL = "#F0EEE6"     # ivory, one step darker
PANEL2 = "#E8E6DC"    # ivory, two steps darker
LINE = "#DEDBD0"      # hairlines, card border
WHITE = "#FFFFFF"
ACC_T = "#F5DED3"     # accent tint
ACC_S = "#FBEFE9"     # accent, very soft
ACC_D = "#B95B3C"     # accent, darker (small accent text on ivory)
BODY = "#1E1E1C"      # device plastic
BODY2 = "#2C2B28"     # device plastic, lighter

SANS = "-apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
SERIF = "Georgia, 'Times New Roman', serif"
MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace"


def esc(s):
    return _esc(s, {'"': "&quot;"})


def f(v):
    """Compact number formatting."""
    if isinstance(v, float):
        v = round(v, 2)
        if v == int(v):
            return str(int(v))
        return ("%.2f" % v).rstrip("0").rstrip(".")
    return str(v)


class Svg:
    def __init__(self, w, h, title, desc, lang):
        self.w, self.h = w, h
        self.title, self.desc, self.lang = title, desc, lang
        self.defs = []
        self.parts = []

    def add(self, s):
        self.parts.append(s)

    def defn(self, s):
        self.defs.append(s)

    def card(self, fill=IVORY):
        self.add('<rect x="2" y="2" width="%d" height="%d" rx="28" fill="%s" stroke="%s" stroke-width="2"/>'
                 % (self.w - 4, self.h - 4, fill, LINE))

    def render(self):
        out = []
        out.append('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" '
                   'role="img" aria-labelledby="title desc" xml:lang="%s">'
                   % (self.w, self.h, self.w, self.h, self.lang))
        out.append('  <title id="title">%s</title>' % esc(self.title))
        out.append('  <desc id="desc">%s</desc>' % esc(self.desc))
        out.append('  <style>')
        out.append('    .sans { font-family: %s; }' % SANS)
        out.append('    .serif { font-family: %s; }' % SERIF)
        out.append('    .mono { font-family: %s; }' % MONO)
        out.append('  </style>')
        if self.defs:
            out.append('  <defs>')
            for d in self.defs:
                out.extend('    ' + line for line in d.split("\n"))
            out.append('  </defs>')
        for p in self.parts:
            out.extend('  ' + line for line in p.split("\n"))
        out.append('</svg>')
        return "\n".join(out) + "\n"


CHECK = os.environ.get("ESL_CHECK") == "1"


def text(x, y, s, size=16, cls="sans", fill=DARK, weight=None, anchor=None, lh=None,
         style=None, spacing=None, opacity=None, box=None):
    """<text>; s may be a list of lines (tspans with line height lh)."""
    lines = s if isinstance(s, list) else [s]
    attrs = ['x="%s"' % f(x), 'y="%s"' % f(y), 'class="%s"' % cls, 'font-size="%s"' % f(size),
             'fill="%s"' % fill]
    if weight:
        attrs.append('font-weight="%s"' % weight)
    if anchor:
        attrs.append('text-anchor="%s"' % anchor)
    if style:
        attrs.append('font-style="%s"' % style)
    if spacing is not None:
        attrs.append('letter-spacing="%s"' % f(spacing))
    if opacity is not None:
        attrs.append('fill-opacity="%s"' % f(opacity))
    if box and CHECK:
        attrs.append('data-box="%s"' % " ".join(f(v) for v in box))
    a = " ".join(attrs)
    if len(lines) == 1:
        return "<text %s>%s</text>" % (a, rich(lines[0]))
    lh = lh or size * 1.35
    spans = []
    for i, line in enumerate(lines):
        if i == 0:
            spans.append('<tspan x="%s">%s</tspan>' % (f(x), rich(line)))
        else:
            spans.append('<tspan x="%s" dy="%s">%s</tspan>' % (f(x), f(lh), rich(line)))
    return "<text %s>%s</text>" % (a, "".join(spans))


def rich(s):
    """Minimal inline markup: **bold** segments become a bold tspan."""
    parts = s.split("**")
    out = []
    for i, p in enumerate(parts):
        if i % 2:
            out.append('<tspan font-weight="700">%s</tspan>' % esc(p))
        else:
            out.append(esc(p))
    return "".join(out)


def rrect(x, y, w, h, r, fill="none", stroke=None, sw=2, dash=None, opacity=None, extra=""):
    a = 'x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s"' % (f(x), f(y), f(w), f(h), f(r), fill)
    if stroke:
        a += ' stroke="%s" stroke-width="%s"' % (stroke, f(sw))
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    if opacity is not None:
        a += ' opacity="%s"' % f(opacity)
    if extra:
        a += " " + extra
    return "<rect %s/>" % a


def circle(cx, cy, r, fill="none", stroke=None, sw=2, extra=""):
    a = 'cx="%s" cy="%s" r="%s" fill="%s"' % (f(cx), f(cy), f(r), fill)
    if stroke:
        a += ' stroke="%s" stroke-width="%s"' % (stroke, f(sw))
    if extra:
        a += " " + extra
    return "<circle %s/>" % a


def _round_path(pts, r):
    """Polyline through pts with rounded corners of radius r."""
    if len(pts) == 2 or r <= 0:
        return "M" + " L".join("%s %s" % (f(x), f(y)) for x, y in pts)
    d = "M%s %s" % (f(pts[0][0]), f(pts[0][1]))
    for i in range(1, len(pts) - 1):
        (x0, y0), (x1, y1), (x2, y2) = pts[i - 1], pts[i], pts[i + 1]
        l1 = math.hypot(x1 - x0, y1 - y0)
        l2 = math.hypot(x2 - x1, y2 - y1)
        rr = min(r, l1 / 2, l2 / 2)
        ax, ay = x1 - (x1 - x0) / l1 * rr, y1 - (y1 - y0) / l1 * rr
        bx, by = x1 + (x2 - x1) / l2 * rr, y1 + (y2 - y1) / l2 * rr
        d += " L%s %s Q%s %s %s %s" % (f(ax), f(ay), f(x1), f(y1), f(bx), f(by))
    d += " L%s %s" % (f(pts[-1][0]), f(pts[-1][1]))
    return d


def arrow(pts, color=INK2, sw=2.5, head=9, dash=None, corner=14, start_dot=False, both=False):
    """Orthogonal/polyline connector with a rounded arrowhead at the end."""
    pts = [tuple(map(float, p)) for p in pts]
    out = []

    def head_at(tip, frm):
        (x2, y2), (x1, y1) = tip, frm
        ang = math.atan2(y2 - y1, x2 - x1)
        hw = head * 0.62
        bx, by = x2 - math.cos(ang) * head, y2 - math.sin(ang) * head
        px, py = -math.sin(ang) * hw, math.cos(ang) * hw
        return ('<path d="M%s %s L%s %s L%s %s Z" fill="%s" stroke="%s" stroke-width="%s" '
                'stroke-linejoin="round"/>'
                % (f(x2), f(y2), f(bx + px), f(by + py), f(bx - px), f(by - py), color, color, f(sw * 0.8)))

    # shorten the line so it ends inside the arrowhead
    (xe, ye), (xp, yp) = pts[-1], pts[-2]
    le = math.hypot(xe - xp, ye - yp)
    line_pts = list(pts)
    line_pts[-1] = (xe - (xe - xp) / le * head * 0.7, ye - (ye - yp) / le * head * 0.7)
    if both:
        (xs, ys), (xn, yn) = pts[0], pts[1]
        ls = math.hypot(xn - xs, yn - ys)
        line_pts[0] = (xs + (xn - xs) / ls * head * 0.7, ys + (yn - ys) / ls * head * 0.7)
    a = 'd="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % (
        _round_path(line_pts, corner), color, f(sw))
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    out.append("<path %s/>" % a)
    out.append(head_at(pts[-1], pts[-2]))
    if both:
        out.append(head_at(pts[0], pts[1]))
    if start_dot:
        out.append(circle(pts[0][0], pts[0][1], 4, fill=color))
    return "\n".join(out)


def line(pts, color=GRAY, sw=2, dash=None, corner=0, cap="round"):
    a = 'd="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="round"' % (
        _round_path([tuple(map(float, p)) for p in pts], corner), color, f(sw), cap)
    if dash:
        a += ' stroke-dasharray="%s"' % dash
    return "<path %s/>" % a


# --------------------------------------------------------------- icons ---
# All icons are drawn on a 24 x 24 grid with round caps; icon() scales them.

ICONS = {
    # raised hand = "you have to do something here"
    "hand": "M8 14.5V6.6a1.5 1.5 0 0 1 3 0V12M11 11.6V4.6a1.5 1.5 0 0 1 3 0V12M14 12V5.6a1.5 1.5 0 0 1 3 0V12.4"
            "M17 12.6V8.6a1.5 1.5 0 0 1 3 0V15a6.3 6.3 0 0 1-6.3 6.3h-1.4a6.3 6.3 0 0 1-4.9-2.4L4.1 15.5"
            "a1.6 1.6 0 0 1 2.5-2L8 15.2",
    "mic": "M12 3.2a3 3 0 0 1 3 3v5.6a3 3 0 0 1-6 0V6.2a3 3 0 0 1 3-3zM6 11.4a6 6 0 0 0 12 0M12 17.4v3.4M8.8 20.8h6.4",
    "mic_off": "M12 3.2a3 3 0 0 1 3 3v5.6a3 3 0 0 1-6 0V6.2a3 3 0 0 1 3-3zM6 11.4a6 6 0 0 0 12 0M12 17.4v3.4"
               "M8.8 20.8h6.4M4 4l16 16",
    "cloud": "M7 18.5h10a4 4 0 0 0 .4-8a6 6 0 0 0-11.5 1.8A3.4 3.4 0 0 0 7 18.5z",
    "wifi": "M2.5 9.2a14 14 0 0 1 19 0M5.6 12.6a9.5 9.5 0 0 1 12.8 0M8.7 16a5 5 0 0 1 6.6 0M12 19.6h.01",
    "usb": "M12 21V4M12 4l-2.5 3M12 4l2.5 3M12 16.5l-5-3V10M12 14l5-3V8.5M5.8 8.5h2.4v2.4H5.8zM17 8.6a1.2 1.2 0 1 0 0-.01",
    "plug": "M9 3v5M15 3v5M6.5 8h11v3a5.5 5.5 0 0 1-11 0zM12 16.5V21",
    "check": "M5 12.5l4.5 4.5L19 7.5",
    "warn": "M12 4.2L21.2 19.6H2.8zM12 10v4.2M12 17h.01",
    "speaker": "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a4.2 4.2 0 0 1 0 6M18.2 6.5a8 8 0 0 1 0 11",
    "music": "M9 18.2V5.6l10-2v12.4M9 18.2a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0zM19 16a2.6 2.6 0 1 1-5.2 0"
             "a2.6 2.6 0 1 1 5.2 0z",
    "radio": "M4 9.2h16v10.3H4zM6.5 9.2l11-5M14.5 14.4a2.2 2.2 0 1 0 .01 0M7 13h3.6M7 16h3.6",
    "timer": "M12 21a8 8 0 1 0 0-16a8 8 0 0 0 0 16zM12 9v4.2l2.6 1.6M9.5 2.8h5",
    "alarm": "M12 20.5a7.5 7.5 0 1 0 0-15a7.5 7.5 0 0 0 0 15zM12 9v4l2.5 1.6M4 5.6l3-2.6M20 5.6l-3-2.6"
             "M6.5 19l-1.6 1.8M17.5 19l1.6 1.8",
    "clock": "M12 21a9 9 0 1 0 0-18a9 9 0 0 0 0 18zM12 7v5l3.2 2",
    "power": "M12 3.5v8M7.2 6.6a7 7 0 1 0 9.6 0",
    "eye": "M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12zM12 15a3 3 0 1 0 0-6"
           "a3 3 0 0 0 0 6z",
    "wave": "M3 12h2M7 8.5v7M11 5.5v13M15 8v8M19 10.5v3M21 12h.01",
    "terminal": "M4 6.5l5 5.2l-5 5.2M11.5 17.5H20",
    "repeat": "M17 3.5l3 3l-3 3M4 11.5v-1a4 4 0 0 1 4-4h12M7 20.5l-3-3l3-3M20 12.5v1a4 4 0 0 1-4 4H4",
    "lock_open": "M5.5 11h13v9.5h-13zM8.5 11V7.5a3.5 3.5 0 0 1 6.8-1.2M12 14.6v2.4",
    "gear": "M12 15.2a3.2 3.2 0 1 0 0-6.4a3.2 3.2 0 0 0 0 6.4zM12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6"
            "M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M5.5 18.5l1.8-1.8M16.7 7.3l1.8-1.8",
    "spark": "M12 3.5l1.9 5.6l5.6 1.9l-5.6 1.9L12 18.5l-1.9-5.6L4.5 11l5.6-1.9z",
    "x": "M6.5 6.5l11 11M17.5 6.5l-11 11",
    "laptop": "M5 5.5h14v10H5zM2.5 18.5h19",
}


def icon(name, cx, cy, size, color=DARK, sw=2):
    """Stroke icon centred on (cx, cy); sw is the stroke width in output pixels."""
    k = size / 24.0
    return ('<path transform="translate(%s %s) scale(%s)" d="%s" fill="none" stroke="%s" stroke-width="%s" '
            'stroke-linecap="round" stroke-linejoin="round"/>'
            % (f(cx - size / 2.0), f(cy - size / 2.0), f(round(k, 4)), ICONS[name], color, f(round(sw / k, 3))))


def badge(cx, cy, n, r=17, fill=DARK, color=IVORY, size=18):
    return "\n".join([
        circle(cx, cy, r, fill=fill),
        text(cx, cy + size * 0.36, str(n), size=size, cls="serif", fill=color, anchor="middle", weight="700"),
    ])


# ------------------------------------------------------- text metrics ---
# Approximate Helvetica advance widths (1/1000 em); used to size chips/boxes so they fit
# the longer language. The Chrome check (check.py) measures the real fonts afterwards.
_W = {
    " ": 278, "!": 278, '"': 355, "#": 556, "$": 556, "%": 889, "&": 667, "'": 191, "(": 333, ")": 333,
    "*": 389, "+": 584, ",": 278, "-": 333, ".": 278, "/": 278, ":": 278, ";": 278, "<": 584, "=": 584,
    ">": 584, "?": 556, "@": 1015, "_": 556, "|": 260, "~": 584,
    "·": 278, "→": 1000, "–": 556, "—": 1000, "„": 333, "“": 333,
    "”": 333, "…": 1000, "°": 400, "−": 584, "↑": 600, "↓": 600,
    "ä": 556, "ö": 556, "ü": 556, "ß": 611, "Ä": 667, "Ö": 778, "Ü": 722,
}
for _c, _v in zip("abcdefghijklmnopqrstuvwxyz",
                  [556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333,
                   500, 278, 556, 500, 722, 500, 500, 500]):
    _W[_c] = _v
for _c, _v in zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                  [667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722,
                   667, 611, 722, 667, 944, 667, 667, 611]):
    _W[_c] = _v
for _c in "0123456789":
    _W[_c] = 556


def tw(s, size, bold=False, mono=False):
    """Estimated text width in px."""
    s = s.replace("**", "")
    if mono:
        return len(s) * size * 0.602
    w = sum(_W.get(c, 600) for c in s) * size / 1000.0
    return w * (1.07 if bold else 1.0)
