# Episode players and video sources

How the Anixart client turns a release into a playable URL. Based on
`network/api/EpisodeApi.java`, `network/api/TypeApi.java`,
`network/api/ReleaseVideoApi.java`, `database/entity/episode/*`,
`parser/*` and `presentation/main/episodes/EpisodesPresenter.java`.

`/video/*` endpoints are **user-uploaded release videos** (UGC), not episode
players. Episode playback is described here.

## Endpoint chain

Episode data is three levels deep: **types → sources → episodes**.

| Step | Request | Response |
|---|---|---|
| 1. Types | `GET /episode/{releaseId}?token=` | `TypesResponse { code, types: [Type] }` |
| 2. Sources | `GET /episode/{releaseId}/{typeId}` | `SourcesResponse { code, sources: [Source] }` |
| 3. Episodes | `GET /episode/{releaseId}/{typeId}/{sourceId}?sort=&token=` | `EpisodeResponse { code, episodes: [Episode] }` |
| By position | `GET /episode/target/{releaseId}/{sourceId}/{position}` | `EpisodeTargetResponse { code, episode }` |
| Updates | `GET /episode/updates/{releaseId}/{page}` | `PageableResponse<EpisodeUpdate>` |
| Watch | `POST /episode/watch/{releaseId}/{sourceId}[/{position}]?token=` | `EpisodeWatchResponse` |
| Unwatch | `POST /episode/unwatch/{releaseId}/{sourceId}[/{position}]?token=` | `EpisodeUnwatchResponse` |

Only step 1 and step 3 require the token. Sources and target are public.
`sort` is an integer sort mode (default `0`). `position` is the episode number
inside the source, not the database id.

## JSON shapes

`Type` (`database/entity/episode/type/Type.java`):

```json
{
  "id": 1,
  "name": "Kodik",
  "icon": "https://...",
  "is_sub": false,
  "workers": "Studio A, Studio B",
  "episodes_count": 12,
  "view_count": 34567,
  "pinned": true,
  "quality": 1
}
```

`Source` (`database/entity/episode/source/Source.java`):

```json
{ "id": 10, "name": "Kodik", "quality": 1, "episodes_count": 12, "type": { ... } }
```

`Episode` (`database/entity/episode/Episode.java`):

```json
{
  "id": 123456,
  "name": "Серия 1",
  "position": 1,
  "url": "https://kodik.cc/serial/.../720p",
  "iframe": true,
  "quality": 1,
  "is_filler": false,
  "is_watched": false,
  "playbackPosition": 0,
  "addedDate": 1700000000,
  "releaseId": 100,
  "sourceId": 10
}
```

Notes:

- `url` is either an embed page (Kodik, Sibnet, VK, RuTube, AniLibria, ...) or
  a direct `mp4`/`m3u8` link.
- `iframe == true` means the client may show it in a WebView; otherwise it
  tries a native parser and falls back to the external player / browser.
- `is_filler` and `is_watched` are the only explicit snake_case keys on
  `Episode`; the other fields use the camelCase Java names above.
- `quality` is a server-side numeric hint; the actual stream variants come from
  the parser, keyed `Default`, `360p`, `480p`, `720p`, `1080p`, `1440p`,
  `2160p`.

## Player resolution

`Parser.Companion.get(url)` lower-cases the host and selects a parser. If the
host is unknown, the URL is opened externally (or in the WebView when
`iframe` is set).

| Hosts | Parser | What it does |
|---|---|---|
| `kodik.cc`, `kodik.info`, `aniqit.com`, `kodikplayer.com` | `KodikParser` | Extracts the `d=` domain and `link=` from the embed URL, calls `https://kodikres.com/api/video-links?p=<key>&link=<url>&d=<domain>`, then regexes the `"360"/"480"/"720"/"1080"` entries into `360p..1080p`. |
| `video.sibnet.ru` | `SibnetParser` | Fetches the embed page, regexes `src: "(.*?)"`, resolves it against `https://video.sibnet.ru`, follows the redirect and strips `:443`. Yields a single `Default` URL. Optional `SIBNET_USER_AGENT` / `SIBNET_RAND_USER_AGENT` prefs. |
| `rutube.ru` | `RuTubeParser` | Extracts video id and optional private `p`, calls `https://rutube.ru/api/play/options/{id}/?no_404=true&referer&pver=v2` with `Referer: https://rutube.ru/`. Reads `video_balancer.m3u8`, downloads the playlist and exposes variants as `360p..2160p`. Region-locked / live streams are rejected. |
| `vkvideo.ru`, `vk.com` | `VKVideoParser` | Parses `video_ext.php?oid=&id=&hash=`, calls `https://api.vk.com/method/video.get` with `anonymous_token`, `device_id`, `lang`, `v` and `videos=oid_id[_hash]`, then maps `files` (`mp4_360`, `mp4_480`, `mp4_720`, `mp4_1080`, `hls`) to quality keys. |
| `anilibria.tv`, `new.anilib.one`, `anixart.libria.fun`, `anilibria.top` | `AniLibriaParser` | Parses `id=<release>&ep=<ordinal>` from the URL, calls `https://anilibria.top/api/v1/anime/releases/{id}`, finds the episode with that `ordinal` and takes `hls480` / `hls720` / `hls1080`. Falls back to regexing `"file":"..."` from the embed page. |
| `sovetromantica.com` | `SovetRomanticaParser` | Scrapes the embed page for direct links. |
| `my.mail.ru` | `MailRuParser` | Uses the `my.mail.ru/+/video/meta/` endpoint, forwards the `Set-Cookie` header and extracts the video URL. |
| `www.myvi.top`, `myvi.top` | `MyviParser` | Scrapes the embed page for qualities. |
| `secvideo1.online`, `csst.online`, `sstrge.online` | `AllvideoParser` | Scrapes the embed page for `360p..1080p` links. |
| `studiomir.club`, `api.studiomir.club` | `StudioMirParser` | Calls `https://api.studiomir.club/api?ani=<id>` and maps the returned qualities. |
| `ok.ru`, `m.ok.ru` | `OKParser` | Extracts the OK.ru video metadata and direct links. |

Every parser returns two maps on the `Parser` instance:

- `qualities` (`LinkedHashMap<String, String>`) — `"Default"`, `"360p"`,
  `"480p"`, `"720p"`, `"1080p"`, `"1440p"`, `"2160p"` → URL.
- `headers` — usually `Referer` (the original embed URL) and sometimes
  `Cookie` / a custom `User-Agent`, which the native player must send with the
  stream request.

Parsers do their own HTTP with OkHttp (20 s timeout, redirects enabled). They
are **not** authenticated with the Anixart token; they rely on the public embed
page and provider-specific headers.

## Playback flow

1. `EpisodesPresenter` loads types, then sources, then episodes.
2. On tap it checks `prefs` for the preferred player. Options:
   WebView (`onWebPlayer(url, iframe)`), built-in parser, external player.
3. If the host is in the parser registry, the parser runs and fills the
   quality map; the player picks the best quality or asks the user.
4. `POST /episode/watch/...` (and `history/add`) records progress; the episode
   payload already contains `is_watched` and `playback_position`, so the UI can
   resume without a separate progress request.
5. `POST /episode/unwatch/...` clears the watched flag.

Kodik additionally has an ad path (`KodikAdActivity`) that opens the embed page
in a WebView with a desktop `User-Agent`, and a quality-selection dialog.
