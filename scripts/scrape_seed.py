#!/usr/bin/env python3
"""Scrape the Wayne State 'Word Warriors' list into the SmartWords seed/batch shape.

Source: https://wordwarriors.wayne.edu/list  (word + definition + example sentence).
The source has no part-of-speech, so `pos` is omitted; `example` is captured for free.
Reused later to publish daily batches (same output shape, add a "date").

    python3 scripts/scrape_seed.py            # -> shared/seed-words.json
    python3 scripts/scrape_seed.py --check    # parse + self-check, write nothing
"""
import html, json, os, re, sys, urllib.request

URL = "https://wordwarriors.wayne.edu/list"
OUT = os.path.join(os.path.dirname(__file__), "..", "shared", "seed-words.json")

# Each entry: <h2 class="...word">W</h2> <em class="definition">D</em> <p class="sentence">S</p>
ENTRY = re.compile(
    r'class="[^"]*\bword\b[^"]*">(?P<word>.*?)</h2>.*?'
    r'class="definition">(?P<definition>.*?)</em>.*?'
    r'class="sentence">(?P<sentence>.*?)</p>',
    re.DOTALL,
)

def clean(s: str) -> str:
    s = re.sub(r"<[^>]+>", "", s)          # strip any stray inline tags
    return re.sub(r"\s+", " ", html.unescape(s)).strip()

def cap(s: str) -> str:                     # shown as a standalone word, not in a sentence
    return s[:1].upper() + s[1:]

def parse(htmltext: str) -> list[dict]:
    out = []
    for m in ENTRY.finditer(htmltext):
        word = cap(clean(m["word"]))
        definition = clean(m["definition"])
        example = clean(m["sentence"])
        if word and definition:
            out.append({"word": word, "definition": definition, "example": example})
    return out

def main():
    htmltext = urllib.request.urlopen(URL, timeout=30).read().decode("utf-8", "replace")
    words = parse(htmltext)

    # self-check: the parser is the only fragile part — fail loud if the page shape moved.
    assert len(words) >= 800, f"expected ~831 entries, got {len(words)}"
    assert words[0]["word"] == "Abate", f"first word changed: {words[0]['word']!r}"
    assert all(w["word"] and w["definition"] for w in words), "missing word/definition"

    if "--check" in sys.argv:
        print(f"ok: parsed {len(words)} words"); return
    with open(OUT, "w") as f:
        json.dump({"words": words}, f, ensure_ascii=False, indent=0)
        f.write("\n")
    print(f"wrote {len(words)} words -> {os.path.relpath(OUT)}")

if __name__ == "__main__":
    main()
