#!/usr/bin/env python3
"""EN and DE must differ only in text: strip text content, title, desc and xml:lang, then compare."""
import re, sys, difflib, glob, os
d = sys.argv[1]
bad = 0
for en in sorted(glob.glob(os.path.join(d, "*.en.svg"))):
    de = en.replace(".en.svg", ".de.svg")
    def norm(p):
        s = open(p, encoding="utf-8").read()
        s = re.sub(r'xml:lang="[a-z]+"', "", s)
        s = re.sub(r"<title([^>]*)>.*?</title>", r"<title\1/>", s, flags=re.S)
        s = re.sub(r"<desc([^>]*)>.*?</desc>", r"<desc\1/>", s, flags=re.S)
        s = re.sub(r">[^<]+<", "><", s)          # text nodes (incl. tspans)
        return s.splitlines()
    a, b = norm(en), norm(de)
    diff = list(difflib.unified_diff(a, b, lineterm="", n=0))
    print("%-28s %s" % (os.path.basename(en).replace(".en.svg", ""), "identical layout" if not diff else "DIFFERS"))
    if diff:
        bad += 1
        print("\n".join(diff[:20]))
sys.exit(1 if bad else 0)
