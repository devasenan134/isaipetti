"""Training examples for the ranker: what happened to each song we suggested, and to each song people played.

One Example per (person, song, moment). The outcome comes from the social server's plays:
  listened     played to the end, or at least LISTENED_MS / half the song, not skipped          label 1
  skip_late    skipped after SKIP_EARLY_MS                                                       label 0
  skip_early   skipped within SKIP_EARLY_MS (the clearest "not for me")                          label 0
  passed_over  suggested, never played, but a later song of the same list was played            label 0
  not_reached  suggested, never played, nothing after it was played either                       no label

A suggestion is matched to a play by the same person, the play's source "mix:<mix id>", the same song,
within MATCH_WINDOW_MS after it was served. Plays that match no suggestion are examples too (position None):
someone chose that song themselves, or it came from a mix served before suggestions were recorded.
"""
import sqlite3
from dataclasses import dataclass

SKIP_EARLY_MS = 30_000
LISTENED_MS = 60_000
MATCH_WINDOW_MS = 2 * 24 * 3600 * 1000
LABEL = {"listened": 1, "skip_late": 0, "skip_early": 0, "passed_over": 0, "not_reached": None}


@dataclass
class Example:
    user_id: int
    song_id: str
    at: int                    # when it was played, or served if never played (ms)
    outcome: str
    mix_id: str | None         # "daily-1", "radio-song-…", None for a play outside mixes
    kind: str | None           # mix / radio / recommend
    position: int | None       # place in the suggested list
    played_ms: int = 0

    @property
    def label(self) -> int | None:
        return LABEL[self.outcome]


def outcome_of(played_ms: int, duration_ms: int, skipped: bool) -> str:
    if skipped:
        return "skip_early" if played_ms < SKIP_EARLY_MS else "skip_late"
    if duration_ms and played_ms < min(LISTENED_MS, duration_ms / 2):
        return "skip_early" if played_ms < SKIP_EARLY_MS else "skip_late"
    return "listened"


def load(social: sqlite3.Connection) -> list[Example]:
    plays = social.execute(
        "SELECT id, user_id, song_id, at, played_ms, duration_ms, skipped, source FROM plays ORDER BY at").fetchall()
    has_suggestions = social.execute(
        "SELECT count(*) FROM sqlite_master WHERE name = 'suggestion_lists'").fetchone()[0] > 0
    lists = social.execute(
        "SELECT id, user_id, mix_id, kind, served_at FROM suggestion_lists ORDER BY served_at").fetchall() if has_suggestions else []

    # Plays by (user, mix, song), to find each suggestion's play
    by_key: dict[tuple, list] = {}
    for p in plays:
        if p[7] and p[7].startswith("mix:"):
            by_key.setdefault((p[1], p[7][4:], p[2]), []).append(p)

    used, examples = set(), []
    for list_id, user, mix_id, kind, served_at in lists:
        items = social.execute("SELECT position, song_id FROM suggestions WHERE list_id = ? ORDER BY position", (list_id,)).fetchall()
        found = {}
        for position, song in items:
            for p in by_key.get((user, mix_id, song), []):
                if p[0] not in used and served_at <= p[3] <= served_at + MATCH_WINDOW_MS:
                    found[position] = p
                    used.add(p[0])
                    break
        last_played = max(found, default=None)
        for position, song in items:
            p = found.get(position)
            if p:
                examples.append(Example(user, song, p[3], outcome_of(p[4], p[5], bool(p[6])), mix_id, kind, position, p[4]))
            else:
                outcome = "passed_over" if last_played is not None and position < last_played else "not_reached"
                examples.append(Example(user, song, served_at, outcome, mix_id, kind, position))
    for p in plays:
        if p[0] not in used:
            mix = p[7][4:] if p[7] and p[7].startswith("mix:") else None
            examples.append(Example(p[1], p[2], p[3], outcome_of(p[4], p[5], bool(p[6])), mix, None, None, p[4]))
    examples.sort(key=lambda e: e.at)
    return examples
