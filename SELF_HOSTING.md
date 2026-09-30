# Hosting Isaipetti yourself

Isaipetti is an Android app that plays music from servers you run at home. There are up to
three Docker containers, each in its own folder of this repository:

| Folder | Container | What it does | Needed? |
|---|---|---|---|
| `navidrome/` | **Navidrome** | Your music library: streaming, lyrics, playlists, accounts | Yes |
| `server/` | **isaipetti-social** (the friends server) | Invites, friends, chat, listening together, notifications, mixes, song requests | Recommended |
| `analyzer/` | **isaipetti-analyzer** | Listens to every song once so mixes know moods and which songs sound alike | Optional |

The app has no server addresses built in. You give your friends two web addresses, they
type them in at login, and that's it. This guide gets you:

1. Navidrome running with your music,
2. the friends server running next to it,
3. both reachable over HTTPS from anywhere,
4. your friends signed up with invite codes,
5. optionally, push notifications, mixes by Isai Pettai, mood mixes, and bug reports from the app.

## What you need

- A computer that stays on, with [Docker](https://docs.docker.com/engine/install/) and
  Docker Compose (a home server, mini PC, NAS or Mac mini; x86 or ARM).
- Your music on that machine, in folders (one folder per movie/album is ideal).
- Two web addresses with HTTPS, for example `music.example.com` and `friends.example.com`.
  Phones stream from outside your home, and release builds of the app only talk to HTTPS
  servers. A [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
  or a reverse proxy like [Caddy](https://caddyserver.com/) gives you both; see
  [HTTPS](#https) below.
- Android phones. There is no iPhone app.

## 1. Get the code

```sh
git clone https://github.com/devasenan134/isaipetti.git
cd isaipetti
id -u; id -g        # your user and group id, usually 1000 and 1000
```

Every container runs as your own user, not root. Wherever a `.env` file asks for `PUID` and
`PGID`, use these two numbers.

## 2. Navidrome, the music server

```sh
cd navidrome
cp .env.example .env
```

Open `.env` and set:

- `MUSIC_FOLDER`: the full path to your music, e.g. `/home/you/Music`
- `PUID` and `PGID`: from step 1

Then start it:

```sh
mkdir -p data
docker compose up -d
```

Open `http://<this machine>:4533` in a browser. The first visit asks you to **create the admin
account**; use a strong password. Navidrome then scans your music. The first scan can take a
while for a big library; after that it looks for new music every hour.

### The account for the friends server

The friends server needs a Navidrome admin account of its own, to create accounts for people
who sign up with invite codes. In Navidrome, go to **Settings → Users → +**, and create:

- **Username:** `isaipetti-bot`
- **Password:** a long random one (`openssl rand -base64 24` makes one)
- **Is admin:** ticked

Don't use this account for anything else. You'll need it in the next step.

### Lyrics

Isaipetti shows synced lyrics when Navidrome has them: embedded in the song files, or an
`.lrc` file next to the song with the same name (`Song.mp3` and `Song.lrc`). New lyric files
show up after the next scan.

### Artist pictures (optional)

For artist photos and biographies, get free keys from [Last.fm](https://www.last.fm/api) and
[Spotify](https://developer.spotify.com), put them in `navidrome/.env` (`ND_LASTFM_*`,
`ND_SPOTIFY_*`), and run `docker compose up -d` again.

## 3. The friends server

Without it, the app is a plain music player. With it, you get everything social: invites,
friends and who's online, chats, sharing songs and clips, listening together, song requests
and notifications.

```sh
cd ../server
cp .env.example .env
```

Open `.env` and set:

- `NAVIDROME_ADMIN_USER` and `NAVIDROME_ADMIN_PASSWORD`: the `isaipetti-bot` account from step 2
- `PUID` and `PGID`: from step 1
- Leave `NAVIDROME_URL` as it is. It's how the container reaches Navidrome on the same machine.

Then start it:

```sh
mkdir -p data secrets
docker compose up -d --build
docker logs isaipetti-social
```

The first build takes a few minutes. The log should end with a line like
`isaipetti-social ready on port 8095`. That line also says which optional parts are on
(`push on`, `mixes on`, `feedback on`); you'll turn those on in steps 6 to 8.

The friends server only listens on this machine (`127.0.0.1:8095`), so nothing outside can
reach it until you set up HTTPS.

## 4. HTTPS

Give each server its own HTTPS address:

| Address | Points to |
|---|---|
| `https://music.example.com` | `http://localhost:4533` (Navidrome) |
| `https://friends.example.com` | `http://localhost:8095` (friends server) |

**Cloudflare Tunnel** (no open ports on your router): install `cloudflared` on the server,
create a tunnel in the Cloudflare Zero Trust dashboard, and add two **public hostnames**, one
for each row above.

**Caddy** (you have a domain pointing at your home, with ports 80 and 443 forwarded to the
server):

```
music.example.com {
    reverse_proxy localhost:4533
}

friends.example.com {
    reverse_proxy localhost:8095
}
```

Caddy gets the certificates by itself. Both options pass WebSockets through, which listening
together and live chat need.

Check it: `https://friends.example.com/health` should show `{"status":"ok"}` in a browser, and
`https://music.example.com` should show Navidrome's login page.

## 5. Install the app and invite friends

1. On your phone, download the newest `isaipetti-<version>.apk` from
   [Releases](https://github.com/devasenan134/isaipetti/releases) and open it. Allow
   installing from your browser when Android asks.
2. Open Isaipetti and enter:
   - **Music server:** `music.example.com`
   - **Friends server:** `friends.example.com`
3. Log in with your Navidrome admin account from step 2 (not the bot).

You're in. To add people, open the **Friends** tab and create an **invite code**. Send it to a
friend along with the two addresses. They install the app, tap **Got an invite code? Sign
up**, and choose a username and password. That creates their Navidrome account, and you're
friends right away. Each code works once, for 7 days, and anyone can make codes for their
own friends.

The app checks Releases for new versions and offers to install them.

**Song requests** work straight away: people search for music that isn't in the library and
tap **Request**. Navidrome admins see the requests and mark them done once the songs are added.

## 6. Push notifications (optional)

Without this, messages and invites show up when the app is open. With it, phones get
notifications. It uses Firebase Cloud Messaging with a free Firebase project of your own.

1. Go to the [Firebase console](https://console.firebase.google.com) and create a project
   (any name, e.g. "Isaipetti"). You can turn Google Analytics off.
2. **Add app → Android**, with:
   - **Package name:** `io.github.devasenan134.isaipetti`
   - **SHA-1:** `88:4E:5A:15:BA:36:A4:E8:60:71:76:61:75:A8:A7:17:DD:F9:FE:78`
     (the certificate the apps in Releases are signed with)

   Download **google-services.json**. Skip the rest of the wizard.
3. **Project settings → Service accounts → Generate new private key.** This downloads the
   server key. **It's a secret:** anyone with it can send notifications as your server.
4. Put both files in `server/secrets/` and lock them down:

   ```sh
   mv ~/Downloads/google-services.json secrets/google-services.json
   mv ~/Downloads/*-firebase-adminsdk-*.json secrets/firebase-key.json
   chmod 700 secrets && chmod 600 secrets/*
   docker compose up -d
   ```

   The log line now says `push on`. Phones pick up the settings from the friends server
   the next time they log in, so nothing about Firebase is built into the app.
5. Recommended: in the [Google Cloud console](https://console.cloud.google.com/apis/credentials),
   open the project's **Android key (auto created by Firebase)** and, under **Application
   restrictions**, allow only Android apps with the package name and SHA-1 from step 2.

If you build the app yourself (step 9), use your own package name and SHA-1 instead.

## 7. Mixes by Isai Pettai (optional)

The friends server can make mixes for everyone: Daily Mixes, Discover Weekly, On Repeat,
Rewind, New Arrivals, Friends Mix, Top 50, composer, singer and decade mixes, endless
stations, and **Recommended songs** for your playlists. They're worked out from your
library and everyone's listening, and update themselves as you listen and as music is added.

To turn them on, the friends server needs to read Navidrome's database. In `server/.env`, set:

```sh
NAVIDROME_DATA=/home/you/isaipetti/navidrome/data   # full path to Navidrome's data folder
MIX_TIMEZONE=Asia/Kolkata                           # when "today" starts for Daily Mixes
```

(Run `pwd` in the `navidrome/` folder to get its full path, then add `/data`.)

Then run `docker compose up -d` in `server/`. The log line now says `mixes on`. The friends
server only reads Navidrome's database; it never changes it.

### Mood mixes with the audio analyzer

On its own, the friends server groups songs by composer, singers and era. The analyzer adds
how songs **sound**: it listens to every song once with an AI music model, and that gives
you mood mixes (Chill, Romance, Party, Kuthu, Sad Songs, Workout, Devotional, Focus,
Sleep...) and stations that follow the sound of a song.

It needs about 3 GB of free memory while it runs and 2 GB of disk for the model. The first
run takes roughly 1.5 seconds per song on 4 CPU cores (about 80 minutes for 3,000 songs on an
Apple M1). After that, only new or changed songs are analyzed, a few minutes after
Navidrome finds them.

```sh
cd ../analyzer
cp .env.example .env
```

Open `.env` and set:

- `NAVIDROME_DATA`: the same full path as in `server/.env`
- `MUSIC_FOLDER`: the same music folder as in `navidrome/.env`
- `PUID` and `PGID`: from step 1
- `ANALYZER_CPUS`: how many CPU cores it may use (default 4)

```sh
mkdir -p data
docker compose up -d --build
docker logs -f isaipetti-analyzer
```

You'll see `Analyzing 3061 new or changed songs`, then progress every 20 songs. Now tell the
friends server where the results are. In `server/.env`:

```sh
FEATURES_FOLDER=/home/you/isaipetti/analyzer/data
```

and run `docker compose up -d` in `server/`. Mood mixes appear once a quarter of the library
is analyzed. You can stop the analyzer any time with `docker compose stop`; it continues
where it left off.

## 8. Bug reports from the app (optional)

The app's **Settings → Feedback** (**Report a bug**, **Suggest a feature**) can create issues
in a GitHub repository of yours, through the friends server, so no token ships inside the
app.

1. Create a [fine-grained token](https://github.com/settings/personal-access-tokens/new)
   for just that repository, with only **Issues: Read and write**.
2. In `server/.env`, set `GITHUB_REPO=you/your-repo` and `GITHUB_TOKEN=` the token.
3. Run `docker compose up -d` in `server/`. The log line says `feedback on`.

Issues don't say who sent them.

## 9. Building the app yourself (optional)

You don't need to: the APKs in Releases work with anyone's servers. Build your own if you
want to change the app. See [android/README.md](android/README.md#build-it-yourself) for
the build steps and signing key. If you hand out your own version:

- Change `applicationId` in `android/app/build.gradle.kts` so it doesn't clash with this app,
  and add that package name and your key's SHA-1 to your Firebase project (step 6).
- Set `ISAIPETTI_GITHUB_REPO=you/your-repo` in `~/.gradle/gradle.properties`, so the app's
  update check looks at your Releases.

## Backups

Everything that matters is in three `data/` folders:

| Folder | What's in it |
|---|---|
| `navidrome/data/` | Accounts, playlists, likes, play counts |
| `server/data/` | Friends, chats, photos and voice messages, jams, requests |
| `analyzer/data/` | `features.db` (can be rebuilt by analyzing again, just slowly) |

Plus `server/secrets/` if you set up notifications. Your music folder is yours to back up
as usual.

To make a clean copy, stop the containers for a moment, copy the folders, and start them again:

```sh
for d in navidrome server analyzer; do (cd $d && docker compose stop); done
tar czf ~/isaipetti-backup-$(date +%F).tar.gz navidrome/data server/data server/secrets analyzer/data
for d in navidrome server analyzer; do (cd $d && docker compose start); done
```

(Leave out `analyzer` if you don't run it.)

## Updating

```sh
git pull
cd navidrome && docker compose pull && docker compose up -d && cd ..
cd server    && docker compose up -d --build && cd ..
cd analyzer  && docker compose up -d --build && cd ..    # if you run it
```

The friends server updates its database by itself when a new version starts. Back up first
anyway. The app updates itself from Releases.

## Keeping it safe

- `.env`, `data/` and `secrets/` stay on the server; git ignores them. Never commit them.
- Use strong passwords, especially for admin accounts. Navidrome and the friends server both
  limit repeated login attempts.
- Navidrome's port 4533 is open to your home network. The friends server's port only to the
  machine itself. Use your firewall if you want to close 4533 to the network too.
- Keep Navidrome updated (see [Updating](#updating)).

## If something doesn't work

- **The app says it can't reach a server:** on the phone's browser, open the music address and
  the friends address followed by `/health`. If they don't load, the problem is HTTPS (step 4), not the app.
- **Sign-up with an invite code fails:** check `NAVIDROME_ADMIN_USER` and
  `NAVIDROME_ADMIN_PASSWORD` in `server/.env`, and that the bot account is an admin.
- **No mixes:** the log line must say `mixes on`. If it doesn't, `NAVIDROME_DATA` isn't the
  folder that contains `navidrome.db`.
- **"Permission denied" in a log:** `PUID`/`PGID` don't match the owner of the `data/`
  folders. Fix it with `sudo chown -R $(id -u):$(id -g) data`.
- **Anything else:** `docker logs navidrome`, `docker logs isaipetti-social` and
  `docker logs isaipetti-analyzer` usually say what's wrong.

## Settings reference

Each folder has a `.env.example` with a comment on every setting:
[navidrome/](navidrome/.env.example), [server/](server/.env.example),
[analyzer/](analyzer/.env.example).
