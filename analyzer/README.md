# Part 2b (optional): the audio analyzer

The friends server makes mixes, playlists and stations by **Isai Pettai**: Daily Mixes, Discover Weekly, On Repeat, composer, singer and decade mixes, and stations that never end. With only Navidrome's tags and your listening, it groups songs by composer, singers and era.

The analyzer adds what the tags can't say: **how songs sound**. It listens to every song once, with the [CLAP](https://github.com/LAION-AI/CLAP) music model, and saves for each song:

- a *sound fingerprint*: songs that sound alike get similar ones. Stations, Daily Mixes and "Recommended songs" use them.
- how well descriptions like "a sad melancholic song" or "a fast folk dance song with thappu drums" fit it. That's where the **mood mixes** come from (Chill, Romance, Party, Kuthu, Sad Songs, Workout, Devotional, Carnatic Touch, Focus, Sleep, Retro...). Nobody has to tag moods by hand.
- tempo and energy.

It only **reads** the music and Navidrome's database. It writes just its own `features.db`, which the friends server reads.

## What you need

- Navidrome (Part 1) and the friends server (Part 2) on the same machine
- Docker; about 3 GB of free memory while it runs and 2 GB of disk for the model
- Time for the first run: roughly 1.5 seconds per song on 4 cores (an Apple M1: about 80 minutes for 3,000 songs). After that, only new or changed songs are analyzed, a few minutes after Navidrome's scan finds them.

## Run it

```bash
cd analyzer
cp .env.example .env
nano .env                  # Navidrome's data folder, your music folder, PUID/PGID
mkdir -p data
docker compose up -d --build
docker logs -f isaipetti-analyzer    # "Analyzing 3061 new or changed songs", then progress every 20 songs
```

Then tell the friends server where both folders are, in `server/.env`:

```bash
NAVIDROME_DATA=/path/to/navidrome/data
FEATURES_FOLDER=/path/to/isaipetti/analyzer/data
```

and restart it (`docker compose up -d` in `server/`). Its log line ends with `mixes on`. Mood mixes appear once a quarter of the library is analyzed; everything else works right away.

## Trying it out first

```bash
docker compose run --rm isaipetti-analyzer sample 100   # analyze 100 random songs
docker compose run --rm isaipetti-analyzer report       # which songs fit each description best, and sound-alikes
```

## Notes

- **Keeping mixes up to date.** Every 10 minutes the analyzer checks Navidrome for new or changed songs. The friends server notices new songs and new analysis within a minute and works out the mixes again, so a new song that fits a mix shows up in it on its own.
- **The model.** It uses `laion/larger_clap_general`. The music-only `laion/larger_clap_music` would fit better, but its text half is broken in the current `transformers` library (every description comes out the same). Set `CLAP_MODEL` to try another; changing it analyzes everything again.
- **Descriptions** are in `PROMPTS` at the top of `analyze.py`. Adding one computes it for every song without listening again. Mood mixes (in `server/.../MixMaker.kt`, `MOODS`) combine them.
- **Load.** It runs with low priority limits (`ANALYZER_CPUS`, default 4, and 3 GB of memory). Stop it any time with `docker compose stop`; it continues where it left off.
