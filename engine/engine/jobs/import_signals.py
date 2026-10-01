"""Load what the data jobs collected (the signals folder) into engine.db.

  songs.csv              the live songs, Navidrome id ↔ JioSaavn id          → songs
  lrclib.jsonl           LRCLIB lookups (only confirmed synced/plain ones)     → lyrics
  saavn_lyrics.jsonl     JioSaavn's lyrics (English letters)                   → lyrics
  t2l/songs.jsonl        tamil2lyrics pages (Tamil + English, mood tags)       → lyrics, song_tags
  saavn_reco.jsonl       JioSaavn's "listeners also play"                      → song_links

Missing files are skipped, so it can run while the collectors are still going. Every run replaces what the
previous run loaded from the same source.
"""
import csv
import json
import re
from collections import defaultdict
from pathlib import Path

from engine.jobs import run

TAMIL = re.compile(r"[஀-௿]")


def script_of(text: str) -> str:
    letters = [c for c in text if c.isalpha()]
    return "ta" if letters and sum(bool(TAMIL.match(c)) for c in letters) > len(letters) / 2 else "en"


def jsonl(path: Path):
    if not path.exists():
        return
    with path.open(encoding="utf-8") as f:
        for line in f:
            if line.strip():
                yield json.loads(line)


def import_songs(db, signals: Path, info: dict):
    rows = list(csv.DictReader(open(signals / "songs.csv", encoding="utf-8")))
    num = lambda v: int(v) if v and v.isdigit() else None
    db.execute("CREATE TEMP TABLE live (id TEXT PRIMARY KEY)")
    db.executemany("INSERT INTO live VALUES (?)", [(r["navidrome_id"],) for r in rows])
    info["songs_removed"] = db.execute("DELETE FROM songs WHERE id NOT IN (SELECT id FROM live)").rowcount
    db.executemany(
        """INSERT INTO songs (id, path, title, album, film, year, composer, singers, kind, duration, saavn_id,
                              saavn_album_id, language, saavn_play_count)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT (id) DO UPDATE SET path = excluded.path, title = excluded.title, album = excluded.album,
               film = excluded.film, year = excluded.year, composer = excluded.composer, singers = excluded.singers,
               kind = excluded.kind, duration = excluded.duration, saavn_id = excluded.saavn_id,
               saavn_album_id = excluded.saavn_album_id, language = excluded.language,
               saavn_play_count = excluded.saavn_play_count""",
        [(r["navidrome_id"], r["path"], r["title"], r["album"], r["film"], num(r["year"]), r["composer"], r["singers"],
          r["kind"], num(r["duration"]), r["saavn_id"] or None, r["saavn_album_id"] or None, r["language"] or None,
          num(r["saavn_play_count"])) for r in rows],
    )
    db.execute("DROP TABLE live")
    info["songs"] = len(rows)


def import_lyrics(db, signals: Path, info: dict):
    by_path = dict(db.execute("SELECT path, id FROM songs"))
    by_saavn = defaultdict(list)
    for sid, saavn in db.execute("SELECT id, saavn_id FROM songs WHERE saavn_id IS NOT NULL"):
        by_saavn[saavn].append(sid)
    known = {i for (i,) in db.execute("SELECT id FROM songs")}
    rows = []
    for r in jsonl(signals / "lrclib.jsonl"):
        if r.get("status") in ("synced", "plain") and r["path"] in by_path and r.get("lyrics", "").strip():
            rows.append((by_path[r["path"]], "lrclib", script_of(r["lyrics"]), int(r["status"] == "synced"), r["lyrics"].strip()))
    for r in jsonl(signals / "saavn_lyrics.jsonl"):
        for sid in by_saavn.get(r["saavn_id"], []):
            if r.get("lyrics", "").strip():
                rows.append((sid, "saavn", script_of(r["lyrics"]), 0, r["lyrics"].strip()))
    tags = []
    for r in jsonl(signals / "t2l" / "songs.jsonl"):
        for sid in set(r.get("navidrome_ids", [])) & known:
            for lang in ("tamil", "english"):
                text = (r.get(lang) or "").strip()
                if text:
                    rows.append((sid, "t2l", script_of(text), 0, text))
            tags += [(sid, "t2l", mood, 1.0) for mood in r.get("moods", [])]
    for source in ("lrclib", "saavn", "t2l"):
        db.execute("DELETE FROM lyrics WHERE source = ?", (source,))
    db.executemany("INSERT OR REPLACE INTO lyrics VALUES (?, ?, ?, ?, ?)", rows)
    db.execute("DELETE FROM song_tags WHERE source = 't2l'")
    db.executemany("INSERT OR REPLACE INTO song_tags VALUES (?, ?, ?, ?)", tags)
    info["lyrics"] = {s: n for s, n in db.execute("SELECT source || ':' || script, count(*) FROM lyrics GROUP BY 1")}
    info["songs_with_lyrics"] = db.execute("SELECT count(DISTINCT song_id) FROM lyrics").fetchone()[0]
    info["t2l_mood_tags"] = len(tags)


def import_links(db, signals: Path, info: dict):
    by_saavn = defaultdict(list)
    for sid, saavn in db.execute("SELECT id, saavn_id FROM songs WHERE saavn_id IS NOT NULL"):
        by_saavn[saavn].append(sid)
    links, outside = {}, 0
    for r in jsonl(signals / "saavn_reco.jsonl"):
        for rank, other in enumerate(r.get("reco", []), start=1):
            targets = by_saavn.get(other.get("id"), [])
            outside += not targets
            for src in by_saavn.get(r["saavn_id"], []):
                for dst in targets:
                    if src != dst:
                        links[(src, dst)] = (rank, 1.0 / rank ** 0.5)
    db.execute("DELETE FROM song_links WHERE source = 'saavn_reco'")
    db.executemany("INSERT INTO song_links VALUES (?, ?, 'saavn_reco', ?, ?)",
                   [(s, d, rank, w) for (s, d), (rank, w) in links.items()])
    info["saavn_links"] = len(links)
    info["saavn_reco_outside_library"] = outside


def main(cfg, db):
    with run(db, "import_signals") as info:
        import_songs(db, cfg.signals, info)
        import_lyrics(db, cfg.signals, info)
        import_links(db, cfg.signals, info)
        db.commit()
    return info
