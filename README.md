# 남산 음악감상 (namsan-player)

Native Android client for the 인민대학습당 《남산》 real-time music listening
room at `http://www.gpsh.edu.kp/index.php/ko/rmenjoy/room`, reimplemented as
a proper music app (Kotlin + Jetpack Compose + Media3/ExoPlayer).

## Features

- **새 노래** — the new-songs list the site embeds in its room page
- **차례** — browse the 10 categories (송가 … 아동음악, ~600 songs)
- **검색** — full-text search across title, lyrics and credits
- Playback queue (tap to play, `+` to enqueue, reorder/remove in player)
- Background playback with media notification (Media3 `MediaSessionService`)
- Lyrics, lyricist/composer credits, song download (DownloadManager)

## Backend notes

Unauthenticated plain-HTTP XML/HTML API — see [docs/api.md](docs/api.md) for
the full endpoint reference. In short:

| Endpoint | Purpose |
| --- | --- |
| `POST /index.php/ko/rmenjoy/loadsongs` (`catcode=`) | category list / category songs |
| `POST /index.php/ko/rmenjoy/searchsong` (`search=`) | song search (empty = all) |
| `GET  /index.php/ko/rmenjoy/play?id=` | player page → media URL, title, credits, lyrics |
| `GET  /index.php/ko/rmenjoy/download?id=` | mp3 download |
| `GET  /index.php/ko/rmenjoy/room` | embedded "새 노래" list |

The app uses `android:usesCleartextTraffic="true"` because the site has no
HTTPS endpoint.

## Architecture

- `api/` — `NamsanApi` (OkHttp + coroutines), `RmenjoyParser` (Jsoup for both
  XML lists and the HTML play page, JVM-unit-tested), `SongRepository`
  (shared detail cache).
- `player/PlaybackService` — `MediaSessionService` hosting ExoPlayer.
  `MediaItem`s use `namsan://song/<id>` URIs which a `ResolvingDataSource`
  resolves to real mp3 URLs on demand (fetch once, cached).
- `ui/` — single-activity Compose UI: `MainScreen` (tabs + mini player),
  `SongList`, `PlayerScreen` (seek, lyrics, queue, download),
  `MainViewModel` (state + `MediaController`).

## Build & test

```bash
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # parser unit tests (real captured fixtures)
./gradlew :app:lintDebug            # Android lint
```

Needs Android SDK 35 (`local.properties` `sdk.dir=` or `ANDROID_SDK_ROOT`)
and JDK 17.
