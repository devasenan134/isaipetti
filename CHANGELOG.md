# Isaipetti Patch Notes

Every release of the Isaipetti app and its companion server, newest first.

Each release has an APK named `isaipetti-<version>.apk` and a git commit titled `<Summary> (<version>)`.
Notes are grouped into **New**, **Improved**, **Fixed** and **Server**. Plans and open bugs are in [ROADMAP.md](ROADMAP.md).

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
