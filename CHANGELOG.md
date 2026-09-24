# Isaipetti Patch Notes

Every release of the Isaipetti app and its companion server, newest first.

Each release has an APK named `isaipetti-<version>.apk` and a git commit titled `<Summary> (<version>)`.
Notes are grouped into **New**, **Improved**, **Fixed** and **Server**. Plans and open bugs are in [ROADMAP.md](ROADMAP.md).

---

## 0.6.11 (2026-09-24): Ask for a song now or next, and edit the queue

### New
- **Edit the queue.** In the queue panel, drag a song by its handle to move it, or swipe it left to remove it. The song that's playing can't be swiped away. Moving songs needs shuffle off. In a jam you host, your changes reach everyone; in someone else's jam the queue is view-only.
- **Ask for a song now or next.** In someone else's jam, swipe a song **right** to ask the host to play it right away (skipping the current song), or **left** to ask for it next. The request in the chat says which, and the host's button reads **Play now** or **Play next**.

### Fixed
- **No more request spam.** You can ask for a song once every 10 seconds, with at most 3 requests waiting for an answer at a time.

### Server
- `POST /conversations/{id}/listen/requests` takes `mode` (`"next"`, the default, or `"now"`); messages carry `requestMode`. Requests are refused with 429 when they come within 10 s of the last one (`songRequestCooldownMs`) or when 3 are already waiting. Database schema v11 (`messages.request_mode`).

---

## 0.6.10 (2026-09-24): Search by actor and lyricist, and spelling that doesn't have to be right

### New
- **Search by actor.** Type an actor's name to see their movies, marked **Starring …**. Tap them for a page with their movies and every song from those movies. The cast comes from Wikipedia and Wikidata and is still being filled in, so some films don't list everyone yet.
- **Search by lyricist.** Songs show **Lyrics by …** when you searched for who wrote them. A lyricist's page has all their songs and the movies they wrote for.
- **Spelling doesn't have to be right.** Search goes by how the words sound, so "kanavae" finds Kanave, "thanush" finds Dhanush and "putu velai malai" finds Pudhu Vellai Mazhai. Half-typed words work too.

### Improved
- Results are grouped as **People** (with what they do: Composer, Artist, Lyricist, Actor), **Movies** and **Songs**.
- One person, one result: different spellings of a name ("Vaali", "Vaalee") and an actor who also sings are shown as one person.

### Server
- `GET /search` and `GET /search/people/{id}`. Actors are read from `movie-cast.jsonl` next to the database (made by the library tools); without it, search just has no actors. If the friends server has no search, the app uses Navidrome's.

---

## 0.6.9 (2026-09-24): Jams have a host

### New
- **Whoever starts a jam runs it.** In a chat or a group, only the person who started listening together can play, pause, skip, seek or change the queue. Everyone else hears the same music; their controls are greyed out (and presses on the lock screen or in the notification are ignored).
- **Ask for a song.** In someone else's jam, swiping a song (either way), or **Play next** / **Add to queue**, sends a request in the chat. The host gets **Accept** and **Decline**; an accepted song plays after the current one, in the order requests were accepted. Everyone sees whether a request was accepted, declined, or left unanswered when the jam ended.
- **The mini player shows the jam.** Listeners see a jam icon where pause and next were. The host sees a small record spinning next to pause and next.

### Improved
- The player says whose jam you're in ("Alice's jam · they control the music").

### Fixed
- Swiping a song sometimes played it next (or queued it) twice.

### Server
- Sessions have an owner. The session ends when the owner leaves, or stays offline for more than a minute.
- `POST /conversations/{id}/listen/requests` and `POST /conversations/{id}/listen/requests/{messageId}` for song requests, and a `messageUpdated` event when one is answered.

---

## 0.6.7 (2026-09-24): Listening stats for admins

### New
- **Listening stats (admins only).** Settings has a new **Listening stats** card, shown only to people who are admins in Navidrome. It shows how much everyone listens: hours and plays for **today, 7 days, 30 days or all time**, when each person last played something, and a bar to compare them.
- **Tap a person** to see their top songs, movies and composers for that range.
- **Two charts for everyone together:** hours per day over the last 30 days, and which hours of the day people listen. Tap a bar to read its exact value.
- A song counts as played once half of it (or 4 minutes) is heard, and adds its full length, so the hours are a close estimate.

### Server
- `GET /admin/access` and `GET /admin/stats` read Navidrome's play history (read-only). Only Navidrome admins get an answer, and the server's own admin account is left out of the list.

---

## 0.6.6 (2026-09-24): A live pointer when sharing a part

### Improved
- **The pointer follows the song.** When you share a part of the song that's playing, the pointer on the waveform moves along with it. Drag the pointer and the song jumps there.
- **Preview plays just your part.** Preview moves the pointer to the start of your part and plays from there. At the end of the part the song pauses and the pointer stays there.
- **A play/pause button** at the end of the row (after Preview, Start here and End here) carries on playing from the pointer, past the end of your part too. It shows pause while the song plays.

---

## 0.6.5 (2026-09-24): Lyrics under the cover, a song waveform for clips

### New
- **The lyric being sung, under the cover.** When a song has synced lyrics, the current line shows between the cover and the title and slides up as the song moves on. Tap it to see all the lyrics.
- **See the song when you share a part of it.** "Share only a part" shows the song's waveform, like Instagram's music picker: the part you chose is in colour, the rest is faded. The first time for a song takes a few seconds; after that it shows at once.
- **A pointer on the waveform.** Tap or drag the small pointer above the waveform to anywhere in the song. **Start here** and **End here** use it, and it follows the preview while it plays.

### Improved
- **Every singer's name.** When the singers don't fit under the song title in the player, the line scrolls sideways so every name shows.
- **Previews leave your music alone.** Preview plays on its own, so it no longer changes your queue. Your music pauses for it and carries on afterwards. While listening together, Preview is off until the music is paused, and it never changes what the others hear.
- **"Artists" instead of "Singers"** in song details, the search box and Home.

### Server
- Home rows are now **This is: artists** and **Composer and artist stations**.

---

## 0.6.4 (2026-09-24): Graphite & mango, appearance settings and new tile art

### New
- **A brighter colour theme: Graphite & mango.** A soft graphite grey, lighter than before, with a ripe mango accent, in dark and light. The grey is neutral, so album covers bring the colour.
- **Appearance in Settings.** Choose **Auto** (follows your phone), **Light** or **Dark**. On Android 12 and later, **Use wallpaper colours** gives the app the colours of your wallpaper instead.
- **New art for mixes.** Made for you tiles (Daily Mixes, Discover Weekly, On Repeat...) are colour gradients. **This Is** tiles and **stations** show the composer, singer or song on a soft pastel.

### Improved
- **Home:** the list of songs is now **Recently Played** and lists every song that played, whether you tapped it or it came next in a movie, playlist or mix. The tiles below it are now **Jump back in**.
- **Your Library:** list rows are bigger (larger pictures and text).

---

## 0.6.3 (2026-09-24): Indigo night, and a new Home

### New
- **A new colour theme: Indigo night.** Deep indigo with a periwinkle accent, in dark and light mode. Titles are near white and subtitles a bright, cool grey, so text no longer looks dull.
- **Home, in a new order.** First **Made for <your name>** as tiles. Then **Recent songs**: songs you started by tapping them, from anywhere (a movie, a playlist, search, a chat...), as a list. Then **Recently played**: movies, playlists, Liked songs, mixes, composers and singers you started as a whole with **Play**, **Shuffle** or **Resume**, as tiles. Tapping a single song no longer adds its movie or playlist there.

### Improved
- **Your Library** starts at the top of the page (it sat in the middle), its sections are **All, Playlists, Movies, My Playlists**, and its pictures are a little bigger.

---

## 0.6.2 (2026-09-24): Made for you vs. showcases, cropping photos, and a tidier Your Library

### New
- **Crop your photos.** After you take or choose a photo for your profile, a group or a playlist cover, it opens so you can frame it: pinch to zoom, drag to move, **Rotate** to turn it. Profile and group pictures show a round guide, since that's how they appear.

### Improved
- **"Made for you" means it.** Only mixes built from your own listening say **Made for <your name>**: Daily Mixes, Discover Weekly, On Repeat, Friends Mix and Rewind, plus a new **Your stations** row (radio from the songs you play most).
- **Showcases for the library's composers and singers.** **This Is A.R. Rahman**, **This Is S. P. Balasubrahmanyam** and so on: their most played songs, the same for everyone. Mood, decade and **Composer and singer stations** rows are showcases too, and **Popular and new** has Top 50 and New Arrivals (newest first).
- **Bigger tiles on Home** (and in Search's rows).
- **Your Library is more compact:** smaller pictures in the list, and three tiles across in the grid.
- **"About this song" shows one Composer line** instead of both "Music director" and "Composer", which were the same person for film songs.
- **Your Library sections are All, Playlists, My Playlists, Movies.** Playlists has the mixes and playlists you saved; My Playlists has Liked songs and the playlists you made.

---

## Server update (2026-09-24): better mood mixes, composer and singer mixes

No app update needed; the friends server changed.

### Improved
- **Chill, Sleep and Focus** no longer share songs: each song goes to the one it fits best. Chill and Sleep only take songs that measure as quiet with soft beats, none of them party songs; Focus takes only songs that clearly sound instrumental.
- **Composer and singer mixes** (like the A.R. Rahman Mix) now have only that composer's or singer's songs. For music that sounds like theirs, use their **Radio**.

### Fixed
- A few songs whose files are longer than their music (the analyzer measured silence at the "middle") looked like the quietest songs in the library and showed up in Chill, Sleep and Focus. Such measurements are now ignored.

---

## 0.6.1 (2026-09-24): Pictures, invites, page colours and a bigger look

### New
- **Profile pictures.** In Settings, tap your picture to **take a photo** or **choose one from your photos** (or remove it). It's cropped to a square and made small on the phone, and your friends see it in their friends list, chats and the share sheet.
- **Group photos.** In a group chat, ⋮ → **Change group photo**. Anyone in the group can change or remove it, and the chat shows who did.
- **Playlist covers.** On a playlist you made, ⋮ → **Change cover** to use a photo instead of the automatic cover (or go back to it). Covers are stored in Navidrome, so they show everywhere, even in other Navidrome apps.
- **Manage your invites.** Friends → Invite now opens **Your invites**: every unused code with when it expires, and buttons to **copy** it, **share** it again or **delete** it (asks first; the code stops working). **New invite** makes another, up to 5 unused at a time, and "3 of 5 left" shows how many you can still make. Used and expired codes from the last month are listed below, with who joined.
- **Colour on every page, like the player.** Movie, playlist, singer and composer pages take a colour from their cover and fade it into the background behind the header. Mixes use their own colour, and Liked songs the brand colour.
- **Your Library as a grid or a list.** The button next to **+** switches between them; the phone remembers your choice. **Swipe left and right** to move between All, Movies and Playlists.
- **"Made for you" says your name.** On Home it's **Made for <your name>**, and mixes picked for your taste say **Made for <your name> · By Isai Pettai** on their page and in Your Library. Mixes everyone gets alike (Top 50, stations) don't.

### Improved
- **Bigger tiles and pictures, closer to Spotify.** Tiles on Home and Search, grid tiles, Your Library pictures, song covers in lists and the big picture on movie, playlist and mix pages are all larger, and section titles are bigger and bold.

### Fixed
- **Every song in Liked songs, and in playlists you made, had a ✓.** It said nothing new there. In Liked songs the ✓ now shows only for songs also in one of your playlists; in your own playlists, only for songs you've also liked or put in another playlist.

### Server
- Unused invites can be deleted (`DELETE /invites/{code}`).
- Mixes say whether they were picked for the person (`personal`).
- Profile pictures (`PUT/DELETE /me/avatar`, `GET /users/{id}/avatar`) and group photos (`/conversations/{id}/picture`), kept as files next to the database.
- Playlist covers (`PUT/DELETE /playlists/{id}/cover`): the server checks you made the playlist, then stores the picture in Navidrome with its admin account, so the phone never needs your Navidrome password for it.

---

## 0.6.0 (2026-09-23): Mixes by Isai Pettai (Phase 4)

### New
- **Made for you, like Spotify.** Home has new rows of mixes, playlists and stations made for you by **Isai Pettai**, the app's own DJ. Everything it makes says **By Isai Pettai** and has its own artwork: the mix's colour, covers from its movies, its name and the Isai Pettai mark.
  - **Daily Mix 1–6:** one for each side of your taste (say, 80s Ilaiyaraaja, Anirudh-era songs, A.R. Rahman melodies). Songs you love mixed with new ones that sound like them. A fresh selection every day.
  - **Discover Weekly:** 30 songs you've never played, picked for how you listen. New every Monday.
  - **On Repeat** (what you've played most this month), **Rewind** (old favourites you haven't played lately), **New Arrivals** (songs just added to the library, the ones that suit you first), **Friends Mix** (what your friends are playing) and **Top 50** (the most played on the server).
  - **Moods and vibes:** Chill, Romance, Feel Good, Party, Kuthu, Sad Songs, Melody, Workout, Devotional, Carnatic Touch, Focus, Sleep and Retro. They come from listening to every song, not from tags, and lean towards what you like. The moods that suit you most come first.
  - **Your composers, Singers you love and Through the decades:** a mix for each, with their best songs and music like theirs.
- **Stations that never end.** Start a radio from any song (⋮ → **Start song radio**, or the radio button in the player), a movie, a composer or a singer (**Radio** on their pages). It plays songs that sound like it and adds more as it goes, even with the screen off.
- **Mixes keep themselves up to date.** When songs are added to the library, the ones that fit a mix join it on their own; mixes also follow what you play, like and skip. Each mix's page says when it last changed.
- **Save mixes to Your Library** with ♡. They keep updating there. Or **⋮ → Save a copy as a playlist** to keep today's songs as a normal playlist ("By Isai Pettai" in its description).
- **Recommended songs** under your own playlists: songs that would fit, each with a **+** to add it. **Refresh** shows others.
- Mixes and stations you play show up in **Recently played**.

### Improved
- **Skips teach the mixes.** The app tells the friends server when you skip a song in its first 30 seconds. Songs you keep skipping stay out of your mixes. (Nothing is reported while listening together or for shared clips.)

### Server
- The friends server makes the mixes. It reads Navidrome's database (read-only) for the library and everyone's plays and likes. Turn it on with `NAVIDROME_DATA` in `.env`; see [server/README.md](server/README.md#mixes-by-isai-pettai-optional).
- New, optional **audio analyzer** ([analyzer/](analyzer/README.md)): listens to every song once with the CLAP music model to find moods and sound-alikes, then only new songs. Without it, mixes use composers, singers, years and listening.
- Lyricists credited as artists in some files no longer count as singers for mixes.
- `./gradlew previewMixes` prints the mixes someone would get, from copies of the databases.

---

## 0.5.4 (2026-09-23): Recently played, resume everywhere and fixes

### New
- **Recently played on Home shows everything.** Songs, movies, playlists, composers (from Shuffle all), artists and Liked songs, newest first. Composers and artists have **round** tiles; songs, movies, playlists and Liked songs are **square**. Tapping a song plays it; anything else opens its page.
- **Resume on movies and Liked songs**, not only playlists: continue from the song you were on, in the same order as before.

### Fixed
- **Changing a read-only playlist failed with "Couldn't change".** Navidrome doesn't allow changing the songs of smart playlists or playlists kept in sync with a playlist file on the server, even for their owner. The app now knows which ones those are: in **Save to** they're greyed out with an explanation, and on their page "Remove from this playlist" is hidden and a note explains why. Renaming, public/private and deleting still work.
- **The player's colour now really matches the cover.** It was using the cover's muted shades, blended only lightly into the background, so a red cover barely looked red. It now takes the cover's main colour (a red cover gives a deep red in dark mode, a soft pink in light mode) and shows it clearly at the top, easing into the background around the controls.

---

## 0.5.3 (2026-09-23): Swipe gestures, cover colours and resume

### New
- **Swipe down to minimize the player.** Drag the full player down and it follows your finger. Let go far enough (or flick) and it tucks back into the mini player; otherwise it springs back. If you've scrolled down to "About this song", swiping scrolls back up first.
- **A background that matches the song.** The full player has a gentle gradient in a muted colour from the cover art. It's picked to suit dark or light mode and fades smoothly when the song changes.
- **Swipe songs to queue them.** In any song list, **swipe right to play next** or **swipe left to add to the end of the queue**. The row springs back and a short message confirms it.
- **Saved marks, like Spotify.** Songs you've liked or added to one of your playlists show a **✓** in lists. Tap it to see where it's saved (Liked songs and which playlists) and change it there.
- **Resume a playlist where you left off.** When you play from a playlist, the app remembers the queue and which song you were on. Next time you open that playlist, **Resume · song name** continues from that song, in the same order as before, so a shuffled order stays the same. Songs added to the playlist since then join the end, and removed ones are skipped. The spot within the song isn't kept; the song starts from the beginning. This is kept on your phone for your 30 most recent playlists and cleared when you log out.

### Improved
- **Recents in Search include what you play.** Playing from a movie, composer or singer page (Play, Shuffle, Shuffle all or a song) now adds it to **Your recent movies** and **Your recent composers and artists**, however you got there, not only from search results.
- **Home** no longer lists every playlist. Playlists are under Search → Playlists and in Your Library.

---

## 0.5.2 (2026-09-23): Artists, playlist editing and live search

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
