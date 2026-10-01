"""Offline jobs. Each one reads its inputs, writes engine.db, and records itself in runs."""
import json
import time
from contextlib import contextmanager


@contextmanager
def run(db, job: str):
    """with run(db, "import_signals") as info: ... ; info is a dict saved with the run."""
    info: dict = {}
    run_id = db.execute("INSERT INTO runs (job, started_at) VALUES (?, ?)", (job, int(time.time() * 1000))).lastrowid
    db.commit()
    try:
        yield info
    finally:
        db.execute("UPDATE runs SET finished_at = ?, info = ? WHERE id = ?",
                   (int(time.time() * 1000), json.dumps(info, ensure_ascii=False), run_id))
        db.commit()
