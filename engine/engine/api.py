"""The engine's HTTP API. For now it shows what the engine knows; mixes and stations come in phase 4.

  GET /health              songs, lyrics and links loaded, and the last run of each job
  GET /songs/{id}          a song's card: facts, lyrics sources, tags, songs that go with it
"""
from fastapi import FastAPI, HTTPException

from engine import config, db as engine_db

app = FastAPI(title="Isai Pettai engine")
cfg = config.load()


def conn():
    return engine_db.connect(cfg.engine_db)


@app.get("/health")
def health():
    db = conn()
    count = lambda sql: db.execute(sql).fetchone()[0]
    runs = {job: {"finished_at": at, "info": info} for job, at, info in db.execute(
        "SELECT job, max(finished_at), info FROM runs WHERE finished_at IS NOT NULL GROUP BY job")}
    return {"songs": count("SELECT count(*) FROM songs"),
            "songs_with_lyrics": count("SELECT count(DISTINCT song_id) FROM lyrics"),
            "links": count("SELECT count(*) FROM song_links"),
            "tags": count("SELECT count(*) FROM song_tags"),
            "runs": runs}


@app.get("/songs/{song_id}")
def song(song_id: str):
    db = conn()
    cols = [c[1] for c in db.execute("PRAGMA table_info(songs)")]
    row = db.execute("SELECT * FROM songs WHERE id = ?", (song_id,)).fetchone()
    if not row:
        raise HTTPException(404, "No such song")
    links = db.execute(
        """SELECT l.dst, s.title, s.film, l.source, l.weight FROM song_links l JOIN songs s ON s.id = l.dst
           WHERE l.src = ? ORDER BY l.weight DESC LIMIT 20""", (song_id,)).fetchall()
    return {"song": dict(zip(cols, row)),
            "lyrics": [{"source": s, "script": sc, "synced": bool(sy), "lines": t.count("\n") + 1}
                       for s, sc, sy, t in db.execute("SELECT source, script, synced, text FROM lyrics WHERE song_id = ?", (song_id,))],
            "tags": [{"source": s, "tag": t, "value": v} for s, t, v in
                     db.execute("SELECT source, tag, value FROM song_tags WHERE song_id = ?", (song_id,))],
            "goes_with": [{"id": d, "title": t, "film": f, "source": s, "weight": round(w, 3)} for d, t, f, s, w in links]}
