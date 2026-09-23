# Isaipetti Patch Notes

Every release of the Isaipetti app and its companion server, newest first.

Each release has an APK named `isaipetti-<version>.apk` and a git commit titled `<Summary> (<version>)`.
Notes are grouped into **New**, **Improved**, **Fixed** and **Server**. Plans and open bugs are in [ROADMAP.md](ROADMAP.md).

---

## 0.5.2 (unreleased)

### New
- **Artists in Search.** The browse boxes are now a 2×2 grid: Movies, Composers, **Artists** and Playlists. Artists lists every singer A to Z (loading more as you scroll). A singer's page shows the songs they sing on, with Play and Shuffle. Search results show composers and artists separately.
- **Edit your playlists.** On a playlist you made, ⋮ lets you **Rename** it, make it **Public** or **Private**, or **Delete** it. Deleting asks twice, because it can't be undone. Each song's ⋮ menu has **Remove from this playlist**.
- **Save to playlists, like Spotify.** The player has a "playlist +" button next to the ♡ (also **Add to playlist** in any song's ⋮ menu). It opens **Save to**: Liked songs and each of your playlists, ticked where the song already is. Tick or untick and tap **Done** to add or remove it everywhere at once. **New playlist** is at the top.
- **Create a playlist from Your Library** with the **+** at the top. The new playlist opens right away.
- **Live suggestions while typing.** Matching past searches and names from the results appear under the search bar as you type; tap one to search for it. Results also update faster.

---

## 0.5.1 (2026-09-23): Playlists in Search, song details and what's new

### New
- **About this song.** In the full player, scroll down (or tap "About this song ⌄") to see the singers, music director, composer and lyricist (when tagged), movie, year, genre, track, length, audio quality (format, bitrate, sample rate), file size, how often you've played it and when it was added.
- **What's new, in Settings.** The App version card shows the patch notes of the version you're running, or of the new version when there's an update, with **Show more** / **Show less**. The update pop-up shows its notes properly formatted too.
- **Playlists in Search.** Next to Movies and Composers there's a **Playlists** box that opens every playlist on the music server. Below, **Recently played playlists** shows the playlists you last played from (remembered on your phone when you press Play or Shuffle, or tap a song, on a playlist).

### Improved
- **Search remembers what you picked.** Below the Movies, Composers and Playlists boxes, Search now shows **Recently searched songs**, **movies** and **composers**: the ones you played or opened from search results, instead of everything you played. Recently played playlists stay. It's kept on your phone and cleared when you log out.
- **Playlist pages** show who made the playlist, its description, whether it's public or private, and when it was last updated.
- Total playing time is easier to read on playlist, movie and Liked songs pages ("2 hr 15 min", or days for very long playlists), and song counts use thousands separators ("3,264 songs").
- **Your Library** shows who made each playlist. It lists your liked playlists first, then the other playlists you created.

---

## 0.5.0 (2026-09-23): Your Library, likes and a new Search

### New
- **New layout: Home, Search, Your Library, Friends.** Movies and Composers moved into Search, like browsing in Spotify: Search shows a **Movies** box and a **Composers** box, then your **recently played songs, movies and composers** in separate rows. Tapping the search bar shows your recent searches; typing shows results. Back closes the search bar first.
- **Liked songs.** Tap ♡ in the player, or **Like** in a song's ⋮ menu. Liked songs show a small heart in lists, and **Your Library → Liked songs** plays them all (Play or Shuffle).
- **Like whole movies and playlists** with the ♡ next to Play and Shuffle.
- **Your Library** lists Liked songs, your liked movies, and your liked and own playlists, with filters for All, Movies and Playlists.
- Liked songs and movies are saved in Navidrome (its "favourites"), so they're the same on every device and in Navidrome's web page. Navidrome can't like playlists, so liked playlists are saved with your account on the friends server and follow you to every phone (without a friends server they stay on the phone).
- **Know when friends listen together.** When a friend starts listening together in one of your chats, you get a notification ("Alice started listening together. Tap to join") that opens the chat. It only comes when you don't have the app open, at most once per chat every 30 minutes, and it has its own notification category ("Friends listening together") so you can turn it off separately in Android's settings.
- **Search remembers** your searches: tap one to run it again, ✕ to remove it, or Clear. A search is remembered when you press search on the keyboard or open a result. History and recently played songs are kept on your phone and cleared when you log out.

### Improved
- The share sheet lists **Groups** and **People** separately. Groups have a group icon and show their members; people show whether they're online. The chat list uses the same group icon.
- The search box has a ✕ to clear it.

### Server
- Liked playlists are stored per person (new table `liked_playlists`, database version 6): `GET /likes/playlists`, `PUT /likes/playlists`, `DELETE /likes/playlists/{id}`. They're removed with the person's account.
- Starting a new listen-together session sends a `listen` push notification to the chat's other members who don't have the app open (at most once per chat every 30 minutes).

---

## 0.4.2 (2026-09-23): Leaving and deleting groups, sharing recent songs

### New
- **Leave a group.** In a group chat, tap ⋮ → **Leave group**. The others see "… left the group" in the chat. If the group's owner leaves, the longest-standing member becomes the owner; when the last person leaves, the group is deleted.
- **Delete a group for everyone.** The group's owner (whoever created it) can tap ⋮ → **Delete for everyone**. The group and all its messages disappear for every member, and anyone who has it open is taken back to their chats.
- **Share music from a chat.** The music button next to the message box opens a list of what's playing and your recently played songs. Pick one and send it whole, or turn on "Share only a part" to choose a start and end first. Recently played songs are remembered on your phone and cleared when you log out.

### Improved
- The share sheet only lists chats you can still message.

### Server
- New: `POST /conversations/{id}/leave` and `DELETE /conversations/{id}/everyone` (owner only), with a `conversationRemoved` live event.
- Chats carry their owner (`createdBy`); messages can be system lines (`system`), such as "left the group". Leaving or deleting a group also ends its listen-together session.

---

## 0.4.1 (2026-09-23): Feature requests

### New
- **Suggest a feature** from Settings. The bug report card is now **Feedback**, with **Report a bug** and **Suggest a feature**. Both become issues on the app's GitHub page, labelled `bug` or `enhancement`. Feature requests leave out phone details unless you tick the box.

### Server
- `POST /bug-reports` takes an optional `kind` (`bug` or `feature`); older apps keep sending bugs.
- Notification registrations from old app versions (another Firebase project) are forgotten after their first failed delivery, instead of failing on every message.

---

## 0.4.0 (2026-09-23): Listen together, clips, updates and a public release

**One-time step: uninstall the old Isaipetti before installing this version.** It has a new app ID and signing key, so Android treats it as a different app. After installing, log in with your music server and friends server addresses.

### New
- **You enter your servers at login.** The login screen asks for the **Music server** and the **Friends server** (optional when logging in, needed to sign up). No server addresses are built into the app, so it works with anyone's servers. Invites you share now include both addresses, and Settings shows the servers you're using.
- **Listen together** in any chat, group or DM. Tap the headphones at the top of a chat to start a session with what you're playing; others in the chat see "listening together" and can join. Everyone in the session hears the same music, and anyone can play, pause, skip, seek or change the queue for everyone. Leave any time and your music keeps playing on its own. Shuffle is off during a session so everyone hears the same order, and a phone call or unplugged headphones pauses only your phone.
- **Share part of a song.** In the share sheet, turn on "Share only a part" and pick a start and end point with the slider, or tap "Start here" / "End here" while the song plays. Preview it before sending. Friends see a "Clip 1:05–1:35" card that plays just that part; press play again to hear the rest.
- **Updates from inside the app.** The app checks GitHub for a newer version when it starts (every few hours at most) and offers to install it, with the patch notes. Settings shows your version and has "Check for updates". The first time, Android asks you to allow Isaipetti to install apps.
- **Report a bug** from Settings. Describe what happened (optionally with app and phone details) and it becomes an issue on the app's GitHub page. Reports are public but don't show your name.
- **Delete old chats.** A chat with someone who left or is no longer your friend (or a group everyone else left) can be deleted: long-press it in the chat list, or tap "Delete chat" inside it. It's removed for you only.

### Improved
- Screen changes now use the standard Material animation found in most Android apps: the old screen fades out quickly while the new one fades in with a short sideways move. The whole thing takes 0.3 seconds, replacing the slower full-width slide.

### Fixed
- A slow back swipe no longer shrinks the page toward the middle over the previous screen. Slow and fast swipes now play the same animation, following your finger.

### Server
- Listen-together sessions: who's listening in each chat, and the shared queue and playback, relayed live over the WebSocket. Sessions are kept in memory and end when the last listener leaves or goes offline.
- Songs in messages can carry a clip start and end; nonsense ranges are refused. Notifications for clips show the range.
- New: `DELETE /conversations/{id}` for chats you can't message in anymore. It hides the chat and clears its history for you; once nobody who's still around has it, it's removed for good. Opening the DM again later starts it fresh.
- Chats in `GET /conversations` now say whether you can still message in them, and who's listening together.

### Security
- The app's saved logins are no longer included in Android backups.
- New app ID `io.github.devasenan134.isaipetti`, and release builds are signed with a permanent release key. **One-time step: uninstall the old app before installing this version** (Android sees it as a different app). You'll just need to log in again.
- The server refuses oversized requests and messages, and caps listen-together queues at 5,000 songs.
- Bug reports go through the friends server, so the GitHub token never ships inside the app.
- No Firebase settings are built into the app. After login the app fetches them from the friends server (`GET /push/config`), so each friends server uses its own Firebase project.
- The friends server's container runs as a normal user instead of root, and its Firebase files live in a `secrets/` folder.

### Server
- New: `POST /bug-reports`, turned on by setting `GITHUB_REPO` and `GITHUB_TOKEN`. At most 5 reports per person per hour.

### Project
- The project is licensed under GPL-3.0, with a README and the licenses of the bundled fonts and password list.
- Server addresses, Firebase files and signing keys are not in the repository or the app. Only the release signing key is a private build setting.
- The repository has three parts, each with a guide: `navidrome/` (the music server on Docker), `server/` (the friends server on Docker) and `android/` (the app).
- The companion server is now called `isaipetti-social`, and code packages are `io.github.devasenan134.isaipetti` (app) and `io.github.devasenan134.isaipetti.server` (server). Its database file is now `isaipetti-social.db`.

---

## 0.3.3 (2026-09-23): Push notifications and smooth back gesture

### New
- **Push notifications** for new chat messages, friend requests and accepted requests, even when the app is closed or in the background.
- Messages are stacked per chat. There's no notification for the chat you're looking at.
- Tapping a notification opens that chat, or the Friends tab for requests.
- On Android 13 and newer, the app asks for permission to show notifications the first time it opens.

### Fixed
- Going back with the Android back gesture no longer shows two screens faded over each other (for example, a movie page on top of a composer's movie grid). Screens now slide in from the right when opened, and slide away under your finger when you swipe back. Switching tabs still fades.

### Server
- Remembers each phone's notification token and sends notifications through Firebase Cloud Messaging, but only when none of your phones has the app on screen. The app tells the server whether it's on screen, so you still get notified while listening with the app in the background.
- Tokens Firebase rejects (for example, after the app is uninstalled) are forgotten.
- The app registers its token after login and removes it on logout.
- New: `POST /devices` and `POST /devices/remove`. Push is turned off when no Firebase key is configured.

---

## 0.3.2 (2026-09-23): Password rules and rename-safe users

### New
- Password rules for sign-up and password changes: at least 10 characters, not one of about 9,000 most-used passwords, not containing your username, and not overly repetitive.
- A live strength meter as you type a new password.

### Improved
- Existing passwords still log in, even if they don't meet the new rules.

### Server
- The same password rules are checked on the server at sign-up.
- Users are matched by Navidrome's permanent user id instead of their username. An account renamed in Navidrome keeps its friends and chats, and a new account that reuses a deleted username starts fresh. Existing users get their id filled in automatically.
- The server reuses its admin login instead of logging in for every request, to stay under Navidrome's login rate limit.

---

## 0.3.1 (2026-09-23): Settings

### New
- Settings page where you can change your display name.
- Password change. It goes straight from the app to Navidrome and requires your current password. Nothing stores the password, and the friends server never sees it.
- After a password change, the app switches to the new login and signs out your other devices. Devices whose saved login no longer works log out with a note explaining why.

### Improved
- Tapping the tab you're already on returns to that tab's main screen.
- Screens opened from a tab (a movie, a chat, settings) keep that tab highlighted.

### Fixed
- The play queue keeps working after a password change.

### Server
- New: rename (`PATCH /me`) and sign out other devices (`POST /auth/logout-others`).

---

## Server update (2026-09-23): Deleted accounts are cleaned up

Server only, released between 0.3.0 and 0.3.1. No new app version.

- Every 10 minutes the server checks for people whose Navidrome account was deleted and removes them. They lose their friends, sessions, requests and group memberships. Their messages stay, marked "(left)", and their username is freed.
- As a safety check, it skips the run if Navidrome can't be reached or if it would remove more than half the users at once.
- Direct messages to someone who is no longer your friend are refused.

---

## 0.3.0 (2026-09-23): Friends and chat

### New
- **Friends tab** with your chats, friends and friend requests.
- Add friends, and invite new people with invite codes.
- Sign up on the login screen with an invite code, which creates your Navidrome account.
- See which friends are online and what they're listening to, live.
- Direct messages and group chats.
- Share songs into a chat from song menus and from the player.

### Server
- New companion server (`server/`, Kotlin + Ktor + SQLite). It logs you in by checking your details with Navidrome, and handles invites, friend requests, presence, now-playing and chats over a live WebSocket.

---

## 0.2.0 (2026-09-23): Branding

### New
- Brass & night colour palette for light and dark mode, replacing the phone's wallpaper colours.
- New fonts: Bricolage Grotesque for headings and Catamaran for body text.
- New box-with-lid launcher icon.

---

## 0.1.1 (2026-09-23): Player

The first version in git.

### New
- Log in to your Navidrome server.
- Home screen with rows of music, a movies grid, composers, and search.
- Background playback with a play queue.
- Synced lyrics view.
- Scrobbling, so your plays are counted on the server.
- Swipe left or right to change songs in the full player and the mini player.

---

## 0.1.0 (2026-09-23)

The first build. Released before the project was in git, so its changes weren't recorded separately. 0.1.1 has the full feature list.
