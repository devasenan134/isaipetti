"""Where everything is. Inside the container these are the mounted paths; set the variables to run elsewhere."""
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Config:
    # Ours, read and written
    engine_db: Path
    # Read only: Navidrome (songs, users), the social server (plays, suggestions), the analyzer (sound)
    navidrome_db: Path
    social_db: Path
    features_db: Path
    # Read only: files the data jobs on the server collect (songs.csv, lyrics, listened-together)
    signals: Path
    # Optional, only for the Gemini labelling job; never sent anywhere else
    gemini_api_key: str | None


def load() -> Config:
    env = os.environ.get
    return Config(
        engine_db=Path(env("ENGINE_DB", "/data/engine.db")),
        navidrome_db=Path(env("NAVIDROME_DB", "/navidrome/navidrome.db")),
        social_db=Path(env("SOCIAL_DB", "/social/isaipetti-social.db")),
        features_db=Path(env("FEATURES_DB", "/features/features.db")),
        signals=Path(env("SIGNALS_DIR", "/signals")),
        gemini_api_key=env("GEMINI_API_KEY") or None,
    )
