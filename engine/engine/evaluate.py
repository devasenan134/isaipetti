"""The evaluation harness: every ranker, the same examples, the same split, the same numbers.

Split by time, like real life: train on the earlier TRAIN_SHARE of labelled examples, test on the rest.
  auc       how often a listened song is ranked above a skipped one (0.5 = coin toss, 1 = perfect)
  log_loss  how well the probabilities themselves fit (lower is better; base_rate sets the bar)
  brier     mean squared error of the probabilities (lower is better)
Results are also split by where the song came from: a suggestion (mix, station...) or a play outside mixes.
"""
import numpy as np
from sklearn.metrics import brier_score_loss, log_loss, roc_auc_score

from engine.data import Data
from engine.events import Example
from engine.models import RANKERS

TRAIN_SHARE = 0.8


def metrics(y: np.ndarray, p: np.ndarray) -> dict:
    p = np.clip(p, 1e-6, 1 - 1e-6)
    out = {"n": int(len(y)), "listen_rate": round(float(y.mean()), 3) if len(y) else None}
    if len(y) and len(set(y)) > 1:
        out |= {"auc": round(float(roc_auc_score(y, p)), 3), "log_loss": round(float(log_loss(y, p)), 3),
                "brier": round(float(brier_score_loss(y, p)), 3)}
    return out


def evaluate(examples: list[Example], data: Data, names: list[str] | None = None) -> dict:
    labelled = [e for e in examples if e.label is not None and e.song_id in data.songs]
    cut = int(len(labelled) * TRAIN_SHARE)
    train, test = labelled[:cut], labelled[cut:]
    y = np.array([e.label for e in test])
    groups = {"all": np.ones(len(test), bool), "suggested": np.array([e.position is not None for e in test], bool),
              "outside_mixes": np.array([e.mix_id is None for e in test], bool)}
    report = {"examples": len(examples), "labelled_known_songs": len(labelled), "train": len(train), "test": len(test),
              "rankers": {}}
    for name in names or list(RANKERS):
        ranker = RANKERS[name]()
        ranker.fit(train, data)
        p = np.asarray(ranker.predict(test, data, train), dtype=float)
        report["rankers"][name] = {g: metrics(y[m], p[m]) for g, m in groups.items() if m.any()}
        if hasattr(ranker, "weights"):
            report["rankers"][name]["weights"] = ranker.weights()
    return report
