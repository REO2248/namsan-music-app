# 《남산》 rmenjoy backend API

Reverse-engineered from `http://www.gpsh.edu.kp/index.php/ko/rmenjoy/room`
(인민대학습당 real-time music listening room) and its JS bundles
(`rmenjoy.list.min.js`, `rmenjoy.player.min.js`, `rmenjoy.global.min.js`).

## Basics

- Base: `http://www.gpsh.edu.kp` — **plain HTTP only** (no TLS listener), no auth.
- UI language prefix: `/index.php/{ko|en|cn|ru}/`. All endpoints below are
  relative to `/index.php/ko/`.
- Lists are XML; the player page is HTML; media streams are static files
  under `/data/rmenjoy/music/**` (HTTP 206 range requests supported → seekable).
- The server strips ``" ' < > / ( ) + $ % : ; ? @ & * # \`` from search input
  (`validateSearchText`); the client sanitizes the same way.

## `POST rmenjoy/loadsongs`

Form field: `catcode=<id>`.

- Empty `catcode` → the 10 root categories:
  송가(A) · 명작가요(B) · 대중가요(C) · 전시가요(D) · 영화가요(FILM) ·
  조선민요(I) · 기악곡(L) · 계몽기가요(M) · 가극음악(S) · 아동음악(V)
- `catcode=<category id>` → that category's items. Most categories are flat
  song lists, but some nest further `catalogue` items (e.g. `송가`/A contains
  전인민적송가 and 혁명가요) — clients should handle catalogues at any level.

Response: `<result>` containing `<item>` elements:

```xml
<item>
  <type>catalogue|song</type>
  <id>A | 2164</id>
  <label>송가 | 감나무마을</label>
  <visible>TRUE</visible>
  <songsize>7</songsize>      <!-- catalogues only -->
  <duration>03:40</duration>  <!-- songs only -->
  <access>0</access>          <!-- songs only, always 0 in practice -->
  <language>ko</language>     <!-- songs only -->
  <manage>false</manage><inspector>false</inspector>  <!-- admin flags -->
</item>
```

## `POST rmenjoy/searchsong`

Form field: `search=<text>` — same `<item>` XML as `loadsongs` (songs only).
Matches title, lyrics and credits (full text). **Empty query returns every
song (~600).**

## `GET rmenjoy/room`

The room page HTML. Only place the "새 노래" (new songs) list exists:
`ul#newsongs li.rmenjoy_song_item` rows — `input.check.song@value` = id,
`.rmenjoy_song_title` = title, `.badge.songtime` = `mm:ss`.

## `GET rmenjoy/play?id=<songId>`

Player page HTML. What to extract:

- `<audio|video> <source src="…">` → media path, e.g.
  `/./data/rmenjoy/music/People's/Moranbong/2164.mp3`
  (the filename does **not** match the song id — always resolve via play page).
  `/./data/…` normalizes to `/data/…`.
- `var song_url = "music/…/2164.mp3"` → same path, relative to `/data/rmenjoy/`.
- `var song_title = "…"` and `h3.song-title` → title.
- `.rmenjoy_meta_wraper nobr` → `작사: …` / `작곡: …` credits.
- `#songGasa #words` → lyrics (`<br/>` separated lines).
- Some songs are instrumentals → no `#words`, no credits.
- A `video_frame` div / `.mp4` source marks video songs.

## `GET rmenjoy/download?id=<songId>`

Streams the mp3 as an attachment (`Content-Disposition: filename=<id>.mp3`).

## Playlist model

The web page keeps a client-side list of checked song ids ("연주목록");
the player iframe advances through them. The app does the same via an
ExoPlayer playlist of `namsan://song/<id>` URIs resolved lazily per track.
