#!/usr/bin/env python3
"""Backfill missing `pos` (part of speech) in a SmartWords word file.

Dev-time enrichment, NOT app code — so the network call is fine here. Uses the
free dictionaryapi.dev (no API key). Resumable: only fills entries that lack
`pos` and writes back as it goes, so re-running continues where it stopped and
retries words that weren't found last time.

    python3 scripts/fill_pos.py                      # fill shared/seed-words.json
    python3 scripts/fill_pos.py path/to/words.json   # fill another file
    python3 scripts/fill_pos.py --limit 20           # only the first 20 missing
    python3 scripts/fill_pos.py --selftest           # offline parse check, no network
"""
from __future__ import annotations
import json, re, sys, time, urllib.error, urllib.parse, urllib.request

API = "https://api.dictionaryapi.dev/api/v2/entries/en/{}"

STOP = {"the", "a", "an", "of", "to", "or", "and", "in", "is", "be", "with",
        "for", "that", "as", "by", "on", "at", "from", "into", "not", "very",
        "being", "such", "which", "who", "whom", "his", "her", "its", "their"}

def _tokens(s: str) -> set[str]:
    return {w for w in re.findall(r"[a-z]+", s.lower()) if len(w) > 2 and w not in STOP}

def best_pos(payload, our_def: str | None = None) -> str | None:
    """Pick the POS whose API sense best matches our definition (cross-check).

    Falls back to the most common POS, then the first, when nothing overlaps.
    """
    meanings = []  # (pos, [definition strings])
    for entry in payload:
        for m in entry.get("meanings", []):
            pos = m.get("partOfSpeech")
            if pos:
                meanings.append((pos, [d.get("definition", "") for d in m.get("definitions", [])]))
    if not meanings:
        return None

    if our_def:
        ours = _tokens(our_def)
        best_score, best = 0, None
        for pos, defs in meanings:
            score = max((len(ours & _tokens(d)) for d in defs), default=0)
            if score > best_score:          # strict >: ties keep the earlier (more primary) sense
                best_score, best = score, pos
        if best:
            return best

    # fallback: most common POS, ties broken by first appearance
    fallback = None
    for pos, _ in meanings:
        if fallback is None or sum(p == pos for p, _ in meanings) > sum(p == fallback for p, _ in meanings):
            fallback = pos
    return fallback

UA = "Mozilla/5.0 (SmartWords seed builder)"   # default urllib UA gets 403'd

def lookup(word: str, our_def: str | None = None, retries: int = 3) -> str | None:
    url = API.format(urllib.parse.quote(word.lower()))   # API is keyed on the plain word
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                return best_pos(json.load(r), our_def)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None                              # not in the dictionary
            time.sleep(2 * (attempt + 1))                # 429/5xx/etc: back off, retry, then give up
        except (urllib.error.URLError, TimeoutError):
            time.sleep(attempt + 1)
    return None                                          # transient failure: leave for a re-run

def save(path: str, data) -> None:
    with open(path, "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=0)
        f.write("\n")

def opt(name: str) -> str | None:
    if name in sys.argv:
        i = sys.argv.index(name)
        return sys.argv[i + 1] if i + 1 < len(sys.argv) else None
    return next((a.split("=", 1)[1] for a in sys.argv if a.startswith(name + "=")), None)

def selftest() -> None:
    payload = [{"meanings": [
        {"partOfSpeech": "noun", "definitions": [{"definition": "a reduction in amount"}]},
        {"partOfSpeech": "verb", "definitions": [{"definition": "become less intense or widespread"}]},
    ]}]
    # cross-check picks the sense matching our definition, not the first listed
    assert best_pos(payload, "Become less intense or widespread.") == "verb"
    assert best_pos(payload) == "noun"                       # no def: tie -> first wins
    # no overlap -> most-common fallback (two nouns beat one verb)
    three = [{"meanings": [
        {"partOfSpeech": "noun", "definitions": [{"definition": "xxx"}]},
        {"partOfSpeech": "verb", "definitions": [{"definition": "yyy"}]},
        {"partOfSpeech": "noun", "definitions": [{"definition": "zzz"}]},
    ]}]
    assert best_pos(three, "no shared words here") == "noun"
    assert best_pos([{"meanings": []}]) is None
    assert best_pos([]) is None
    print("selftest ok")

def main() -> None:
    if "--selftest" in sys.argv:
        return selftest()

    positional = [a for a in sys.argv[1:] if not a.startswith("--")
                  and a != opt("--limit")]
    path = positional[0] if positional else "shared/seed-words.json"
    limit = opt("--limit")
    limit = int(limit) if limit else None

    data = json.load(open(path))
    words = data["words"]
    missing = [w for w in words if not w.get("pos")]
    todo = missing[:limit] if limit else missing
    print(f"{len(missing)} of {len(words)} missing pos; processing {len(todo)}")

    filled = notfound = 0
    for i, w in enumerate(todo, 1):
        p = lookup(w["word"], w.get("definition"))
        if p:
            w["pos"] = p; filled += 1
        else:
            notfound += 1
        if i % 25 == 0:
            save(path, data)
            print(f"  {i}/{len(todo)}  filled {filled}, not found {notfound}")
        time.sleep(0.15)                                 # be polite to a free API

    save(path, data)
    still = len([w for w in words if not w.get("pos")])
    print(f"done: filled {filled}, not found {notfound}, still missing {still}")

if __name__ == "__main__":
    main()
