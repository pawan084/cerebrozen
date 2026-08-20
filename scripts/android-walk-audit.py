"""Audit a full-graph Android device walk.

Pairs a `uiautomator dump` with a screencap for every route and reports what a
screenshot review misses: dead screens, content past the right edge, tap
targets under the 48dp floor, and text under the WCAG contrast floor.

    # 1. capture (debug build only — MainActivity reads `walk_route` under BuildConfig.DEBUG)
    for r in $ROUTES; do
      adb shell am force-stop com.cerebrozen.app
      adb shell am start -n com.cerebrozen.app/.MainActivity -e walk_route "$r"
      sleep 7
      adb exec-out screencap -p           > out/$r.png
      adb exec-out uiautomator dump /dev/tty > out/$r.xml
    done

    # 2. audit
    python scripts/android-walk-audit.py out/

WHAT THIS TOOL GETS WRONG, because a walk on 2026-08-20 proved both directions:

* **It over-reports tap targets.** uiautomator reports VISIBLE bounds, clipped
  to the viewport. A card at the bottom of a scroll container measures its
  visible sliver, so a first pass flagged 12 "small tap targets" and eleven of
  them were 200px cards clipped at y=1604. Nodes touching a viewport edge are
  now skipped and counted separately. Compose also expands an interactive
  node's touch target to 48dp even when the visual box is smaller (a 46dp
  `TopBarAction` measures 96x96 at the touch layer), so a small VISUAL size is
  not a finding — only a small touch node is. A single dump can also race a
  layout or entrance animation: the bottom nav pill measured 220x11 seven
  seconds after launch and 212x114 once settled. **Every SMALLTAP is a
  candidate, not a verdict — confirm it with a second dump before believing
  it.**
* **It over-reports contrast on filled buttons.** Ink is taken as the extreme
  pixel inside the glyph box; on a filled pill that extreme is the fill, so
  every primary button reads ~1.00:1. Those are marked LOW-CONFIDENCE.
* **It under-reports contrast on art.** Paper is the median of a ring outside
  the glyphs, and a ring that is not near-flat is skipped as unmeasurable — so
  text on a gradient is not judged at all. The worst literal in the app
  (1.89:1) sat on a gradient and this tool called that screen clean.

The reliable instrument for colour is arithmetic over the palette tokens
(`ContrastTest`), not pixels. Treat everything here as a list of places to go
look, not as a verdict.
"""
import os
import re
import sys
from collections import Counter

from PIL import Image

DENSITY = 2.0                       # CPH2681: 320dpi
TAP_FLOOR = int(48 * DENSITY)       # 96px
# Chrome present on nearly every screen; a page with only these drew nothing.
CHROME = {"Home", "Chat", "Sleep", "CereBro", "Back", ""}

NODE = re.compile(r"<node [^>]*/?>")
BOUNDS = re.compile(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')
TEXT = re.compile(r'text="([^"]*)"')
DESC = re.compile(r'content-desc="([^"]*)"')


def lum(c):
    def f(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = c[:3]
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def nodes(xml):
    out = []
    for m in NODE.finditer(xml):
        tag = m.group(0)
        b = BOUNDS.search(tag)
        if not b:
            continue
        x1, y1, x2, y2 = (int(g) for g in b.groups())
        t, d = TEXT.search(tag), DESC.search(tag)
        out.append(dict(
            text=t.group(1) if t else "",
            desc=d.group(1) if d else "",
            click='clickable="true"' in tag,
            x1=x1, y1=y1, x2=x2, y2=y2,
        ))
    return out


def audit(png, xml_path):
    findings, clipped = [], 0
    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    ns = nodes(xml)
    if not ns:
        return [("DEAD", "no UI nodes dumped at all")], 0, 0, 0

    W = max(n["x2"] for n in ns)
    H = max(n["y2"] for n in ns)

    real = [n["text"] for n in ns if n["text"].strip() and n["text"] not in CHROME]
    if len(real) < 2:
        return [("DEAD", f"only {len(real)} non-chrome strings on screen")], 0, 0, 0

    for n in ns:
        if n["x2"] > W + 1 and n["text"].strip():
            findings.append(("OFFSCREEN", f'"{n["text"][:34]}" right={n["x2"]}'))
        if not n["click"]:
            continue
        h, w = n["y2"] - n["y1"], n["x2"] - n["x1"]
        if h <= 0:
            continue
        # A node flush against a viewport edge is CLIPPED, and its reported
        # height is how much of it you can see, not how big it is.
        if n["y1"] <= 0 or n["y2"] >= H or n["x1"] <= 0 or n["x2"] >= W:
            if h < TAP_FLOOR:
                clipped += 1
            continue
        if h < TAP_FLOOR or w < TAP_FLOOR:
            label = n["desc"][:28] or n["text"][:28] or "(unlabelled)"
            findings.append(("SMALLTAP?", f"{label} {w}x{h}px @({n['x1']},{n['y1']})"))

    checked = unmeasured = 0
    try:
        im = Image.open(png).convert("RGB")
    except Exception:
        return findings, 0, 0, clipped

    for n in ns:
        t = n["text"].strip()
        x1, y1, x2, y2 = n["x1"], n["y1"], n["x2"], n["y2"]
        if len(t) < 3 or x2 <= x1 or y2 <= y1 or x2 > im.width or y2 > im.height or y1 < 0:
            continue
        box = list(im.crop((x1, y1, x2, y2)).getdata())
        if len(box) < 40:
            continue
        pad = 8
        ring = []
        for yy in range(max(0, y1 - pad), min(im.height, y2 + pad)):
            for xx in range(max(0, x1 - pad), min(im.width, x2 + pad)):
                if x1 <= xx < x2 and y1 <= yy < y2:
                    continue
                ring.append(im.getpixel((xx, yy)))
        if len(ring) < 30:
            unmeasured += 1
            continue
        ring.sort(key=lum)
        paper = ring[len(ring) // 2]
        if lum(ring[int(len(ring) * 0.9)]) - lum(ring[int(len(ring) * 0.1)]) > 0.08:
            unmeasured += 1          # art or a gradient: no single background
            continue
        box.sort(key=lum)
        ink = box[0] if lum(paper) > 0.5 else box[-1]
        ratio = contrast(ink, paper)
        floor = 3.0 if (y2 - y1) >= 40 else 4.5
        checked += 1
        if ratio < floor:
            # On a FILLED control the extreme pixel inside the box is the fill,
            # not the glyph, so the ratio is meaningless. Say so rather than
            # reporting a number that will be dismissed — and then ignored
            # along with the real ones next to it.
            tag = "CONTRAST?" if ratio < 1.2 and n["click"] else "CONTRAST"
            findings.append((tag, f'{ratio:.2f}:1 (floor {floor}) "{t[:30]}"'))
    return findings, checked, unmeasured, clipped


def main(folder):
    rows = []
    for png in sorted(f for f in os.listdir(folder) if f.endswith(".png")):
        xml = os.path.join(folder, png[:-4] + ".xml")
        if os.path.exists(xml):
            rows.append((png[:-4],) + audit(os.path.join(folder, png), xml))

    total = Counter()
    print(f"{'route':26} {'measured':>8} {'clipped':>8}  findings")
    print("-" * 78)
    for route, f, checked, unm, clipped in rows:
        kinds = Counter(k for k, _ in f)
        total.update(kinds)
        print(f"{route:26} {checked:>8} {clipped:>8}  "
              f"{', '.join(f'{k}x{v}' for k, v in sorted(kinds.items())) or 'clean'}")
    print("-" * 78)
    print(f"TOTALS: {dict(total)} over {len(rows)} routes")
    print("CONTRAST? = low confidence (filled control); verify against the palette tokens.")
    print("SMALLTAP? = candidate only; a single dump races layout — re-dump to confirm.")
    print()
    for route, f, *_ in rows:
        if f:
            print(f"== {route}")
            for k, d in f[:10]:
                print(f"   {k:11} {d}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
