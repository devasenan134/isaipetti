"""The contract every ranker follows. Write yours as a class with these three things and register it in
models/__init__.py; `engine evaluate` then compares it with the others on the same examples.

A ranker answers one question: will this person listen to this song (label 1) or skip it (label 0)?
It must only use what was known before each example's time (examples are sorted by time; never peek at
later ones when computing features for an earlier one).
"""
from typing import Protocol

import numpy as np

from engine.data import Data
from engine.events import Example


class Ranker(Protocol):
    name: str

    def fit(self, train: list[Example], data: Data) -> None:
        """Learn from labelled examples (all have a label)."""

    def predict(self, examples: list[Example], data: Data, history: list[Example]) -> np.ndarray:
        """P(listen) for each example. [history] is every earlier example (train set + the test examples
        before each one), for features like "how often did they skip this composer so far"."""
