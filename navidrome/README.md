# Part 1: Navidrome, the music server

[Navidrome](https://www.navidrome.org/) holds your music library and streams it. Isaipetti plays from it, shows its lyrics and playlists, and logs everyone in with their Navidrome account.

## What you need

- A computer that stays on (a home server, mini PC or NAS) with [Docker](https://docs.docker.com/engine/install/) and Docker Compose
- Your music in folders on that machine
- A way to reach it over HTTPS from outside your home (see [Make it reachable](#make-it-reachable)), so phones can stream anywhere

## Run it

```bash
cd navidrome
cp .env.example .env
nano .env                  # set MUSIC_FOLDER, and PUID/PGID from `id -u` / `id -g`
mkdir -p data
docker compose up -d
```

Open `http://<this machine>:4533` in a browser. The first visit asks you to **create the admin account**; use a strong password. Navidrome then scans your music (the first scan can take a while for a big library).

## Accounts

- **You and anyone you add by hand:** create users in Navidrome under *Settings → Users*.
- **Friends who sign up with an invite code** (Part 2) get their Navidrome account created automatically by the friends server.
- **The friends server needs its own admin account** to create those accounts and notice deleted ones. Create a user such as `isaipetti-bot`, tick **Is admin**, give it a long random password, and put both in the friends server's `.env` (Part 2). Don't use it for anything else.

## Lyrics

Isaipetti shows synced lyrics when Navidrome has them: either embedded in the files, or an `.lrc` file next to the song with the same name (`Song.mp3` and `Song.lrc`). Rescan after adding lyric files (*Settings → Library* or wait for the hourly scan).

## Make it reachable

Phones need an **HTTPS** address for Navidrome, like `https://music.example.com`. Two common ways, both free:

- **Cloudflare Tunnel:** no open ports on your router. Install `cloudflared` on the server, create a tunnel in the Cloudflare dashboard, and add a public hostname that points to `http://localhost:4533`.
- **Reverse proxy** such as [Caddy](https://caddyserver.com/) with a domain and ports 80/443 forwarded to the server. A Caddy site is two lines:

  ```
  music.example.com {
      reverse_proxy localhost:4533
  }
  ```

Whatever address you end up with is the **Music server** your friends type in the app.

## Keeping it safe

- Keep Navidrome updated: `docker compose pull && docker compose up -d`.
- Use strong passwords, especially for admin accounts. Navidrome limits repeated login attempts.
- `.env` and `data/` stay on the server; they're ignored by git.
