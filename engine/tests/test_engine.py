import csv
import json
import sqlite3

from engine import data as engine_data, db as engine_db, events
from engine.config import Config
from engine.evaluate import evaluate
from engine.jobs import import_signals

DAY = 24 * 3600 * 1000


def social_db(plays, lists):
    """A social server database with just plays and suggestions: lists = [(mix_id, kind, served_at, [song ids])]."""
    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE plays (id INTEGER PRIMARY KEY, user_id INTEGER, song_id TEXT, at INTEGER, played_ms INTEGER,
                            duration_ms INTEGER, skipped INTEGER, source TEXT);
        CREATE TABLE suggestion_lists (id INTEGER PRIMARY KEY, user_id INTEGER, mix_id TEXT, kind TEXT,
                                       songs_hash INTEGER, first_position INTEGER, served_at INTEGER);
        CREATE TABLE suggestions (list_id INTEGER, position INTEGER, song_id TEXT);
    """)
    for mix_id, kind, served_at, songs in lists:
        lid = db.execute("INSERT INTO suggestion_lists (user_id, mix_id, kind, songs_hash, first_position, served_at) "
                         "VALUES (1, ?, ?, 0, 0, ?)", (mix_id, kind, served_at)).lastrowid
        db.executemany("INSERT INTO suggestions VALUES (?, ?, ?)", [(lid, i, s) for i, s in enumerate(songs)])
    db.executemany("INSERT INTO plays (user_id, song_id, at, played_ms, duration_ms, skipped, source) VALUES (1, ?, ?, ?, ?, ?, ?)", plays)
    return db


def test_suggestions_get_outcomes_from_plays():
    t = 10 * DAY
    social = social_db(
        plays=[("a", t + 1, 240_000, 240_000, 0, "mix:daily-1"),       # listened
               ("c", t + 2, 10_000, 240_000, 1, "mix:daily-1"),        # skipped early (b was jumped over)
               ("x", t + 3, 200_000, 240_000, 0, None),                # their own choice
               ("e", t + 4, 90_000, 240_000, 1, "mix:daily-1")],       # skipped late
        lists=[("daily-1", "mix", t, ["a", "b", "c", "d", "e", "f"])])
    by_song = {(e.song_id, e.position): e.outcome for e in events.load(social)}
    assert by_song == {("a", 0): "listened", ("b", 1): "passed_over", ("c", 2): "skip_early", ("d", 3): "passed_over",
                       ("e", 4): "skip_late", ("f", 5): "not_reached", ("x", None): "listened"}


def test_a_play_long_after_the_mix_was_served_is_not_its_suggestion():
    social = social_db(plays=[("a", 10 * DAY, 240_000, 240_000, 0, "mix:daily-1")],
                       lists=[("daily-1", "mix", 1 * DAY, ["a"])])
    outcomes = {(e.position, e.outcome) for e in events.load(social)}
    assert outcomes == {(0, "not_reached"), (None, "listened")}


def write_signals(tmp_path):
    signals = tmp_path / "signals"
    (signals / "t2l").mkdir(parents=True)
    with open(signals / "songs.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["navidrome_id", "path", "title", "album", "film", "year", "composer", "singers",
                                          "kind", "duration", "saavn_id", "saavn_album_id", "language", "saavn_play_count"])
        w.writeheader()
        for i in range(4):
            w.writerow({"navidrome_id": f"s{i}", "path": f"F (2000)/0{i} - T{i}.m4a", "title": f"T{i}", "album": "F",
                        "film": "F", "year": "2000", "composer": "Ilaiyaraaja" if i < 2 else "Deva", "singers": "S",
                        "kind": "album", "duration": "240", "saavn_id": f"sv{i}", "saavn_album_id": "1",
                        "language": "tamil", "saavn_play_count": str(1000 * i)})
    (signals / "lrclib.jsonl").write_text(json.dumps({"path": "F (2000)/00 - T0.m4a", "status": "synced", "lyrics": "[00:01.00] la la"}) + "\n")
    (signals / "saavn_lyrics.jsonl").write_text(json.dumps({"saavn_id": "sv1", "lyrics": "Nee sirichaalum"}) + "\n")
    (signals / "t2l" / "songs.jsonl").write_text(json.dumps(
        {"navidrome_ids": ["s1"], "tamil": "காதல் காதல்", "english": "Kaadhal kaadhal", "moods": ["romantic"]}, ensure_ascii=False) + "\n")
    (signals / "saavn_reco.jsonl").write_text(json.dumps({"saavn_id": "sv0", "reco": [{"id": "sv1"}, {"id": "zz"}, {"id": "sv2"}]}) + "\n")
    return signals


def test_import_signals_and_evaluate(tmp_path):
    signals = write_signals(tmp_path)
    cfg = Config(tmp_path / "engine.db", tmp_path / "nd.db", tmp_path / "social.db", tmp_path / "f.db", signals, None)
    db = engine_db.connect(cfg.engine_db)
    info = import_signals.main(cfg, db)
    assert info["songs"] == 4
    assert info["lyrics"] == {"lrclib:en": 1, "saavn:en": 1, "t2l:en": 1, "t2l:ta": 1}
    assert info["t2l_mood_tags"] == 1 and info["saavn_links"] == 2 and info["saavn_reco_outside_library"] == 1
    assert db.execute("SELECT rank FROM song_links WHERE src = 's0' AND dst = 's2'").fetchone()[0] == 3
    # Running again replaces rather than duplicates
    import_signals.main(cfg, db)
    assert db.execute("SELECT count(*) FROM lyrics").fetchone()[0] == 4

    # Someone who loves Ilaiyaraaja and skips Deva: taste_lr should rank like that.
    t, plays = 10 * DAY, []
    for k in range(40):
        song = f"s{k % 4}"
        listened = song in ("s0", "s1")
        plays.append((song, t + k * 60_000, 240_000 if listened else 5_000, 240_000, 0 if listened else 1, None))
    report = evaluate(events.load(social_db(plays, [])), engine_data.load(db))
    assert report["train"] == 32 and report["test"] == 8
    assert report["rankers"]["taste_lr"]["all"]["auc"] == 1.0
    assert report["rankers"]["taste_lr"]["weights"]["composer"] > 0
