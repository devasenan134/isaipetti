"""Rankers to beat. Your models should do better than these on `engine evaluate`.

  base_rate   everyone listens with the same chance (the share of listens in training)
  taste_lr    logistic regression on TasteFeatures: composer/singer/film affinity, listened-together with
              recent songs, popularity, heard or skipped before. Roughly what today's MixMaker does by hand.
"""
import numpy as np
from sklearn.linear_model import LogisticRegression

from engine.data import Data
from engine.events import Example
from engine.models.features import NAMES, TasteFeatures


class BaseRate:
    name = "base_rate"

    def fit(self, train: list[Example], data: Data) -> None:
        self.p = float(np.mean([e.label for e in train])) if train else 0.5

    def predict(self, examples: list[Example], data: Data, history: list[Example]) -> np.ndarray:
        return np.full(len(examples), self.p)


class TasteLR:
    name = "taste_lr"

    def fit(self, train: list[Example], data: Data) -> None:
        x = TasteFeatures(data).table(train)
        y = np.array([e.label for e in train])
        self.model = LogisticRegression(C=1.0, max_iter=1000) if len(set(y)) > 1 else None
        if self.model:
            self.model.fit(x, y)
        self.p = float(y.mean()) if len(y) else 0.5

    def predict(self, examples: list[Example], data: Data, history: list[Example]) -> np.ndarray:
        # Features for the test examples come from walking through all earlier examples too.
        timeline = sorted(history + examples, key=lambda e: e.at)
        x = TasteFeatures(data).table(timeline)
        index = {id(e): i for i, e in enumerate(timeline)}
        rows = x[[index[id(e)] for e in examples]]
        return self.model.predict_proba(rows)[:, 1] if self.model else np.full(len(examples), self.p)

    def weights(self) -> dict[str, float]:
        return dict(zip(NAMES, self.model.coef_[0].round(3))) if self.model else {}
