"""engine.db: what the engine knows about songs (the "song card") and what it learned.

Migrations work like the Kotlin server's Db.kt: PRAGMA user_version says how many of SCHEMA have run, and
opening the database runs the rest. Add a new entry at the end; never edit one that has shipped.
"""
import sqlite3
from pathlib import Path

SCHEMA = [
    # 1. The song card. songs mirrors Navidrome's live songs plus where each came from (JioSaavn).
    """
    CREATE TABLE songs (
        id TEXT PRIMARY KEY,            -- Navidrome's song id, what the app and the social server use
        path TEXT NOT NULL,
        title TEXT NOT NULL,
        album TEXT,
        film TEXT,
        year INTEGER,
        composer TEXT,
        singers TEXT,
        kind TEXT,                      -- album / score / single (the JioSaavn album it came from)
        duration INTEGER,
        saavn_id TEXT,
        saavn_album_id TEXT,
        language TEXT,
        saavn_play_count INTEGER
    );
    CREATE INDEX songs_by_saavn ON songs(saavn_id);

    -- Lyrics from every source, kept apart: script 'ta' (Tamil) or 'en' (English letters).
    CREATE TABLE lyrics (
        song_id TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
        source TEXT NOT NULL,           -- lrclib / saavn / t2l
        script TEXT NOT NULL,
        synced INTEGER NOT NULL,
        text TEXT NOT NULL,
        PRIMARY KEY (song_id, source, script)
    );

    -- Songs that go together, one row per direction. source 'saavn_reco': JioSaavn's listeners also
    -- play dst after src (rank 1 = strongest). Later: our own playlists and listening sessions.
    CREATE TABLE song_links (
        src TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
        dst TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
        source TEXT NOT NULL,
        rank INTEGER,
        weight REAL NOT NULL,
        PRIMARY KEY (src, dst, source)
    );
    CREATE INDEX song_links_by_dst ON song_links(dst);

    -- Labels and scores per song: tag is a mood, theme or situation ("romantic", "valence"...), value
    -- 0..1 (or -1..1 for an axis). source says who said so: 't2l' (tamil2lyrics' editors), 'gemini',
    -- or 'model:<name>' for a model's predictions.
    CREATE TABLE song_tags (
        song_id TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
        source TEXT NOT NULL,
        tag TEXT NOT NULL,
        value REAL NOT NULL,
        PRIMARY KEY (song_id, source, tag)
    );

    -- Vectors per song (lyrics meaning, listened-together...), float32 bytes. name says which.
    CREATE TABLE song_vectors (
        song_id TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
        name TEXT NOT NULL,
        dims INTEGER NOT NULL,
        vector BLOB NOT NULL,
        PRIMARY KEY (song_id, name)
    );

    -- Every job run, for "when was this last updated" and debugging.
    CREATE TABLE runs (
        id INTEGER PRIMARY KEY,
        job TEXT NOT NULL,
        started_at INTEGER NOT NULL,
        finished_at INTEGER,
        info TEXT
    )
    """,
]


def connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path)
    db.execute("PRAGMA journal_mode = WAL")
    db.execute("PRAGMA foreign_keys = ON")
    version = db.execute("PRAGMA user_version").fetchone()[0]
    for i, script in enumerate(SCHEMA[version:], start=version + 1):
        with db:
            db.executescript(script)
            db.execute(f"PRAGMA user_version = {i}")
    return db


def read_only(path: Path) -> sqlite3.Connection:
    """Another program's database (Navidrome, the social server, the analyzer), without disturbing it."""
    return sqlite3.connect(f"file:{path}?mode=ro", uri=True)
