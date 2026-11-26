# Anixart Linux client (`anixart/app`)

iced 0.13 desktop front end for `anixart-core` (REST) and `anixart-parsers`
(video hosts). Playback is handed to an external `mpv` process.

```
src/main.rs     binary entry point (anixart)
src/lib.rs      iced::application wiring + unit tests
src/app.rs      state, Message routing, navigation, browser / mpv hand-off
src/login.rs    login screen (five methods) + OAuth credential parsing
src/session.rs  persistent session token
src/net.rs      async glue to anixart-core / anixart-parsers
src/feed.rs src/search.rs src/release.rs   authenticated screens
```

## Login methods

All five produce an Anixart `profileToken.token`; the credential differs
(details in `../docs/oauth.md`):

| Tab | Credential | How it is obtained here |
|---|---|---|
| Password | `login` + `password` | `POST /auth/signIn` |
| Telegram | `telegramIdToken` | app generates the PKCE pair, `xdg-open` opens `https://oauth.telegram.org/auth`; paste back the `…/tglogin?code=…` URL (core exchanges the code for an `id_token`) or an `id_token` directly |
| VK | `vkAccessToken` | `xdg-open` opens the VK ID authorize URL; paste back the redirect URL carrying `access_token=…`, or a VK ID access token |
| Google | `googleIdToken` (**Firebase** ID token) | paste the token, or give a Google OAuth client id to open the browser sign-in first |
| Token | — | paste an existing Anixart session token: instant login, no request |

There is no local HTTP callback listener on purpose: the registered redirect
hosts (`app1593901251-login.tg.dev`, `vk54701760://vk.ru/blank.html`) are bound
to the Android package, so the provider rejects a `http://localhost` redirect.
Copying the URL the browser lands on is the provider-approved equivalent, and a
pasted token works everywhere.

## Session persistence

On success the token is written to `~/.config/anixart-linux/session.json`
(`$ANIXART_CONFIG_DIR` overrides the directory, then `$XDG_CONFIG_HOME`), with
mode `600` on Unix:

```json
{ "version": 1, "token": "…", "method": "telegram", "saved_at": 1764200000 }
```

`App::startup` restores it and opens the feed straight away; **Logout** deletes
the file. Third-party tokens, passwords and PKCE verifiers are never persisted.

## Build

Compiled only in GitHub Actions (`anixart-rust.yml`):
`cargo clippy --workspace --all-targets -- -D warnings`, `cargo test`,
`cargo build --release`. Needs `libxkbcommon-dev`, `libwayland-dev`, `libx11-dev`,
`pkg-config` at build time and `xdg-utils` + `mpv` at run time.
