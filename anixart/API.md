# Anixart API (reverse-engineered)

Reference for the Anixart Android client (`com.swiftsoft.anixartd`, version 10.5 /
versionCode 26101718). Derived from `hints/retrofit.txt` and the decompiled
`network/api/*.java`, `network/response/*`, `dagger/module/ApiModule` and
`utils/domain/moderation/Police` sources. Not affiliated with Anixart.

## Transport

- **Base URL:** `https://api-s.anixsekai.com/` (preference `API_BASE_URL`).
  The OkHttp interceptor rewrites the request host/port/scheme to this base, so
  the client can be pointed at a mirror without changing Retrofit paths.
- **Client:** OkHttp 4 with `HTTP_2, HTTP_1_1`, 10 s connect/read/write timeouts,
  Jackson converter (`@JsonIgnoreProperties(ignoreUnknown = true)`) and RxJava2.
- All paths below are relative to the base URL. Request/response bodies are JSON
  unless the row says `form:` (then `application/x-www-form-urlencoded`).
- `file:` rows are `multipart/form-data`; the file part is named `image` and may
  be accompanied by a `name` text part (original filename).
- The API is RPC-style and not RESTful: many state-changing operations (vote,
  delete, subscribe, mute, pin) are `GET` requests with no body.

## Auth token

The token is **not** sent in an `Authorization` header. It is passed as the
`token` **query parameter** on almost every endpoint:

```
GET /profile/info?token=<TOKEN>
```

Auth endpoints take credentials as form fields instead. Login flow:

1. `POST /auth/checkLogin` (`form: login`) → `{code, available, suggested_logins}`
   (`code == 2` = invalid login).
2. `POST /auth/signIn` (`form: login, password`) → `{code, profile, profile_token}`
   where `profile_token.token` is the token.
3. Store it (prefs key `TOKEN`) and append `?token=...` to later calls.
4. Social sign-in: `POST /auth/{google,telegram,vk,yandex}` with the provider
   token; `POST /auth/{provider}` is also used for sign-up variants with
   `login`, `email` and the provider token.
5. E-mail verification / password restore use `auth/resend`, `auth/verify`,
   `auth/restore`, `auth/restore/verify`, `auth/restore/resend`.

`POST /auth/firebase` only takes `?token=` and refreshes/validates the session.
`GET /profile/deletion/status`, `profile/health/status` etc. use the same token.

## Headers

An OkHttp interceptor adds these headers to every request:

| Header | Value |
|---|---|
| `User-Agent` | `AnixartApp/%s-%d (Android %s; SDK %s; %s; %s %s; %s)` → `AnixartApp/10.5-26101718 (Android 14; SDK 34; arm64-v8a; Google Pixel 7; ru)` |
| `Sign` | Obfuscated anti-tamper signature from `Police` (APK certificate SHA-1, package name, random chars, timestamp, Base64). Not a standard HMAC; implementation is obfuscated. |
| `X-Media-Session` | Media-codec capability blob: `MediaCodecBridge.st(context)` (72-byte Base64) transformed by `CodecBufferUtil` into an 80-byte Base64 value with a random nonce. |
| `X-Codec-Profile` | Display/codec fingerprint from `ViewportSizeCalculator`: `a.b.c[.display_metrics[.seal]]`. |

Per-endpoint headers:

| Header | Endpoints |
|---|---|
| `X-Long-Code: 1` | `auth/resend`, `auth/restore`, `auth/restore/resend`, `auth/signUp`, `auth/verify`, `POST /video/appeal/add` |
| `API-Version` | `profile/preference/email/change`, `related/{relatedId}/{page}`, `search/releases/{page}` (value supplied by the caller) |

There is **no** request-body signature, nonce, timestamp or `Authorization`
scheme. The three custom headers above are the only client attestation; a
third-party client can send a normal `User-Agent` and omit the obfuscated
`Sign`/codec headers, though the server may rate-limit or reject such requests.

## Response envelope

Every JSON response extends `Response`:

```json
{ "code": 0, ... }
```

| `code` | Meaning |
|---|---|
| `0` | success (`Response.SUCCESSFUL`) |
| `1` | generic failure (`Response.FAILED`) |
| `402` | banned (`Response.BANNED`) |
| `403` | permanently banned (`Response.PERM_BANNED`) |

HTTP status is normally `200` even for failures — check `code`. Endpoints whose
Java return type is the bare `Response` only return `{ "code": n }`.

Entity-specific codes: `CheckLoginResponse.INVALID_LOGIN = 2`;
`PageableResponse.INVALID_QUERY = 0`.

## Paging

Paginated endpoints use a **0-based** `{page}` path segment:

```
GET /feed/latest/all/0
GET /release/comment/all/{releaseId}/1
```

The client keeps `currentPage` starting at `0` and increments it by one; the
first request is page `0`. Some collection/recommendation endpoints also take a
`previous_page` query parameter, initialised to `-1`.

The response is a `PageableResponse<T>`:

```json
{
  "code": 0,
  "content": [ ... ],
  "current_page": 0,
  "total_count": 1234,
  "total_page_count": 62
}
```

Non-paginated list responses use named arrays (`types`, `sources`, `episodes`,
`reasons`, ...). `POST` search/filter endpoints put the search/filter object in
the JSON body and the page in the path.

## Endpoints

## Authentication

Login, registration, social sign-in, e-mail verification and password restore. All are `application/x-www-form-urlencoded`.

| Method | Path | Params |
|---|---|---|
| POST | `/auth/checkLogin` | form: login |
| POST | `/auth/firebase` | query: token |
| POST | `/auth/resend` | form: login, email, password, vkAccessToken, googleIdToken, telegramIdToken, yandexAccessToken, hash |
| POST | `/auth/restore` | form: data |
| POST | `/auth/restore/resend` | form: data, password, hash |
| POST | `/auth/restore/verify` | form: data, password, hash, code |
| POST | `/auth/signIn` | form: login, password |
| POST | `/auth/google` | form: googleIdToken |
| POST | `/auth/telegram` | form: telegramIdToken |
| POST | `/auth/vk` | form: vkAccessToken |
| POST | `/auth/yandex` | form: yandexAccessToken |
| POST | `/auth/signUp` | form: login, email, password |
| POST | `/auth/google` | form: login, email, googleIdToken |
| POST | `/auth/telegram` | form: login, email, telegramIdToken |
| POST | `/auth/vk` | form: login, email, vkAccessToken |
| POST | `/auth/yandex` | form: login, email, yandexAccessToken |
| POST | `/auth/verify` | form: login, email, password, vkAccessToken, googleIdToken, telegramIdToken, yandexAccessToken, hash, code |

## Config

Remote config: player settings, feature toggles and mirror URL list.

| Method | Path | Params |
|---|---|---|
| GET | `/config/anixplayer` | — |
| GET | `/config/toggles` | query: version_code, is_beta, is_api_alt, token |
| GET | `/config/urls` | query: version_code, is_beta, token |

## Profile

Own and public profiles, social links, login history, roles and moderation.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/login/history/all/{id}/{page}` | query: token |
| GET | `/profile/info` | query: token |
| POST | `/profile/process/{id}` | query: token; body: ProfileProcessRequest |
| GET | `/profile/{id}` | query: token |
| GET | `/profile/social/{id}` | query: token |
| GET | `/profile/{id}/badge/list` | query: recent, token |
| GET | `/profile/{id}/badge/list/{categoryId}/{page}` | query: token |

## Profile preferences

Account, notification, privacy, theme and badge preferences.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/preference/notification/article/edit` | query: token |
| GET | `/profile/preference/notification/comment/edit` | query: token |
| GET | `/profile/preference/notification/episode/edit` | query: token |
| GET | `/profile/preference/notification/episode/first/edit` | query: token |
| GET | `/profile/preference/notification/my` | query: token |
| GET | `/profile/preference/notification/my/article/comment/edit` | query: token |
| GET | `/profile/preference/notification/my/collection/comment/edit` | query: token |
| GET | `/profile/preference/notification/release/all/{page}` | query: token |
| GET | `/profile/preference/notification/release/type/{releaseId}` | query: token |
| POST | `/profile/preference/notification/release/type/edit` | query: token; body: ProfileReleaseTypeNotificationPreferencesEditRequest |
| POST | `/profile/preference/notification/status/edit` | query: token; body: ProfileStatusNotificationPreferencesEditRequest |
| POST | `/profile/preference/notification/type/edit` | query: token; body: ProfileTypeNotificationPreferencesEditRequest |
| GET | `/profile/preference/notification/related/release/edit` | query: token |
| GET | `/profile/preference/notification/report/process/edit` | query: token |
| GET | `/profile/preference/notification/selected/releases/edit` | query: token |
| GET | `/profile/preference/badge/edit/{id}` | query: token |
| POST | `/profile/preference/badge/showcase/pin/{id}` | query: token |
| GET | `/profile/preference/badge/remove` | query: token |
| POST | `/profile/preference/badge/showcase/reorder` | query: token; body: ReorderBadgeShowcaseRequest |
| POST | `/profile/preference/badge/showcase/unpin/{id}` | query: token |
| GET | `/profile/preference/avatar/delete` | query: token |
| POST | `/profile/preference/avatar/edit` | query: token; file: image; part: name |
| POST | `/profile/preference/email/change` | query: token; form: current_email, current_password, new_email; header: API-Version |
| POST | `/profile/preference/email/resend` | query: token; form: new_email, current_email, current_password, hash |
| GET | `/profile/preference/email/verify` | query: new_email, code, hash, token |
| POST | `/profile/preference/login/change` | query: login, token |
| POST | `/profile/preference/login/info` | query: token |
| POST | `/profile/preference/password/change` | query: token; form: current, new |
| POST | `/profile/preference/login/history/delete` | query: token |
| POST | `/profile/preference/episode-widget/edit` | query: hidden, token |
| POST | `/profile/preference/google/bind` | query: token; form: idToken |
| POST | `/profile/preference/google/unbind` | query: token |
| GET | `/profile/preference/my` | query: token |
| POST | `/profile/preference/section/edit` | query: token; body: SelectPinnedSectionRequest |
| POST | `/profile/preference/privacy/counts/edit` | query: token; body: PrivacyEditRequest |
| POST | `/profile/preference/privacy/friendRequests/edit` | query: token; body: PrivacyEditRequest |
| GET | `/profile/preference/privacy/incognito/edit` | query: token |
| POST | `/profile/preference/privacy/social/edit` | query: token; body: PrivacyEditRequest |
| POST | `/profile/preference/privacy/stats/edit` | query: token; body: PrivacyEditRequest |
| POST | `/profile/preference/themes/edit` | query: token; body: SelectThemeRequest |
| GET | `/profile/preference/social` | query: token |
| POST | `/profile/preference/social/edit` | query: token; body: SocialPagesEditRequest |
| POST | `/profile/preference/status/edit` | query: token; body: StatusEditRequest |
| POST | `/profile/preference/telegram/bind` | query: token; form: idToken |
| POST | `/profile/preference/telegram/unbind` | query: token |
| POST | `/profile/preference/vk/bind` | query: token; form: accessToken |
| POST | `/profile/preference/vk/unbind` | query: token |
| POST | `/profile/preference/yandex/bind` | query: token; form: accessToken |
| POST | `/profile/preference/yandex/unbind` | query: token |

## Friends

Friend lists, requests and recommendations.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/friend/all/{id}/{page}` | query: token |
| GET | `/profile/friend/recommendations` | query: token |
| GET | `/profile/friend/request/hide/{id}` | query: token |
| GET | `/profile/friend/request/remove/{id}` | query: token |
| GET | `/profile/friend/request/send/{id}` | query: token |
| GET | `/profile/friend/requests/in/{page}` | query: token |
| GET | `/profile/friend/requests/in/last` | query: count, token |
| GET | `/profile/friend/requests/out/{page}` | query: token |
| GET | `/profile/friend/requests/out/last` | query: count, token |

## Profile lists

Watch-status lists (planned / watching / completed / dropped / on hold).

| Method | Path | Params |
|---|---|---|
| GET | `/profile/list/add/{status}/{r_id}` | query: token |
| GET | `/profile/list/delete/{status}/{r_id}` | query: token |
| GET | `/profile/list/all/{status}/{page}` | query: sort, filter_announce, token |
| GET | `/profile/list/all/{p_id}/{status}/{page}` | query: sort, filter_announce, token |

## Blocklist

Blocked profiles.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/blocklist/add/{id}` | query: token |
| GET | `/profile/blocklist/all/{page}` | query: token |
| GET | `/profile/blocklist/remove/{id}` | query: token |

## Account health

Enforcement status and appeals.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/health/enforcement/account/all/{page}` | query: token |
| POST | `/profile/health/enforcement/{id}/appeal` | query: token; body: ProfileEnforcementAppealRequest |
| GET | `/profile/health/enforcement/content/all/{page}` | query: token |
| GET | `/profile/health/enforcement/{id}` | query: token |
| GET | `/profile/health/status` | query: token |

## Account deletion

Request / cancel / status of account deletion.

| Method | Path | Params |
|---|---|---|
| POST | `/profile/deletion/cancel` | query: token |
| POST | `/profile/deletion/request` | query: token; form: password |
| GET | `/profile/deletion/status` | query: token |

## Release votes

Releases the user has or has not voted for.

| Method | Path | Params |
|---|---|---|
| GET | `/profile/vote/release/unvoted/{page}` | query: token |
| GET | `/profile/vote/release/voted/{p_id}/{page}` | query: sort, token |
| GET | `/profile/vote/release/unvoted/last` | query: token |

## Feed

Latest and paginated feed, including articles.

| Method | Path | Params |
|---|---|---|
| GET | `/feed/all/{page}` | query: channel_id, date, token |
| GET | `/feed/latest` | query: token |
| GET | `/feed/latest/all/{page}` | query: token |

## Discover

Editorial/algorithmic discovery blocks.

| Method | Path | Params |
|---|---|---|
| POST | `/discover/comments` | — |
| POST | `/discover/discussing` | query: token |
| POST | `/discover/interesting` | — |
| POST | `/discover/recommendations/{page}` | query: previous_page, token |
| POST | `/discover/watching/{page}` | query: token |

## Releases

Release cards, random releases, votes, comments, related, streaming platforms.

| Method | Path | Params |
|---|---|---|
| GET | `/release/{r_id}/badges/{page}` | query: token |
| GET | `/release/vote/delete/{r_id}` | query: token |
| GET | `/release/random` | query: extended_mode, token |
| GET | `/release/collection/{id}/random` | query: extended_mode, token |
| GET | `/release/random/favorite` | query: extended_mode, token |
| GET | `/release/random/profile/list/{p_id}/{status}` | query: extended_mode, token |
| GET | `/release/{r_id}` | query: extended_mode, token |
| GET | `/release/vote/add/{r_id}/{vote}` | query: token |
| POST | `/release/comment/add/{releaseId}` | query: token; body: CommentAddRequest |
| GET | `/release/comment/{releaseId}` | query: token |
| GET | `/release/comment/all/{releaseId}/{page}` | query: sort, token |
| GET | `/release/comment/delete/{commentId}` | query: token |
| POST | `/release/comment/edit/{commentId}` | query: token; body: CommentEditRequest |
| POST | `/release/comment/process/{commentId}` | query: token; body: CommentProcessRequest |
| GET | `/release/comment/all/profile/{p_id}/{page}` | query: sort, token |
| POST | `/release/comment/replies/{commentId}/{page}` | query: sort, token |
| GET | `/release/comment/vote/{commentId}/{vote}` | query: token |
| GET | `/release/comment/votes/{commentId}/{page}` | query: sort, token |
| GET | `/release/streaming/platform/{releaseId}` | — |

## Related

Related releases.

| Method | Path | Params |
|---|---|---|
| GET | `/related/{relatedId}/{page}` | query: token; header: API-Version |

## Episodes & sources

Episode types → sources → episodes, watch progress and updates.

| Method | Path | Params |
|---|---|---|
| GET | `/episode/target/{releaseId}/{sourceId}/{position}` | — |
| GET | `/episode/{releaseId}/{typeId}/{sourceId}` | query: sort, token |
| GET | `/episode/{releaseId}/{typeId}` | — |
| GET | `/episode/{releaseId}` | query: token |
| POST | `/episode/unwatch/{releaseId}/{sourceId}/{position}` | query: token |
| POST | `/episode/unwatch/{releaseId}/{sourceId}` | query: token |
| GET | `/episode/updates/{releaseId}/{page}` | — |
| POST | `/episode/watch/{releaseId}/{sourceId}/{position}` | query: token |
| POST | `/episode/watch/{releaseId}/{sourceId}` | query: token |

## Episode types

Global episode-type catalogue, widgets and pinning.

| Method | Path | Params |
|---|---|---|
| GET | `/type/{typeId}/channel` | query: token |
| GET | `/type/widget/hide/{typeId}` | query: permanent, token |
| GET | `/type/pin/{releaseId}/{typeId}` | query: token |
| GET | `/type/all` | query: token |
| GET | `/type/widget/unhide/{typeId}` | query: token |
| GET | `/type/unpin/{releaseId}/{typeId}` | query: token |

## Favorites

Favorite releases.

| Method | Path | Params |
|---|---|---|
| GET | `/favorite/add/{r_id}` | query: token |
| GET | `/favorite/delete/{r_id}` | query: token |
| GET | `/favorite/all/{page}` | query: sort, filter_announce, token |

## History

Watch history.

| Method | Path | Params |
|---|---|---|
| GET | `/history/add/{r_id}/{s_id}/{position}` | query: token |
| GET | `/history/delete/{r_id}` | query: token |
| GET | `/history/{page}` | query: token |

## Collections

Collections, their releases and comments.

| Method | Path | Params |
|---|---|---|
| GET | `/collection/{id}` | query: token |
| GET | `/collection/all/{page}` | query: previous_page, where, sort, token |
| GET | `/collection/all/profile/{p_id}/{page}` | query: token |
| GET | `/collection/all/release/{r_id}/{page}` | query: sort, token |
| GET | `/collection/{id}/releases/{page}` | query: token |
| POST | `/collection/comment/add/{collectionId}` | query: token; body: CommentAddRequest |
| GET | `/collection/comment/{collectionId}` | query: token |
| GET | `/collection/comment/all/{collectionId}/{page}` | query: sort, token |
| GET | `/collection/comment/delete/{commentId}` | query: token |
| POST | `/collection/comment/edit/{commentId}` | query: token; body: CommentEditRequest |
| POST | `/collection/comment/process/{commentId}` | query: token; body: CommentProcessRequest |
| GET | `/collection/comment/all/profile/{p_id}/{page}` | query: sort, token |
| POST | `/collection/comment/replies/{commentId}/{page}` | query: sort, token |
| GET | `/collection/comment/vote/{commentId}/{vote}` | query: token |
| GET | `/collection/comment/votes/{commentId}/{page}` | query: sort, token |

## Favorite collections

Collections bookmarked by the user.

| Method | Path | Params |
|---|---|---|
| GET | `/collectionFavorite/add/{id}` | query: token |
| GET | `/collectionFavorite/delete/{id}` | query: token |
| GET | `/collectionFavorite/all/{page}` | query: token |

## My collections

Owned collections and their contents.

| Method | Path | Params |
|---|---|---|
| POST | `/collectionMy/create` | query: token; body: CollectionCreateEditRequest |
| GET | `/collectionMy/delete/{collectionId}` | query: token |
| POST | `/collectionMy/edit/{collectionId}` | query: token; body: CollectionCreateEditRequest |
| POST | `/collectionMy/editImage/{collectionId}` | query: token; file: image; part: name |
| GET | `/collectionMy/release/add/{collectionId}` | query: release_id, token |
| GET | `/collectionMy/{id}/releases` | query: token |

## Favorite release videos

Bookmarked UGC release videos.

| Method | Path | Params |
|---|---|---|
| GET | `/releaseVideoFavorite/add/{r_id}` | query: token |
| GET | `/releaseVideoFavorite/delete/{r_id}` | query: token |
| GET | `/releaseVideoFavorite/all/{p_id}/{page}` | query: token |

## Articles

Channel articles, suggestions, comments, votes and events.

| Method | Path | Params |
|---|---|---|
| GET | `/article/{a_id}` | query: token |
| POST | `/article/create/{c_id}` | query: token; body: ArticleCreateEditRequest |
| POST | `/article/delete/{a_id}` | query: token |
| POST | `/article/edit/{a_id}` | query: token; body: ArticleCreateEditRequest |
| GET | `/article/edit/pinned/{a_id}` | query: is_pinned, token |
| POST | `/article/event` | query: token; body: ArticleEventRequest |
| GET | `/article/mute/{a_id}` | query: token |
| GET | `/article/reposts/{a_id}/{page}` | query: sort, token |
| GET | `/article/unmute/{a_id}` | query: token |
| GET | `/article/vote/{a_id}/{vote}` | query: token |
| POST | `/article/votes/{a_id}/{page}` | query: sort, token |
| POST | `/article/comment/add/{articleId}` | query: token; body: CommentAddRequest |
| GET | `/article/comment/{articleId}` | query: token |
| GET | `/article/comment/all/{a_id}/{page}` | query: sort, token |
| GET | `/article/comment/all/{a_id}/popular` | query: token |
| GET | `/article/comment/delete/{commentId}` | query: token |
| POST | `/article/comment/edit/{commentId}` | query: token; body: CommentEditRequest |
| POST | `/article/comment/process/{commentId}` | query: token; body: CommentProcessRequest |
| GET | `/article/comment/all/profile/{p_id}/{page}` | query: sort, token |
| POST | `/article/comment/replies/{commentId}/{page}` | query: sort, token |
| GET | `/article/comment/vote/{commentId}/{vote}` | query: token |
| POST | `/article/comment/votes/{commentId}/{page}` | query: sort, token |
| GET | `/article/suggestion/{a_id}` | query: token |
| POST | `/article/suggestion/all/{page}` | query: token; body: ArticleSuggestionsFilterRequest |
| POST | `/article/suggestion/create/{b_id}` | query: token; body: ArticleSuggestionCreateEditRequest |
| POST | `/article/suggestion/delete/{a_id}` | query: token |
| POST | `/article/suggestion/edit/{a_id}` | query: token; body: ArticleSuggestionCreateEditRequest |
| POST | `/article/suggestion/publish/{a_id}` | query: is_signed, token |

## Channels

Channels, subscriptions, staff, blocks and avatars/covers.

| Method | Path | Params |
|---|---|---|
| POST | `/channel/{c_id}/article/all/{page}` | query: token |
| POST | `/channel/avatar/upload/{c_id}` | query: token; file: image |
| GET | `/channel/{c_id}/block/{p_id}` | query: token |
| POST | `/channel/{c_id}/block/manage` | query: token; body: ChannelBlockManageRequest |
| GET | `/channel/{c_id}/block/all/{page}` | query: token |
| GET | `/channel/blog/{p_id}` | query: token |
| GET | `/channel/{c_id}` | query: token |
| POST | `/channel/all/{page}` | query: token; body: ChannelsFilterRequest |
| POST | `/channel/cover/delete/{c_id}` | query: token |
| POST | `/channel/cover/upload/{c_id}` | query: token; file: image |
| POST | `/channel/create` | query: token; body: ChannelCreateEditRequest |
| POST | `/channel/blog/create` | query: token |
| POST | `/channel/delete/{c_id}` | query: token |
| POST | `/channel/edit/{c_id}` | query: token; body: ChannelCreateEditRequest |
| GET | `/channel/{c_id}/editor/available` | query: is_suggestion, is_edit_mode, token |
| GET | `/channel/editor/available/all` | query: token, only_subscribed |
| POST | `/channel/mute/{c_id}` | query: token |
| GET | `/channel/mute/all/{page}` | query: token |
| POST | `/channel/{c_id}/permission/manage` | query: token; body: ChannelPermissionManageRequest |
| POST | `/channel/{c_id}/permission/all/{page}` | query: token; body: ChannelPermissionsFilterRequest |
| GET | `/channel/recommendations/{page}` | query: token, is_blog, exclude_subscribed |
| POST | `/channel/subscribe/{c_id}` | query: token |
| GET | `/channel/subscription/count` | query: token |
| GET | `/channel/subscription/all/{page}` | query: token, sort |
| POST | `/channel/unmute/{c_id}` | query: token |
| POST | `/channel/unsubscribe/{c_id}` | query: token |

## Notifications

Notification feed and per-type deletion / preferences.

| Method | Path | Params |
|---|---|---|
| GET | `/notification/all/{page}` | query: token |
| GET | `/notification/article/comments/{page}` | query: token |
| GET | `/notification/articles/{page}` | query: token |
| GET | `/notification/collectionComments/{page}` | query: token |
| GET | `/notification/count` | query: token |
| GET | `/notification/delete/all` | query: token |
| GET | `/notification/articleComment/delete/{id}` | query: token |
| GET | `/notification/article/delete/{id}` | query: token |
| GET | `/notification/collectionComment/delete/{id}` | query: token |
| GET | `/notification/episode/delete/{id}` | query: token |
| GET | `/notification/friend/delete/{id}` | query: token |
| GET | `/notification/my/article/comment/delete/{id}` | query: token |
| GET | `/notification/my/collection/comment/delete/{id}` | query: token |
| GET | `/notification/related/release/delete/{id}` | query: token |
| GET | `/notification/releaseComment/delete/{id}` | query: token |
| GET | `/notification/episodes/{page}` | query: token |
| GET | `/notification/friends/{page}` | query: token |
| GET | `/notification/read` | query: token |
| GET | `/notification/related/release/{page}` | query: token |
| GET | `/notification/releaseComments/{page}` | query: token |

## Search

Search and filtered search for every entity type.

| Method | Path | Params |
|---|---|---|
| POST | `/search/articles/{page}` | query: token; body: ArticlesSearchRequest |
| POST | `/search/channels/{page}` | query: token; body: ChannelsSearchRequest |
| POST | `/search/channel/{c_id}/subscribers/{page}` | query: token; body: SearchRequest |
| POST | `/search/collections/{page}` | query: token; body: SearchRequest |
| POST | `/search/favoriteCollections/{page}` | query: token; body: SearchRequest |
| POST | `/search/favorites/{page}` | query: token; body: SearchRequest |
| POST | `/search/feed/{page}` | query: token; body: SearchRequest |
| POST | `/search/history/{page}` | query: token; body: SearchRequest |
| POST | `/search/profileCollections/{p_id}/{page}` | query: release_id, token; body: SearchRequest |
| POST | `/search/profile/list/{status}/{page}` | query: token; body: SearchRequest |
| POST | `/search/profiles/{page}` | query: token; body: SearchRequest |
| POST | `/search/releases/{page}` | query: token; body: SearchRequest; header: API-Version |

## Release videos (UGC)

User-uploaded release videos, categories and appeals. Not episode players.

| Method | Path | Params |
|---|---|---|
| GET | `/video/release/categories` | — |
| GET | `/video/release/{releaseId}/category/{categoryId}/{page}` | — |
| GET | `/video/release/{releaseId}` | — |
| GET | `/video/profile/{p_id}/{page}` | query: token |
| GET | `/video/release/{releaseId}/{page}` | — |
| POST | `/video/appeal/add` | query: token; body: ReleaseVideoAppealRequest |
| GET | `/video/appeal/profile/{page}` | query: token |
| GET | `/video/appeal/profile/last` | query: token |
| POST | `/video/appeal/delete/{appealId}` | query: token |

## Reports

Report reasons and report submission for every entity type.

| Method | Path | Params |
|---|---|---|
| POST | `/report/article` | query: token; body: ReportRequest<Long> |
| POST | `/report/comment/article` | query: token; body: ReportRequest<Long> |
| GET | `/report/comment/article/reasons` | query: token |
| GET | `/report/article/reasons` | query: token |
| POST | `/report/channel` | query: token; body: ReportRequest<Long> |
| GET | `/report/channel/reasons` | query: token |
| POST | `/report/collection` | query: token; body: ReportRequest<Long> |
| POST | `/report/comment/collection` | query: token; body: ReportRequest<Long> |
| GET | `/report/comment/collection/reasons` | query: token |
| GET | `/report/collection/reasons` | query: token |
| POST | `/report/episode` | query: token; body: ReportRequest<Episode> |
| GET | `/report/episode/reasons` | query: token |
| POST | `/report/profile` | query: token; body: ReportRequest<Long> |
| GET | `/report/profile/reasons` | query: token |
| POST | `/report/release` | query: token; body: ReportRequest<Long> |
| POST | `/report/comment/release` | query: token; body: ReportRequest<Long> |
| GET | `/report/comment/release/reasons` | query: token |
| GET | `/report/release/reasons` | query: token |

## Achievements

Achievement preview and claim by code.

| Method | Path | Params |
|---|---|---|
| GET | `/achievement/get/{code}` | query: badge_id, token |
| GET | `/achievement/preview/{code}` | — |

## Import

Bookmark import.

| Method | Path | Params |
|---|---|---|
| POST | `/import/bookmarks` | query: token; body: BookmarksImportRequest |
| POST | `/import/status` | query: token |

## Export

Bookmark export.

| Method | Path | Params |
|---|---|---|
| POST | `/export/bookmarks` | query: sort, token; body: BookmarksExportRequest |

## Filter

Catalogue filter/search.

| Method | Path | Params |
|---|---|---|
| POST | `/filter/{page}` | query: extended_mode, token; body: FilterRequest |

## Schedule

Weekly release schedule.

| Method | Path | Params |
|---|---|---|
| GET | `/schedule` | — |

## Roles

Role member lists.

| Method | Path | Params |
|---|---|---|
| GET | `/role/all/{page}/{role_id}` | query: token |
