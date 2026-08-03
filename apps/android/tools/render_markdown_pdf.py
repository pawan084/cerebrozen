from __future__ import annotations

import html
import re
import sys
from pathlib import Path


def inline(text: str) -> str:
    value = html.escape(text.strip())
    value = re.sub(r"`([^`]+)`", r"<code>\1</code>", value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", value)
    return value


def render(markdown: str) -> str:
    lines = markdown.splitlines()
    body: list[str] = []
    i = 0
    list_tag: str | None = None

    def close_list() -> None:
        nonlocal list_tag
        if list_tag:
            body.append(f"</{list_tag}>")
            list_tag = None

    while i < len(lines):
        line = lines[i].rstrip()
        if not line.strip():
            close_list()
            i += 1
            continue

        if line.startswith("|") and i + 1 < len(lines) and re.match(r"^\|?[\s:|-]+\|?$", lines[i + 1].strip()):
            close_list()
            headers = [inline(x) for x in line.strip("|").split("|")]
            body.append("<table><thead><tr>" + "".join(f"<th>{x}</th>" for x in headers) + "</tr></thead><tbody>")
            i += 2
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                cells = [inline(x) for x in lines[i].strip().strip("|").split("|")]
                body.append("<tr>" + "".join(f"<td>{x}</td>" for x in cells) + "</tr>")
                i += 1
            body.append("</tbody></table>")
            continue

        heading = re.match(r"^(#{1,6})\s+(.+)$", line)
        if heading:
            close_list()
            level = len(heading.group(1))
            body.append(f"<h{level}>{inline(heading.group(2))}</h{level}>")
            i += 1
            continue

        quote = re.match(r"^>\s?(.*)$", line)
        if quote:
            close_list()
            body.append(f"<blockquote>{inline(quote.group(1))}</blockquote>")
            i += 1
            continue

        item = re.match(r"^\s*(-|\d+\.)\s+(.+)$", line)
        if item:
            wanted = "ol" if item.group(1)[0].isdigit() else "ul"
            if list_tag != wanted:
                close_list()
                list_tag = wanted
                body.append(f"<{wanted}>")
            body.append(f"<li>{inline(item.group(2))}</li>")
            i += 1
            continue

        close_list()
        paragraph = [line.strip()]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#{1,6})\s|^>|^\s*(-|\d+\.)\s|^\|", lines[i]):
            paragraph.append(lines[i].strip())
            i += 1
        body.append(f"<p>{inline(' '.join(paragraph))}</p>")

    close_list()
    return "\n".join(body)


source = Path(sys.argv[1]).resolve()
target = Path(sys.argv[2]).resolve()
content = render(source.read_text(encoding="utf-8"))
target.write_text(
    """<!doctype html><html lang=\"hi\"><head><meta charset=\"utf-8\"><style>
@page { size: A4; margin: 17mm 15mm 18mm; }
* { box-sizing: border-box; }
body { font-family: \"Nirmala UI\", \"Noto Sans Devanagari\", sans-serif; color:#20242b; font-size:10.4pt; line-height:1.52; }
h1 { color:#392c79; font-size:23pt; border-bottom:2px solid #8d7be8; padding-bottom:8px; }
h2 { color:#443587; font-size:16pt; margin-top:24px; break-after:avoid; }
h3 { color:#5b49a1; font-size:12.5pt; margin-top:18px; break-after:avoid; }
p { margin:7px 0; }
ul,ol { margin:7px 0 10px 23px; padding:0; }
li { margin:3px 0; }
table { width:100%; border-collapse:collapse; margin:10px 0 15px; font-size:9.2pt; break-inside:auto; }
tr { break-inside:avoid; }
th { background:#ece8ff; color:#35266f; text-align:left; }
th,td { border:1px solid #c9c2e8; padding:6px 7px; vertical-align:top; }
blockquote { border-left:4px solid #e3a64b; background:#fff7e8; padding:8px 12px; margin:10px 0; }
code { font-family:Consolas,monospace; background:#f2f1f7; padding:1px 3px; border-radius:3px; }
</style></head><body>""" + content + "</body></html>",
    encoding="utf-8",
)
