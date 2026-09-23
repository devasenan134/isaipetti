# Isaipetti (இசைப்பெட்டி)

A music app for your own music, shared with your friends. Isaipetti streams from a [Navidrome](https://www.navidrome.org/) server you run at home, and adds a friends layer: see what friends are playing, chat, share songs (or just a part of one), and listen together in sync.

- **Player:** home rows, movies (albums), composers, search, background playback, queue, synced lyrics, swipe between songs, scrobbling
- **Friends:** invite codes, friend requests, live presence and now-playing, direct messages and group chats, push notifications
- **Sharing:** send a song, or pick a start and end point and send just that part
- **Listen together:** everyone in a chat's session hears the same music, and anyone can play, pause, skip or change the queue
- **Staying current:** the app finds new releases by itself, and bugs can be reported from Settings

## The three parts

| | Part | What it is | Guide |
|---|---|---|---|
| 1 | **Navidrome** | The music server: your library, streaming, lyrics, accounts | [navidrome/](navidrome/README.md) |
| 2 | **isaipetti-social** | The friends server: invites, friends, chat, listen together, notifications | [server/](server/README.md) |
| 3 | **Android app** | What you and your friends install | [android/](android/README.md) |

```
Isaipetti app ── music, lyrics, playlists ──▶ Navidrome            (Part 1)
      │
      └──────── friends, chat, listen together ──▶ isaipetti-social  (Part 2)
                                                     └─ checks logins with Navidrome
```

Run Part 1 (and optionally Part 2) on a machine at home with Docker, give each an HTTPS address, and send those addresses to friends. They install the app (Part 3) and type them in at login. Nothing about your servers is built into the app or stored in this repository.

## Just want to install the app?

Download the latest APK from [Releases](../../releases), then enter the music server and friends server addresses you were given. Details in [android/README.md](android/README.md#install-for-friends).

## Project

- [CHANGELOG.md](CHANGELOG.md): patch notes for every release
- [ROADMAP.md](ROADMAP.md): what's planned, open bugs and todos

## License

Isaipetti is free software under the [GNU General Public License v3.0](LICENSE). You can use, study, change and share it; if you share a changed version, it must stay under the same license with its source available.

Bundled third-party files keep their own licenses:

- Bricolage Grotesque and Catamaran fonts: SIL Open Font License 1.1 ([licenses/](licenses/))
- Common-password list (`common-passwords.txt`), from [SecLists](https://github.com/danielmiessler/SecLists): MIT License ([licenses/MIT-seclists.txt](licenses/MIT-seclists.txt))
