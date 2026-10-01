"""Every ranker `engine evaluate` knows. Add yours here."""
from engine.models.baselines import BaseRate, TasteLR

RANKERS = {r.name: r for r in (BaseRate, TasteLR)}
