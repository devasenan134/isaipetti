# Isaipetti (இசைப்பெட்டி)

An Android music player for your own [Navidrome](https://www.navidrome.org/) server, with friends built in: see what friends are listening to, chat, share songs (or just a part of one), and listen together in sync.

- **Player:** home rows, movies (albums), composers, search, background playback, queue, synced lyrics, swipe between songs, scrobbling
- **Friends:** invite codes, friend requests, live presence and now-playing, direct messages and group chats, push notifications
- **Sharing:** send a song, or pick a start and end point and send just that part
- **Listen together:** everyone in a chat's session hears the same music, and anyone in it can play, pause, skip or change the queue

Written in Kotlin: the app uses Jetpack Compose and Media3; the companion server uses Ktor and SQLite.

See [CHANGELOG.md](CHANGELOG.md) for what's in each release and [ROADMAP.md](ROADMAP.md) for what's planned.

## Install

Download the latest `isaipetti-<version>.apk` from [Releases](../../releases) and open it on your phone (allow installing from your browser or file manager when asked).

To get updates automatically, install [Obtainium](https://github.com/ImranR98/Obtainium), tap **Add app** and paste this repository's address. It checks Releases for new versions and installs them.

You need an account on a Navidrome server. Friends features also need the companion server below; ask whoever runs your server for an invite code.

## How it fits together

```
Isaipetti app ──── music, lyrics, playlists ────▶ Navidrome (Subsonic API)
      │
      └──── friends, chat, listen together ────▶ companion server "isaipetti-social" (server/)
                                                   └─ checks logins with Navidrome
```

## Build the app

Requires JDK 17+ and the Android SDK. Server addresses and signing keys aren't in the repository; put them in `~/.gradle/gradle.properties`:

```properties
ISAIPETTI_SERVER_URL=https://music.example.com      # your Navidrome, pre-filled on the login screen
ISAIPETTI_SOCIAL_URL=https://friends.example.com    # the companion server (friends, chat, listen together)

# Only for signed release builds:
ISAIPETTI_KEYSTORE=/path/to/isaipetti-release.jks
ISAIPETTI_KEYSTORE_PASSWORD=...
ISAIPETTI_KEY_ALIAS=isaipetti
```

Then:

```bash
./gradlew assembleDebug     # or assembleRelease for a signed build
```

The APK ends up in `app/build/outputs/apk/`.

Push notifications use Firebase. Create a Firebase project with an Android app for `io.github.devasenan134.isaipetti` and put its `google-services.json` in `app/`. Without it the app builds and works, just without notifications.

## Run the companion server

The server lives in [`server/`](server/) and runs with Docker next to Navidrome.

1. Copy `server/.env.example` to `server/.env` and fill in a Navidrome admin account for the server to use (it creates accounts for invited people and checks for deleted ones).
2. Optional, for push notifications: put a Firebase service-account key at `server/firebase-key.json`.
3. Start it:

   ```bash
   cd server && docker compose up -d --build
   ```

It listens on `127.0.0.1:8095`; put it behind HTTPS (for example a Cloudflare tunnel or a reverse proxy). Tests: `cd server && ./gradlew test`.

## License

Isaipetti is free software under the [GNU General Public License v3.0](LICENSE). You can use, study, change and share it; if you share a changed version, it must stay under the same license with its source available.

Bundled third-party files keep their own licenses:

- Bricolage Grotesque and Catamaran fonts: SIL Open Font License 1.1 ([licenses/](licenses/))
- Common-password list (`common-passwords.txt`), from [SecLists](https://github.com/danielmiessler/SecLists): MIT License ([licenses/MIT-seclists.txt](licenses/MIT-seclists.txt))
