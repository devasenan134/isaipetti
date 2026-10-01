"""Taste features computed from someone's earlier listening only, walking through examples in time order.

TasteFeatures(data).table(examples) gives one row of numbers per example, using for each example only the
examples before it. Reuse it in your own rankers, or copy it as a starting point.
"""
import math
from collections import defaultdict, deque

import numpy as np

from engine.data import Data
from engine.events import Example

NAMES = ["composer", "singer", "film", "together", "popularity", "heard_before", "skipped_before"]


def split_names(s: str | None) -> list[str]:
    return [p.strip().lower() for p in (s or "").split(",") if p.strip()]


class TasteFeatures:
    def __init__(self, data: Data, recent: int = 20):
        self.data, self.recent = data, recent

    def table(self, examples: list[Example]) -> np.ndarray:
        """Rows in the order of [examples] (which must be sorted by time)."""
        net = defaultdict(lambda: [0, 0])          # (user, "composer:x") → [listens, skips]
        recent = defaultdict(lambda: deque(maxlen=self.recent))
        rows = []
        for e in examples:
            song = self.data.songs.get(e.song_id, {})
            u = e.user_id

            def score(keys):
                vals = [(net[(u, k)][0] - net[(u, k)][1]) / (sum(net[(u, k)]) + 2) for k in keys]
                return max(vals, default=0.0)

            composers = [f"c:{c}" for c in split_names(song.get("composer"))]
            singers = [f"s:{s}" for s in split_names(song.get("singers"))]
            film = [f"f:{song['film'].lower()}"] if song.get("film") else []
            together = 0.0
            for prev in recent[u]:
                together += sum(w for dst, w in self.data.links.get(prev, []) if dst == e.song_id)
                together += sum(w for dst, w in self.data.links.get(e.song_id, []) if dst == prev)
            heard = net[(u, f"song:{e.song_id}")]
            rows.append([score(composers), score(singers), score(film), together,
                         math.log1p(song.get("saavn_play_count") or 0) / 15, float(heard[0] > 0), float(heard[1] > 0)])
            # Only now does this example become history for the ones after it
            if e.label is not None:
                for k in composers + singers + film + [f"song:{e.song_id}"]:
                    net[(u, k)][0 if e.label else 1] += 1
                if e.label:
                    recent[u].append(e.song_id)
        return np.array(rows, dtype=np.float32).reshape(len(rows), len(NAMES))
