# Part 2: isaipetti-social, the friends server

The friends server adds the social side of Isaipetti: invite codes and sign-up, friends and who's online, what everyone is listening to, chats, song and clip sharing, listening together, push notifications and bug reports. It keeps its own small SQLite database and checks every login against Navidrome, so there are no extra passwords.

It's optional: without it, the app is a plain Navidrome player.

## Setting it up

Follow [SELF_HOSTING.md](../SELF_HOSTING.md): [step 3](../SELF_HOSTING.md#3-the-friends-server) runs it, and steps 6 to 8 turn on [push notifications](../SELF_HOSTING.md#6-push-notifications-optional), [mixes](../SELF_HOSTING.md#7-mixes-by-isai-pettai-optional) and [bug reports](../SELF_HOSTING.md#8-bug-reports-from-the-app-optional). The rest of this page explains how those parts work.

## Mixes by Isai Pettai (optional)

The server makes mixes, playlists and stations for everyone, with **Isai Pettai** as their author: up to six **Daily Mixes** (one per side of your taste), **Discover Weekly** (songs you haven't played), **On Repeat**, **Rewind**, **New Arrivals**, **Friends Mix**, **Top 50**, composer, singer and decade mixes, and **stations** from any song, movie, composer or singer that never run out. Your own playlists get **Recommended songs**. People can save mixes to Your Library, where they keep updating, or save a copy as a normal playlist.

It works them out from Navidrome's own database, which it only reads (Navidrome's API can't tell an admin what others played; its database can): the songs, and everyone's plays, likes and ratings. The app also reports skips, so songs you keep skipping stay out of your mixes. Nothing is stored as a finished list: when the library, your listening or the day changes, the mixes are worked out again, so **new songs that fit a mix appear in it by themselves**.

To turn it on, see [SELF_HOSTING.md, step 7](../SELF_HOSTING.md#7-mixes-by-isai-pettai-optional). With only Navidrome's database, mixes group songs by composer, singers and era. For **mood mixes** and radio that follows how songs **sound**, also run the [audio analyzer](../analyzer/README.md).

## Bug reports and feature requests (optional)

The app's *Settings → Feedback* (**Report a bug** and **Suggest a feature**) creates GitHub issues through this server, labelled `bug` or `enhancement`, so the token never ships inside the app. Issues don't say who sent them. Setup: [SELF_HOSTING.md, step 8](../SELF_HOSTING.md#8-bug-reports-from-the-app-optional).

## Updating and backups

See [Updating](../SELF_HOSTING.md#updating) and [Backups](../SELF_HOSTING.md#backups) in the guide.

## Safety notes

- `.env`, `data/` and `secrets/` never leave the server and are ignored by git.
- The container runs as your user, not root, and is only reachable from the machine itself.
- Logins and sign-ups are rate-limited per address, session tokens are stored only as hashes, and oversized requests are refused.

## Development

```bash
./gradlew test                                        # the whole flow against a fake Navidrome
./gradlew runDev -PnavidromeUrl=https://music.example.com  # a local copy on port 8095
# The mixes someone would get, from copies of navidrome.db and features.db:
./gradlew previewMixes -PnavidromeDb=navidrome.db -PfeaturesDb=features.db -Puser=<username>
```

Kotlin, Ktor and SQLite. Code is in `src/main/kotlin/io/github/devasenan134/isaipetti/server/`.
