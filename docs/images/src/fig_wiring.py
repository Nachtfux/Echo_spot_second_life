from common import *  # noqa: F401,F403
from device import spot_rear, port_audio

STRINGS = {
    "en": {
        "title": "Wiring: computer, USB cable, Echo Spot and power supply",
        "desc": "A Mac or Linux laptop runs ./install.sh and is connected with a USB data cable to the hidden "
                "micro-USB port on the back of the Echo Spot. The Spot's DC jack stays connected to its "
                "original power supply. Use a data cable, not a charge-only cable. After setup, adb runs over "
                "Wi-Fi and the cable can be removed.",
        "h": "Wiring",
        "sub": "Computer, USB cable, Spot – and its own power supply",
        "laptop": "Mac / Linux", "laptop2": "adb + fastboot",
        "term": ["$ ./install.sh", "✓ doctor", "› unlock …"],
        "port": "Hidden micro-USB port", "port2": "back of the Echo Spot",
        "data": "Data cable, not charge-only",
        "psu": "Original power supply",
        "wifi": ["After setup: adb over Wi-Fi,", "the cable can go"],
    },
    "de": {
        "title": "Verkabelung: Rechner, USB-Kabel, Echo Spot und Netzteil",
        "desc": "Ein Mac- oder Linux-Laptop führt ./install.sh aus und ist per USB-Datenkabel mit dem "
                "versteckten Micro-USB-Port auf der Rückseite des Echo Spot verbunden. Die Strombuchse "
                "des Spot bleibt am Original-Netzteil. Ein Datenkabel verwenden, kein reines Ladekabel. Nach "
                "dem Setup läuft adb über WLAN und das Kabel kann ab.",
        "h": "Verkabelung",
        "sub": "Rechner, USB-Kabel, Spot – und sein eigenes Netzteil",
        "laptop": "Mac / Linux", "laptop2": "adb + fastboot",
        "term": ["$ ./install.sh", "✓ doctor", "› unlock …"],
        "port": "Versteckter Micro-USB-Port", "port2": "Rückseite des Echo Spot",
        "data": "Datenkabel, kein reines Ladekabel",
        "psu": "Original-Netzteil",
        "wifi": ["Nach dem Setup: adb über WLAN,", "das Kabel kann ab"],
    },
}


def build(lang):
    L = STRINGS[lang]
    s = Svg(1200, 600, L["title"], L["desc"], lang)
    s.card()
    s.add(text(56, 74, L["h"], size=34, cls="serif"))
    s.add(text(56, 108, L["sub"], size=18, fill=INK2))

    # ---- laptop
    lx0, lx1, ly0, ly1 = 84, 376, 196, 370
    s.add(rrect(lx0, ly0, lx1 - lx0, ly1 - ly0 + 6, 14, fill=DARK))
    s.add(rrect(lx0 + 12, ly0 + 12, lx1 - lx0 - 24, ly1 - ly0 - 18, 6, fill="#262624"))
    s.add(text(lx0 + 30, ly0 + 50, L["term"], size=17, cls="mono", fill=IVORY, lh=28))
    s.add(rrect(lx0 + 28, ly0 + 50 + 2 * 28 + 18, 11, 3, 1.5, fill=ACCENT))
    s.add('<path d="M%s %s H%s a8 8 0 0 1 -8 8 H%s a8 8 0 0 1 -8 -8 Z" fill="%s" stroke="%s" stroke-width="2" '
          'stroke-linejoin="round"/>' % (f(lx0 - 28), f(ly1 + 4), f(lx1 + 28), f(lx0 - 12), PANEL2, GRAY))
    s.add(rrect((lx0 + lx1) / 2 - 30, ly1 + 4, 60, 5, 2.5, fill=GRAY))
    s.add(text((lx0 + lx1) / 2, ly1 + 52, L["laptop"], size=19, weight="700", anchor="middle"))
    s.add(text((lx0 + lx1) / 2, ly1 + 76, L["laptop2"], size=16, fill=INK2, anchor="middle", cls="mono"))

    # ---- Spot, rear view
    body, pa, pu, pp = spot_rear(s, 862, 296, 150, uid="wire", cover_on=False)
    k = 150 / 170.0

    s.add(body)

    # ---- cables (in front of the body, outlined so they read on dark plastic)
    usb_start = (lx1 + 28 + 26, ly1 - 6)
    s.add(rrect(lx1 + 28, ly1 - 14, 26, 16, 3, fill="#C9C7BE", stroke=GRAY, sw=2))   # USB plug at the laptop
    usb = ("M%s %s C%s %s %s %s %s %s H%s C%s %s %s %s %s %s"
           % (f(usb_start[0]), f(usb_start[1]), f(usb_start[0] + 70), f(usb_start[1]),
              f(usb_start[0] + 40), f(498), f(usb_start[0] + 120), f(498),
              f(pu[0] - 70), f(pu[0] - 10), f(498), f(pu[0]), f(470), f(pu[0]), f(pu[1] + 10)))
    # power jack is left of the USB port (seen from behind): its cable drops below the USB cable
    pwr = ("M%s %s V%s Q%s %s %s %s H%s"
           % (f(pp[0]), f(pp[1] + 10), f(516), f(pp[0]), f(532), f(pp[0] + 16), f(532), f(1000)))
    for d, core in ((usb, DARK), (pwr, INK2)):
        s.add('<path d="%s" fill="none" stroke="%s" stroke-width="10" stroke-linecap="round"/>' % (d, "#8A8880"))
        s.add('<path d="%s" fill="none" stroke="%s" stroke-width="6" stroke-linecap="round"/>' % (d, core))
    # plugs in the ports
    s.add(rrect(pu[0] - 13, pu[1] - 9, 26, 22, 5, fill="#3E3D39", stroke="#6A6862", sw=2))
    s.add(circle(pp[0], pp[1], 13, fill="#3E3D39", stroke="#6A6862", sw=2))

    # ---- power supply
    s.add(rrect(1000, 493, 104, 78, 16, fill=PANEL2, stroke=GRAY, sw=2))
    s.add(icon("plug", 1052, 532, 30, color=INK2, sw=2.2))
    s.add(text(1052, 478, L["psu"], size=17, weight="700", anchor="middle", box=(950, 460, 204, 26)))

    # ---- labels
    # hidden port: leader from the left
    # leader from the top of the USB plug (the power plug sits left of it)
    s.add(line([(pu[0], pu[1] - 11), (pu[0], 330), (700, 330)], color=ACCENT, sw=2, corner=10))
    s.add(circle(pu[0], pu[1] - 11, 4, fill=ACCENT))
    s.add(text(690, 326, L["port"], size=18, weight="700", anchor="end", box=(430, 306, 260, 28)))
    s.add(text(690, 350, L["port2"], size=16, fill=INK2, anchor="end", box=(430, 334, 260, 24)))

    # data cable note sitting on the cable
    pw = round(max(tw(STRINGS[l]["data"], 17, bold=True) for l in STRINGS) * 1.15 + 70)
    pcx = 610
    s.add(rrect(pcx - pw / 2, 478, pw, 42, 21, fill=WHITE, stroke=LINE, sw=2))
    s.add(icon("check", pcx - pw / 2 + 26, 499, 22, color=ACCENT, sw=2.6))
    s.add(text(pcx - pw / 2 + 46, 505, L["data"], size=17, weight="600", box=(pcx - pw / 2 + 44, 478, pw - 56, 42)))

    # Wi-Fi after setup
    s.add('<path d="M%s %s Q%s %s %s %s" fill="none" stroke="%s" stroke-width="2.5" stroke-dasharray="3 8" '
          'stroke-linecap="round"/>' % (f(lx1 - 30), f(ly0 - 12), f(560), f(60), f(730), f(196), ACCENT))
    s.add(circle(560, 128, 24, fill=IVORY, stroke=ACC_T, sw=2))
    s.add(icon("wifi", 560, 129, 26, color=ACCENT, sw=2.4))
    s.add(text(560, 196, L["wifi"], size=17, fill=DARK, anchor="middle", lh=23, weight="500",
               box=(410, 176, 300, 52)))
    return s.render()
