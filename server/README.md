# Part 2: isaipetti-social, the friends server

The friends server adds the social side of Isaipetti: invite codes and sign-up, friends and who's online, what everyone is listening to, chats, song and clip sharing, listening together, push notifications and bug reports. It keeps its own small SQLite database and checks every login against Navidrome, so there are no extra passwords.

It's optional: without it, the app is a plain Navidrome player.

## What you need

- Navidrome running (Part 1), with an admin account for this server
- Docker and Docker Compose on the same machine
- An HTTPS address for this server, like `https://friends.example.com`

## Run it

```bash
cd server
cp .env.example .env
nano .env                  # Navidrome address, the bot admin account, PUID/PGID
mkdir -p data secrets
docker compose up -d --build
docker logs isaipetti-social   # should end with "isaipetti-social ready on port 8095"
```

The server listens on `127.0.0.1:8095` only. Publish it over HTTPS the same way as Navidrome (a Cloudflare Tunnel hostname or a reverse proxy pointing to `http://localhost:8095`). That HTTPS address is the **Friends server** people type in the app.

The first person logs in with an existing Navidrome account. From then on, anyone can invite friends from the app's Friends tab. An invite code works once, for 7 days, and creates the new person's Navidrome account.

## Push notifications (optional)

Notifications go through Firebase Cloud Messaging, with **your own** Firebase project. Nothing about it is built into the app: the app asks this server for the settings after login.

1. In the [Firebase console](https://console.firebase.google.com), create a project.
2. **Add app → Android** with package name `io.github.devasenan134.isaipetti` (or your own, if you build the app with a different id). Add the SHA-1 fingerprint of the certificate the app is signed with. Download **google-services.json**.
3. **Project settings → Service accounts → Generate new private key.** This downloads the server key. **It's a secret.**
4. Put both files in `secrets/` and lock them down:

   ```bash
   mv ~/Downloads/google-services.json secrets/google-services.json
   mv ~/Downloads/*-firebase-adminsdk-*.json secrets/firebase-key.json
   chmod 700 secrets && chmod 600 secrets/*
   docker compose up -d
   ```

   The log line now says `push on`.
5. Recommended: in the [Google Cloud console](https://console.cloud.google.com/apis/credentials), open the project's **Android key (auto created by Firebase)** and under *Application restrictions* allow only your app's package name and SHA-1.

## Bug reports (optional)

The app's *Settings → Report a bug* creates GitHub issues through this server, so the token never ships inside the app. Create a [fine-grained token](https://github.com/settings/personal-access-tokens/new) for just your repository with only **Issues: Read and write**, then set `GITHUB_REPO=owner/repo` and `GITHUB_TOKEN=...` in `.env` and restart. Issues don't say who sent them.

## Updating

```bash
git pull
docker compose up -d --build
```

The database updates itself when a new version starts. To back it up, copy `data/` while the server is stopped (`docker compose stop`).

## Safety notes

- `.env`, `data/` and `secrets/` never leave the server and are ignored by git.
- The container runs as your user, not root, and is only reachable from the machine itself.
- Logins and sign-ups are rate-limited per address, session tokens are stored only as hashes, and oversized requests are refused.

## Development

```bash
./gradlew test                                        # the whole flow against a fake Navidrome
./gradlew runDev -PnavidromeUrl=https://music.example.com  # a local copy on port 8095
```

Kotlin, Ktor and SQLite. Code is in `src/main/kotlin/io/github/devasenan134/isaipetti/server/`.
