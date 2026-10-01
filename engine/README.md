# Isai Pettai engine

The recommendation and playlist engine: it understands each song, learns each person's taste, learns from
skips, and (from phase 4) builds the mixes and stations the app shows. Python, FastAPI, SQLite.

The Kotlin server (`server/`) keeps sign-in, chat and listen-together, and records what it suggested and what
was played. The engine reads that, plus Navidrome's and the analyzer's databases, and keeps what it works out
in its own `engine.db`.

## Layout

```
engine/
  config.py        where the databases are (environment variables, see docker-compose.yml)
  db.py            engine.db: songs, lyrics, song_links, song_tags, song_vectors, runs (migrations like Db.kt)
  data.py          everything a model may look at, loaded into memory
  events.py        training examples: what happened to each suggestion and each play
  evaluate.py      the harness: every ranker, same examples, same time split, same numbers
  jobs/            offline jobs (import_signals; Gemini labelling and embeddings come next)
  models/          rankers: base.py (the contract), features.py, baselines.py, and yours
  api.py, cli.py   the HTTP API and the `engine` command
tests/
```

## Running it

On your laptop, with copies of the databases (never the live files):

```bash
cd engine
uv sync --group dev
uv run pytest
ENGINE_DB=local/engine.db SOCIAL_DB=local/isaipetti-social.db SIGNALS_DIR=local/signals uv run engine import-signals
ENGINE_DB=local/engine.db SOCIAL_DB=local/isaipetti-social.db uv run engine evaluate
```

On the home server it runs from `/mnt/ugreen/isaipetti-engine` with `docker compose up -d --build`; jobs run
with `docker compose run --rm isaipetti-engine engine <command>`. Copy `.env.example` to `.env` (chmod 600).

## Writing a model

A ranker answers: will this person listen to this song (1) or skip it (0)? See `models/base.py`:

```python
class MyRanker:
    name = "my_ranker"
    def fit(self, train, data): ...                    # labelled Examples, oldest first
    def predict(self, examples, data, history): ...    # → numpy array of P(listen)
```

Register it in `models/__init__.py`, then `engine evaluate` shows it beside the baselines:

- `base_rate`: everyone listens with the same chance. Anything worse than this is broken.
- `taste_lr`: logistic regression on taste features (composer, singer and film affinity, listened-together
  with recent songs, popularity, heard or skipped before). Roughly what today's MixMaker does by hand.
  **This is the one to beat.**

Rules that keep the numbers honest:
- Only use what was known **before** each example's time. `TasteFeatures` shows how: walk through examples in
  time order and update the counts after each one.
- Compare on the same split (`evaluate.py` splits by time: train on the earlier 80%, test on the rest).
- With few plays, differences of a few hundredths of AUC are noise. Look at the number of test examples.

## What the data means

| Table | What's in it |
|---|---|
| `songs` | every live song: Navidrome id (what the app uses), film, year, composer, singers, JioSaavn ids, play count on JioSaavn |
| `lyrics` | per source (`lrclib`, `saavn`, `t2l`) and script (`ta` Tamil, `en` English letters); `synced` = has timestamps |
| `song_links` | songs that go together; `saavn_reco`: JioSaavn's listeners play `dst` after `src` (rank 1 strongest) |
| `song_tags` | moods, themes, axes per source: `t2l` (tamil2lyrics editors), `gemini`, `model:<name>` |
| `song_vectors` | float32 vectors per song and name (lyrics embeddings, listened-together embeddings…) |

Example outcomes (`events.py`): `listened` (1), `skip_early` (< 30 s), `skip_late`, `passed_over`
(suggested, not played, a later song of the list was) all 0, and `not_reached` (no label).

## Plan

| Phase | What | Who |
|---|---|---|
| 0 | Record every suggestion (server schema 18), relink old plays, this skeleton, the harness | Claude |
| 1 | Lyrics files for the app; Gemini labels (teacher); lyrics and listened-together vectors; the mood/theme model (student) | data: Claude, models: you |
| 2 | Taste profiles and candidate generation | you design, Claude wires in |
| 3 | The ranker (skip model), beating `taste_lr` | you |
| 4 | Playlist builder and stations served by the engine; MixMaker hands over | Claude |
| 5 | Old vs new engine, randomly per person and day; skip and finish rate per mix | Claude |
