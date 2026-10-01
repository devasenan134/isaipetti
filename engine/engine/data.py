"""Everything a model may look at, loaded once from engine.db into memory (28k songs fit easily)."""
import sqlite3
from collections import defaultdict
from dataclasses import dataclass, field

import numpy as np


@dataclass
class Data:
    songs: dict[str, dict]                                   # song id → its songs row
    links: dict[str, list[tuple[str, float]]]                # song id → [(linked song id, weight)], strongest first
    tags: dict[str, dict[str, float]] = field(default_factory=dict)   # song id → {"source:tag": value}
    _db: sqlite3.Connection | None = None

    def vectors(self, name: str) -> tuple[list[str], np.ndarray]:
        """All songs' vectors called [name] (e.g. "lyrics-e5"), as (song ids, matrix with one row per song)."""
        rows = self._db.execute("SELECT song_id, dims, vector FROM song_vectors WHERE name = ?", (name,)).fetchall()
        if not rows:
            return [], np.zeros((0, 0), dtype=np.float32)
        return [r[0] for r in rows], np.stack([np.frombuffer(r[2], dtype=np.float32) for r in rows])

    def lyrics(self, song_id: str) -> list[dict]:
        return [dict(source=s, script=sc, synced=bool(sy), text=t) for s, sc, sy, t in self._db.execute(
            "SELECT source, script, synced, text FROM lyrics WHERE song_id = ?", (song_id,))]


def load(db: sqlite3.Connection) -> Data:
    cols = [c[1] for c in db.execute("PRAGMA table_info(songs)")]
    songs = {r[0]: dict(zip(cols, r)) for r in db.execute("SELECT * FROM songs")}
    links = defaultdict(list)
    for src, dst, weight in db.execute("SELECT src, dst, weight FROM song_links ORDER BY src, weight DESC"):
        links[src].append((dst, weight))
    tags = defaultdict(dict)
    for song, source, tag, value in db.execute("SELECT song_id, source, tag, value FROM song_tags"):
        tags[song][f"{source}:{tag}"] = value
    return Data(songs, dict(links), dict(tags), db)
