# Isaipetti Roadmap

The one place for what has shipped, what is being built, what is planned, and what needs fixing.

- **App:** Android (Kotlin + Jetpack Compose), plays music from a Navidrome server over the Subsonic API
- **Server:** companion "isaipetti-social" server in `server/` (Kotlin + Ktor + SQLite) for friends, chat and sign-up
- **Current version:** 0.5.0 (app `versionCode` 11)
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
| Playlists box in Search and recently played playlists | App | P2 | 0.5.1 | Built | Recently played playlists kept on the phone |
| Playlist author, details and readable total time | App | P2 | 0.5.1 | Built | |
| Song details below the player; patch notes in Settings | App | P2 | 0.5.1 | Built | Details from Navidrome's getSong; notes from GitHub Releases |

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

---

## Bugs

### Open

| # | Bug | Area | Severity | Found in | Steps to reproduce / notes |
|---|---|---|---|---|---|
| _none reported_ | | | | | |

### Fixed

| # | Bug | Area | Fixed in | Notes |
|---|---|---|---|---|
| 1 | Going back with the Android back gesture showed both screens faded over each other (e.g. a movie page over the composer's movie grid) | App | 0.3.3 | Screens now slide in and out with a solid background, following the finger during the gesture. Switching tabs fades |
| 2 | A slow back swipe shrank the page toward the middle over the previous screen, while a fast swipe looked fine | App | 0.4.0 | Navigation Compose's separate back-gesture animation defaulted to shrinking the page. It now uses the same animation as the back button. Screen changes also switched to the Material shared-axis transition (short sideways move with a quick fade, 0.3 seconds) |

---

## Todo

Small tasks, cleanup and chores.

- [x] Add a `README.md` with how to build the app and run the server
- [x] Deploy the server update (listen together, clips, deleting chats, push settings)
- [x] Create a release signing key (kept outside the repository)
- [ ] Back up the release key and its password somewhere safe, e.g. Vaultwarden
- [ ] Create a fine-grained GitHub token (this repo only, Issues: read and write) and put it in the server's `.env` as `GITHUB_TOKEN`
- [x] Remove `google-services.json`, the server addresses and the personal email from the git history
- [x] New Firebase project with app ID `io.github.devasenan134.isaipetti`; its files live only on the friends server
- [x] Friends type the servers at login; no addresses or Firebase settings in the app or repo
- [x] Restrict the Firebase API key in Google Cloud to the Android app (package name + signing certificate)
- [x] Create the public GitHub repository and publish 0.4.0 as the first GitHub release
