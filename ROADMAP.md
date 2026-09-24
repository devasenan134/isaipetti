# Isaipetti Roadmap

The one place for what has shipped, what is being built, what is planned, and what needs fixing.

- **App:** Android (Kotlin + Jetpack Compose), plays music from a Navidrome server over the Subsonic API
- **Server:** companion "isaipetti-social" server in `server/` (Kotlin + Ktor + SQLite) for friends, chat, sign-up and mixes
- **Analyzer:** optional audio analyzer in `analyzer/` (Python + CLAP) for moods and sound-alikes
- **Current version:** 0.6.10 (app `versionCode` 26)
- **Last updated:** 2026-09-23

---

## How to use this document

- New feature ideas go under **Ideas**. When one is agreed, move it to **Planned** and give it a priority and a target version.
- Put a feature under **In progress** once work on it starts. When it ships, move it to **Released** under its version.
- Every release gets patch notes in [CHANGELOG.md](CHANGELOG.md). The **Released** section below is a short summary.
- Bugs go under **Bugs**. When a bug is fixed, tick it and add the version it was fixed in.
- Small tasks that aren't features or bugs (cleanup, tooling, docs) go under **Todo**.

**Priority:** `P0` must have / blocker · `P1` important · `P2` nice to have · `P3` someday

**Status:** `Idea` · `Planned` · `In progress` · `Done` · `Dropped`

**Bug severity:** `Critical` crash, data loss or security · `Major` a feature is broken · `Minor` works but wrong · `Cosmetic` looks wrong

---

## Released

Short summary. The full patch notes are in [CHANGELOG.md](CHANGELOG.md).

### 0.6.10: Search by actor and lyricist, forgiving spelling
- Search finds actors (their movies) and lyricists (their songs); spelling goes by sound
- People, Movies and Songs in results; one person per name

### 0.6.9: Jams have a host
- Only the person who starts a jam controls it; others send song requests in the chat

### 0.6.7: Listening stats for admins
- Hours and plays per person, top picks, and charts, for Navidrome admins

### 0.6.6: A live pointer when sharing a part
- The pointer follows the playing song; Preview plays from the in point and stops at the out point; a play/pause button

### 0.6.5: Lyrics under the cover, a song waveform for clips
- The line being sung under the cover; singers scroll when they don't fit
- Song waveform and a movable pointer when sharing a part; Preview no longer touches the queue or listen-together
- "Artists" instead of "Singers"

### 0.6.4: Graphite & mango, appearance settings and new tile art
- Graphite & mango theme; Auto/Light/Dark and wallpaper colours in Settings
- Gradient art for Made for you, pastel art for This Is and stations
- Home: Recently Played lists every song played; Jump back in; bigger Your Library rows

### 0.6.3: Indigo night, and a new Home
- Indigo night colour theme with brighter text
- Home: Made for you; Recent songs (songs you tapped); Recently played (collections started with Play, Shuffle or Resume)
- Your Library starts at the top; sections All, Playlists, Movies, My Playlists; slightly bigger

### 0.6.2: Made for you vs. showcases, cropping photos, and a tidier Your Library
- Crop, zoom and rotate photos before using them (profile, group, playlist cover)
- Made for <name> only on mixes from your listening, plus Your stations; This Is <composer/singer> showcases
- Bigger Home tiles; compact Your Library; sections All, Playlists, My Playlists, Movies

### Server update: better mood mixes, composer and singer mixes
- Chill, Sleep and Focus share no songs and check measured loudness and rhythm; composer and singer mixes have only their songs

### 0.6.1: Pictures, invites, page colours and a bigger look
- Profile pictures, group photos and playlist covers (take a photo or choose one)
- Your invites: copy, share again or delete unused codes; see who joined
- Cover-coloured gradients on movie, playlist, mix, singer, composer and Liked songs pages
- Your Library as a grid or a list, swipe between All, Movies and Playlists; "Made for <your name>" on personal mixes
- Bigger tiles, pictures and section titles
- Fixed: every song in Liked songs showed a ✓

### 0.6.0: Mixes by Isai Pettai (Phase 4)
- Made for you: Daily Mix 1–6, Discover Weekly, On Repeat, Rewind, New Arrivals, Friends Mix, Top 50
- Mood, composer, singer and decade mixes; endless stations from any song, movie, composer or singer
- Mixes update themselves when music is added and as you listen; skips keep songs out
- Save mixes to Your Library, or a copy as a playlist; Recommended songs under your playlists
- Server reads Navidrome's database for listening; optional audio analyzer (CLAP) for moods and sound-alikes

### 0.5.4: Recently played, resume everywhere and fixes
- Recently played on Home shows songs, movies, playlists, composers, artists and Liked songs (round tiles for composers and artists)
- Resume on movies and Liked songs as well as playlists
- Fixed: smart and file-synced playlists are read-only and now explained instead of failing
- Fixed: the player's colour clearly matches the cover

### 0.5.3: Swipe gestures, cover colours and resume
- Swipe down to minimize the player; gentle gradient matched to the cover
- Swipe songs right to play next, left to add to the queue
- Check mark on songs you've liked or saved to a playlist, showing where
- Resume a playlist from where you left off, keeping the shuffled order
- Recents include plays from movie, composer and singer pages; Home no longer lists playlists

### 0.5.2: Artists, playlist editing and live search
- Artists (singers) box in Search and singer pages; composers and artists shown separately in results
- Edit playlists you made: rename, public/private, delete (asks twice), remove songs
- Save to playlists from the player, like Spotify; New playlist in Your Library
- Live suggestions while typing

### 0.5.1: Playlists in Search, song details and what's new
- Playlists box in Search; recently searched songs, movies and composers; recently played playlists
- Playlist pages show the author and details; Your Library shows your own and liked playlists
- Scroll down in the player for details about the song
- Settings shows the patch notes, with Show more

### 0.5.0: Your Library, likes and a new Search
- Tabs are Home, Search, Your Library, Friends; Movies and Composers open from Search
- Search shows browse boxes and recently played songs, movies and composers; recent searches when you tap the bar
- Like songs, movies and playlists; Your Library with Liked songs, movies and playlists (liked playlists saved on the friends server)
- Share sheet separates groups and people
- Notification when friends start listening together

### 0.4.2: Leaving and deleting groups, sharing recent songs
- Leave a group chat; the group's owner can delete it for everyone
- The chat's music button shows what's playing and recently played songs, with the option to share just a part

### 0.4.1: Feature requests
- Settings has a Feedback card: Report a bug and Suggest a feature, both posted as GitHub issues
- The server forgets notification registrations from old app versions

### 0.4.0: Listen together, clips, updates and a public release
- Listen together in any chat, with everyone in control
- Share part of a song (a clip with a start and end point)
- Delete chats with people who left or aren't friends anymore
- The app checks GitHub for new versions and installs them; bug reports from Settings become GitHub issues
- Friends type the music and friends server addresses at login; nothing private is built into the app
- New app ID and a permanent release signing key; the project is public under GPL-3.0 with guides for Navidrome, the friends server and the app

### 0.3.3: Push notifications and smooth back gesture
- Push notifications for chat messages, friend requests and accepted requests (Firebase). Tapping one opens the right screen
- Fixed: the back gesture showed two screens faded over each other. Screens now slide

### 0.3.2: Password rules and rename-safe users
- Passwords must be at least 10 characters. They can't be one of about 9,000 common passwords, can't contain the username, and can't be overly repetitive
- These rules apply at sign-up and on password change. A live strength meter shows as you type
- The server matches users by Navidrome's permanent user id, so renaming an account in Navidrome keeps its friends and chats
- The server reuses the admin token, to stay under Navidrome's login rate limit

### 0.3.1: Settings
- Settings page with name editing
- Secure password change that goes straight to Navidrome, so no password is stored anywhere
- Changing the password signs out other devices
- The queue keeps working after a password change
- Tapping the current tab returns to its main screen

### Server: account cleanup
- People whose Navidrome account was deleted are removed automatically (checked every 10 minutes)

### 0.3.0: Friends and chat (Phase 2)
- Companion server with login checked against Navidrome
- Sign-up with invite codes, which creates the Navidrome user
- Friend requests, live presence and now-playing over WebSocket
- Direct messages and group chats with song sharing
- Friends tab in the app, with chats, friends, requests and invite/add-friend dialogs

### 0.2.0: Branding
- Brand colours, fonts and launcher icon

### 0.1.1: Player
- Login, home rows, movies grid, composers, search
- Background playback with Media3, queue, scrobbling
- Synced lyrics view
- Swipe between songs in the full player and the mini player

---

## In progress

| Feature | Area | Priority | Target | Status | Notes |
|---|---|---|---|---|---|
| _nothing right now_ | | | | | |

---

## Planned

Features agreed for an upcoming version.

| Feature | Area | Priority | Target | Status | Notes |
|---|---|---|---|---|---|
| _none yet_ | | | | | |

---

## Ideas

Features not yet agreed. Add freely.

| Idea | Area | Notes |
|---|---|---|
| Option to have a liked songs section | | |
| Option to like an entire albums or playlist | | want to have the liked songs section and albums, playlist listed on a new separate page like Your library |
| Lets move the movies and Composers page on the app to search section like in spotify | | |
| After moving those pages, lets have the Your library down there, so it will be Home, Search, your library, Friends | | |
| "Not interested" / hide a song from mixes, and a "Don't play this" action in the player | Mixes | Skips already do this slowly |
| Mood mixes tuned for Tamil film music: a small labelled set to check and improve the descriptions | Analyzer | CLAP was trained mostly on Western music |
| Search mixes and moods ("sad 90s Ilaiyaraaja") | Mixes | Descriptions can be matched against any typed text |

---

## Bugs

### Open

| # | Bug | Area | Severity | Found in | Steps to reproduce / notes |
|---|---|---|---|---|---|
| _none reported_ | | | | | |

### Fixed

| # | Bug | Area | Fixed in | Notes |
|---|---|---|---|---|
| 5 | Every song in Liked songs and in your own playlists showed a ✓ | App | 0.6.1 | There the ✓ now means "also saved somewhere else" |
| 4 | Changing a smart or file-synced playlist failed with "Couldn't change" | App | 0.5.4 | Uses Navidrome's `readonly` flag: such playlists are greyed out in Save to and explained on their page |
| 3 | The player's cover gradient was barely visible (a red cover didn't look red) | App | 0.5.4 | Uses the cover's main colour with set brightness, and a stronger gradient |
| 1 | Going back with the Android back gesture showed both screens faded over each other (e.g. a movie page over the composer's movie grid) | App | 0.3.3 | Screens now slide in and out with a solid background, following the finger during the gesture. Switching tabs fades |
| 2 | A slow back swipe shrank the page toward the middle over the previous screen, while a fast swipe looked fine | App | 0.4.0 | Navigation Compose's separate back-gesture animation defaulted to shrinking the page. It now uses the same animation as the back button. Screen changes also switched to the Material shared-axis transition (short sideways move with a quick fade, 0.3 seconds) |

---

## Todo

Small tasks, cleanup and chores.

- [x] Add a `README.md` with how to build the app and run the server
- [x] Deploy the server update (listen together, clips, deleting chats, push settings)
- [x] Create a release signing key (kept outside the repository)
- [ ] Back up the release key and its password somewhere safe, e.g. Vaultwarden
- [x] Watch the analyzer's first full run and check the mood mixes with real listening (`./gradlew previewMixes`)
- [ ] Create a fine-grained GitHub token (this repo only, Issues: read and write) and put it in the server's `.env` as `GITHUB_TOKEN`
- [x] Remove `google-services.json`, the server addresses and the personal email from the git history
- [x] New Firebase project with app ID `io.github.devasenan134.isaipetti`; its files live only on the friends server
- [x] Friends type the servers at login; no addresses or Firebase settings in the app or repo
- [x] Restrict the Firebase API key in Google Cloud to the Android app (package name + signing certificate)
- [x] Create the public GitHub repository and publish 0.4.0 as the first GitHub release
