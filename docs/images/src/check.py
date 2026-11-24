#!/usr/bin/env python3
"""Measure every <text> with Chrome and report problems.

Checks: text outside the card (12 px inset), text overflowing its data-box, and overlapping text
boxes. Usage: check.py <svg> ...   (SVGs built with ESL_CHECK=1 carry data-box attributes)
"""
import json, os, subprocess, sys, tempfile

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
JS = r"""
const out = [];
for (const svg of document.querySelectorAll('svg')) {
  const W = +svg.getAttribute('width'), H = +svg.getAttribute('height');
  const items = [];
  for (const t of svg.querySelectorAll('text')) {
    if (t.closest('[data-nocheck]')) continue;
    const b = t.getBBox();
    const m = t.getCTM();
    // transform bbox corners to svg user space
    const pts = [[b.x,b.y],[b.x+b.width,b.y],[b.x,b.y+b.height],[b.x+b.width,b.y+b.height]].map(
      ([x,y]) => [m.a*x+m.c*y+m.e, m.b*x+m.d*y+m.f]);
    const xs = pts.map(p=>p[0]), ys = pts.map(p=>p[1]);
    const r = {x0:Math.min(...xs), y0:Math.min(...ys), x1:Math.max(...xs), y1:Math.max(...ys),
               s:t.textContent.slice(0,40)};
    items.push(r);
    if (r.x0 < 12 || r.y0 < 12 || r.x1 > W-12 || r.y1 > H-12) out.push(svg.id+': OUTSIDE CARD '+JSON.stringify(r));
    const box = t.getAttribute('data-box');
    if (box) {
      const [bx,by,bw,bh] = box.split(/[ ,]+/).map(Number);
      if (r.x0 < bx-1.5 || r.x1 > bx+bw+1.5 || r.y0 < by-1.5 || r.y1 > by+bh+1.5)
        out.push(svg.id+': OVERFLOWS BOX '+box+' '+JSON.stringify(r)+' w='+(r.x1-r.x0).toFixed(1));
    }
  }
  for (let i=0;i<items.length;i++) for (let j=i+1;j<items.length;j++) {
    const a=items[i], b=items[j];
    const sh=(r)=>{const h=r.y1-r.y0; return [r.y0+0.18*h, r.y1-0.18*h];};
    const [ay0,ay1]=sh(a),[by0,by1]=sh(b);
    const ox = Math.min(a.x1,b.x1)-Math.max(a.x0,b.x0), oy = Math.min(ay1,by1)-Math.max(ay0,by0);
    if (ox>1 && oy>3) out.push(svg.id+': OVERLAP "'+a.s+'" / "'+b.s+'"');
  }
}
document.body.setAttribute('data-result', JSON.stringify(out));
"""

def main():
    files = sys.argv[1:]
    font = os.environ.get("FONT")  # e.g. FONT=Arial to test a wider/narrower fallback
    html = ["<!doctype html><meta charset=utf-8><body>"]
    if font:
        html.append("<style>.sans{font-family:%s !important}</style>" % font)
    for p in files:
        svg = open(p, encoding="utf-8").read()
        svg = svg.replace("<svg ", '<svg id="%s" ' % os.path.basename(p), 1)
        html.append(svg)
    html.append("<script>%s</script>" % JS)
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as fh:
        fh.write("".join(html))
        path = fh.name
    dom = subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--virtual-time-budget=3000",
                          "--dump-dom", "file://" + path], capture_output=True, text=True).stdout
    i = dom.find('data-result="')
    if i < 0:
        print("no result", dom[:500]); sys.exit(2)
    raw = dom[i + 13: dom.find('"', i + 13)]
    import html as h
    res = json.loads(h.unescape(raw))
    for r in res:
        print(r)
    print("%d issue(s) in %d file(s)" % (len(res), len(files)))

main()
