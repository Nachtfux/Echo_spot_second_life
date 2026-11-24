#!/usr/bin/env python3
"""Write docs/images/<name>.<lang>.svg for every figure module."""
import importlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

FIGS = ["hero", "spot-back-port", "fastboot-buttons", "wiring", "install-flow", "architecture", "voice-session"]


def main():
    out = sys.argv[1]
    only = sys.argv[2:] or FIGS
    for name in only:
        mod = importlib.import_module("fig_" + name.replace("-", "_"))
        for lang in ("en", "de"):
            path = os.path.join(out, "%s.%s.svg" % (name, lang))
            svg = mod.build(lang)
            # unique ids for the accessible name, in case several SVGs end up inline on one page
            svg = svg.replace('aria-labelledby="title desc"', 'aria-labelledby="%s-title %s-desc"' % (name, name), 1)
            svg = svg.replace('<title id="title">', '<title id="%s-title">' % name, 1)
            svg = svg.replace('<desc id="desc">', '<desc id="%s-desc">' % name, 1)
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(svg)
            print(path)


if __name__ == "__main__":
    main()
