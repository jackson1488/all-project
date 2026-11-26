# Social OAuth flows (Telegram / VK / Google / Yandex)

Reverse-engineered from the decompiled Android client:
`network/api/AuthApi.java`, `ui/activity/AuthActivity.java`,
`ui/activity/TelegramAuthActivity.java`, `utils/network/TelegramAuthHelper*.java`,
`utils/network/VkAuthHelper*.java`, `presentation/auth/**`,
`network/response/auth/*Response.java` and `AndroidManifest.xml`.

Backend base URL: `https://api-s.anixsekai.com/`. All social auth endpoints are
`application/x-www-form-urlencoded` **POST**s (Retrofit `@FormUrlEncoded`).
Every response extends `Response { int code }` (`0` = success, `1` = failed,
`402` = banned, `403` = perm-banned) and additionally implements
`CodeInputResponse` (`hash`, `code_length`, `code_time_left`,
`suggested_logins`) — i.e. the server may answer "this social account is not
linked yet, pick a login / confirm by e-mail code".

## 1. Endpoint matrix (`AuthApi.java`)

| Purpose | Method | Path | Exact form fields (in declaration order) |
|---|---|---|---|
| Sign in Telegram | POST | `auth/telegram` | `telegramIdToken` |
| Sign in VK | POST | `auth/vk` | `vkAccessToken` |
| Sign in Google | POST | `auth/google` | `googleIdToken` |
| Sign in Yandex | POST | `auth/yandex` | `yandexAccessToken` |
| Sign up Telegram | POST | `auth/telegram` + `X-Long-Code: 1` | `login`, `email`, `telegramIdToken` |
| Sign up VK | POST | `auth/vk` + `X-Long-Code: 1` | `login`, `email`, `vkAccessToken` |
| Sign up Google | POST | `auth/google` + `X-Long-Code: 1` | `login`, `email`, `googleIdToken` |
| Sign up Yandex | POST | `auth/yandex` + `X-Long-Code: 1` | `login`, `email`, `yandexAccessToken` |
| E-mail code verify | POST | `auth/verify` | `login`, `email`, `password`, `vkAccessToken`, `googleIdToken`, `telegramIdToken`, `yandexAccessToken`, `hash`, `code` |
| Resend code | POST | `auth/resend` + `X-Long-Code: 1` | `login`, `email`, `password`, `vkAccessToken`, `googleIdToken`, `telegramIdToken`, `yandexAccessToken`, `hash` |
| Firebase push link | POST | `auth/firebase?token=` | *(query only, no form body)* |

Notes:

* Sign-in and sign-up share the **same path**; the presence of `login` + `email`
  and the `X-Long-Code: 1` header is what registers a new account.
* `auth/verify` / `auth/resend` carry **all four** social credential fields at
  once, so a pending registration started by any provider resumes through the
  same endpoint. Unused fields are sent as empty strings.
* Binding a social account to an already authenticated profile uses
  `profile/preference/{provider}/bind` with a **different** field name:
  `idToken` for `google`/`telegram`, `accessToken` for `vk`/`yandex`
  (`ProfilePreferenceApi.java`), plus `?token=` (session).

### Response codes

`TelegramResponse`, `VkResponse`, `YandexResponse` share one table;
`GoogleResponse` differs from 8 upward.

| Code | Meaning (constant) | Telegram/VK/Yandex | Google |
|---|---|---|---|
| 0 | success (`profile` + `profileToken` present) | ✅ | ✅ |
| 1 | generic failure (`Response.FAILED`) | ✅ | ✅ |
| 2 | `INVALID_REQUEST` | ✅ | ✅ |
| 3 | `NOT_REGISTERED` → switch to sign-up (login + e-mail) | ✅ | ✅ |
| 4 | `INVALID_LOGIN` | ✅ | ✅ |
| 5 | `INVALID_EMAIL` | ✅ | ✅ |
| 6 | `LOGIN_ALREADY_TAKEN` (+ `suggested_logins`) | ✅ | ✅ |
| 7 | `EMAIL_ALREADY_TAKEN` | ✅ | ✅ |
| 8 | `CODE_ALREADY_SEND` | ✅ | `EMAIL_CHANGED` |
| 9 | `EMAIL_SERVICE_DISALLOWED` | ✅ | `EMAIL_CHANGED_AND_CODE_ALREADY_SEND` |
| 10 | `TOO_MANY_REGISTRATIONS` | ✅ | `EMAIL_SERVICE_DISALLOWED` |
| 11 | — | | `TOO_MANY_REGISTRATIONS` |

JSON keys are camelCase on the wire for these responses: `profileToken`,
`codeTimestampExpires`, `codeTimeLeft`, `codeLength`, `suggestedLogins`
(Jackson getters), except `code_length` / `code_time_left` which are explicitly
renamed with `@JsonProperty`. The Rust model accepts both spellings
(`#[serde(alias)]`). `YandexResponse` also returns `email`.

## 2. Telegram — Login Widget with PKCE (no bot-hash signing)

Constants injected in `AuthActivity.onCreate()`:

| Item | Value |
|---|---|
| `client_id` (Telegram bot / Login-Widget app id) | `8789559054` |
| `redirect_uri` | `https://app1593901251-login.tg.dev/tglogin` |
| `scope` | `openid profile` (`"openid"` is always prepended to the configured list `["profile"]`) |
| `code_challenge_method` | `S256` |
| Authorize | `https://oauth.telegram.org/auth` |
| In-app (crossapp) discover | `https://oauth.telegram.org/crossapp` |
| Token exchange | `https://oauth.telegram.org/token` |

Flow (`TelegramAuthHelper.startLogin` → `handleLoginResponse`):

1. **PKCE material.** 32 bytes from `SecureRandom`, base64 **URL-safe,
   no padding** (`Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING`) →
   `code_verifier`, persisted in `SharedPreferences("telegram_auth")` under
   `"code_verifier"`. `code_challenge` = the same base64url encoding of
   `SHA-256(code_verifier ASCII bytes)`.
2. **Preferred: native Telegram app.** `GET` the *crossapp* URL
   (`Accept: application/json`, 15 s timeouts):
   `https://oauth.telegram.org/crossapp?client_id=…&response_type=code&scope=openid%20profile&redirect_uri=…&android_sdk=1&code_challenge=…&code_challenge_method=S256`
   and read `url` (or `result.url`) from the JSON body. If a URL comes back it
   is opened with `ACTION_VIEW` (the Telegram client answers through the
   `tg://` deep link).
3. **Fallback: in-app WebView.** `https://oauth.telegram.org/auth?client_id=…&response_type=code&scope=openid%20profile&redirect_uri=…&code_challenge=…&code_challenge_method=S256`
   is loaded in `TelegramAuthActivity` (JavaScript + DOM storage on). The
   WebView only leaves the page for `tg://…` (handed to the system) or for the
   configured redirect host (`app1593901251-login.tg.dev`, string resource
   `telegram_redirect_host`); the redirect is replayed into `AuthActivity`
   (`android:autoVerify` intent filter on `https://<host>/tglogin`).
4. **Redirect parsing.** Query params of the redirect URI: `error`
   (refined by `error_description`) → failure; otherwise `code` must be
   present and non-empty, else `"No authorization code in response URI"`.
5. **Code → id token.** `POST https://oauth.telegram.org/token` with
   `Content-Type: application/x-www-form-urlencoded`, `Accept: application/json`
   and body (fields URL-encoded, in this order):
   ```
   grant_type=authorization_code&client_id=<id>&code=<code>&redirect_uri=<uri>&code_verifier=<verifier>
   ```
   HTTP 200 is required; the `id_token` is taken from the JSON key `id_token`,
   falling back to the string key `result`. Missing → `"No id_token in response"`.
6. **Backend.** `POST https://api-s.anixsekai.com/auth/telegram` with form field
   `telegramIdToken=<id_token>`. On `code == 3 (NOT_REGISTERED)` the app opens
   `SignUpWithTelegramFragment` (only the id token is passed, e-mail is optional)
   and re-posts to the same path with `login`, `email` (lower-cased),
   `telegramIdToken` + `X-Long-Code: 1`; a `hash` in the answer means an e-mail
   code has to be confirmed through `auth/verify`.
7. On success the `code_verifier` is removed from preferences and
   `profileToken.token` becomes the session token.

There is **no** `hash`/`data_check` bot HMAC in this client — it uses the OAuth
2.0 PKCE code flow, so the `id_token` (a JWT-like string) is the credential.

## 3. VK — VK ID Mobile SDK (v2.7.2), implicit token handed to the backend

Manifest / SDK configuration (`AndroidManifest.xml` of `com.vk.id.internal.auth.AuthActivity`):

| Item | Value |
|---|---|
| `VKIDClientID` (`client_id` / `app_id`) | `54701760` |
| `VKIDRedirectScheme` | `vk54701760` |
| `VKIDRedirectHost` | `vk.ru` |
| `redirect_uri` (SDK) | `vk54701760://vk.ru/blank.html` |
| scopes | `email` (`Collections.singleton("email")`) |
| authorize host | `https://id.vk.ru/authorize` |
| token exchange | `https://api.vk.ru` (SDK-internal, `deviceauth.*`) |

`VKIDClientSecret` is also declared in the manifest; it is a build-time secret
and is deliberately **not** reproduced here (never commit it).

Flow (`VkAuthHelper`):

1. `VKID.getInstance().authorize(lifecycleOwner, callback,
   VKIDAuthParams.Builder().setScopes("email").build())`. The SDK builds
   `https://id.vk.ru/authorize?client_id=54701760&response_type=code
   &redirect_uri=<redirect>&code_challenge_method=sha256&code_challenge=<pkce>
   &state=<state>&prompt=&stats_info=<base64 json>&sdk_type=vkid&v=2.7.2
   &scope=email&lang_id=<locale>&scheme=<theme>`, plus an `oauth2_params`
   base64 payload on the redirect URI, and prefers the native VK app /
   `vkcexternalauth-codeflow` deep link, falling back to Custom Tabs.
2. PKCE verifier, state and the resulting access/refresh tokens are stored and
   refreshed **inside the SDK** — the app never sees the code.
3. `VKIDAuthCallback.onAuth(AccessToken)` yields `accessToken.getToken()` and
   `accessToken.getUserData().getEmail()`; the app packages
   `mapOf("accessToken" to token, "email" to emailOrNull)`
   (`VkAuthHelper$callback$1`). `onAuthCode` is left to the SDK default;
   `VKIDAuthFail.Canceled` is only logged (no error UI).
4. `AuthPresenter` posts `signInWithVk(hashMap["accessToken"].toString())` →
   `POST /auth/vk`, form field `vkAccessToken`. A `NOT_REGISTERED` code routes to
   `SignUpWithVkFragment`, which sends the whole map as `VK_AUTH_DATA` and
   re-posts with `login`, `email`, `vkAccessToken` + `X-Long-Code: 1`.

So for a non-Android client: obtain a VK ID **access token** with scope `email`
(any VK ID OAuth client works) and POST it as `vkAccessToken`.

## 4. Google — GoogleSignIn + Firebase Authentication

| Item | Value |
|---|---|
| OAuth web client id | Android string resource `default_web_client_id` (generated by `google-services.json`; **not present** in the decompiled sources — it is a per-build secret-free config value) |
| Client id source | `AuthActivity.onCreate`: `GoogleSignInOptions.Builder(DEFAULT_SIGN_IN).requestIdToken(getString(R.string.default_web_client_id))` + `requestEmail()` |
| Request code | `9001` |
| Token revocation | `https://accounts.google.com/o/oauth2/revoke?token=<token>` |
| Backend field | `googleIdToken` |

Flow:

1. `GoogleSignInClient.signInIntent` → `startActivityForResult(intent, 9001)`.
2. `onActivityResult` → `GoogleSignIn.getSignedInAccountFromIntent(intent)
   .getResult(ApiException.class)`; `account.idToken` and `account.email` are
   read (`GoogleSignInAccount.IllI111l11` = email, `…lIIlI11I1I` = idToken).
3. `FirebaseAuth.signInWithCredential(GoogleAuthCredential(idToken, null))`
   (the account is **first** exchanged in Firebase), then
   `firebaseUser.getToken(true)` → `GetTokenResult.getToken()`.
4. `signInWithGoogle(getTokenResult.token)` → `POST /auth/google`, form field
   `googleIdToken` = **the Firebase ID token**, not the raw Google ID token.
   The e-mail from Firebase is lower-cased and used for the sign-up variant:
   `login`, `email`, `googleIdToken` + `X-Long-Code: 1`
   (`SignUpWithGoogleFragment` receives `EMAIL_VALUE` + `GOOGLE_ID_TOKEN_VALUE`).
5. Failure at any step → `onGoogleLoginFailed()` (toast), sign-out of the
   Firebase user is attempted when the user already exists.

Implication for this crate: `sign_in_google` expects a **Firebase ID token**
string. Producing it requires a Google OAuth client id plus Firebase project
config that are not part of the APK sources, so the client only forwards the
token; the helper `oauth::google_id_token_hint()` documents where to obtain it.

## 5. Yandex — Yandex ID OAuth SDK (implicit flow)

| Item | Value |
|---|---|
| `com.yandex.auth.CLIENT_ID` (`client_id`) | `a980f00536554180b4826f7daa76f060` |
| `com.yandex.auth.OAUTH_HOST` | `oauth.yandex.ru` |
| redirect URI | `https://yx<a980f00536554180b4826f7daa76f060>.oauth.yandex.ru/auth/finish?platform=android` |
| custom scheme | `yxa980f00536554180b4826f7daa76f060://auth/finish` |
| authorize URL template | `https://%s/authorize?response_type=token&client_id=%s&redirect_uri=%s&state=%s&force_confirm=true&origin=%s` |
| `origin` | `android` |

Flow (`YandexAuthSdkContract` + `ExternalLoginHandler`):

1. `AuthActivity` registers `YandexAuthSdkContract(YandexAuthOptions(context))`
   (`YandexAuthLoginOptions` with default scopes) and launches it; SDK failures
   to initialise are caught and logged as `YandexAuth`.
2. The SDK stores a random `state` in preferences (`state_value`) and opens
   `https://<host>/authorize?response_type=token&client_id=a980f00536554180b4826f7daa76f060
   &redirect_uri=<url-encoded redirect>&state=<state>&force_confirm=true&origin=android`
   in Custom Tabs / WebView, or hands off to a Yandex app via
   `com.yandex.auth.action.YA_SDK_LOGIN`. `<host>` is `oauth.yandex.ru` with the
   trailing `ru` replaced by a region suffix derived from the device language
   (`ru`, `com`, `kz` for kk, `by` for be, `com.tr` for tr, default `com`).
3. The redirect is received by `ChromeTabDataActivity`
   (`autoVerify` on `yx<client_id>.oauth.yandex.ru`, path `/auth/finish`); the
   SDK parses the **URL fragment** (`dummy://dummy?<fragment>`) and validates
   `state`, yielding `YandexAuthResult.Success(accessToken)`.
4. `signInWithYandex(success.accessToken)` → `POST /auth/yandex`, form field
   `yandexAccessToken` (an OAuth **access token**, not an id token).
   `NOT_REGISTERED` → `SignUpWithYandexFragment` (`EMAIL` + `YANDEX_ACCESS_TOKEN`)
   → same path with `login`, `email` (lower-cased), `yandexAccessToken`
   + `X-Long-Code: 1`.
5. `YandexAuthResult.Failure` → log + `onYandexLoginFailed()`; `Cancelled` is
   silent.

## 6. Mapping to `anixart-core`

`anixart/core/src/oauth.rs` + `AnixartClient`:

| Rust | Mirrors |
|---|---|
| `oauth::TELEGRAM_*`, `vk_*`, `yandex_*`, constants | the tables above |
| `oauth::generate_pkce` / `code_challenge` / `code_verifier_from_bytes` | step 1 of the Telegram flow |
| `oauth::telegram_authorize_url`, `telegram_crossapp_url` | steps 2–3 |
| `oauth::parse_telegram_redirect` | step 4 |
| `oauth::telegram_token_body`, `extract_telegram_id_token`, `extract_telegram_inapp_url` | step 5 |
| `oauth::vk_authorize_url`, `YANDEX_*` + `yandex_authorize_url` | VK / Yandex authorize URLs (for manual/browser login) |
| `oauth::sign_in_form` / `sign_up_form` / `SocialProvider` | the exact `@Field` names and order |
| `AnixartClient::sign_in_telegram` / `sign_in_vk` / `sign_in_google` / `sign_in_yandex` | `POST /auth/{provider}`, token auto-stored on `code == 0` |
| `AnixartClient::sign_up_*` | the `X-Long-Code: 1` registration variants |
| `AnixartClient::exchange_telegram_code` | `POST https://oauth.telegram.org/token` |
| `AnixartClient::fetch_telegram_inapp_url` | `GET https://oauth.telegram.org/crossapp` |
| `SocialSignInResponse` / `oauth::codes` | `*Response` + `CodeInputResponse` |

Everything that touches a third-party host (Telegram token exchange, PKCE,
URL building) is provider-agnostic and testable offline; the VK ID and Yandex ID
SDKs remain platform-specific, so their tokens are supplied by the caller.
