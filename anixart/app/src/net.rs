//! Async glue between the iced runtime and `anixart-core` / `anixart-parsers`.
//!
//! Every helper builds its client *inside* the spawned future: iced is powered by
//! the `tokio` executor, so all reqwest traffic then runs on a real tokio runtime.
//!
//! The 1-click Google sign-in additionally owns a short-lived loopback HTTP
//! listener ([`google_callback::Listener`]) that the browser hands its
//! implicit-flow redirect back to; the captured credential is exchanged for a
//! **Firebase** ID token through `anixart_core::oauth` before `POST /auth/google`.
//!
//! [`read_clipboard`] is the other half of the login screen: iced 0.13 cannot read
//! the clipboard on a GNOME / KDE Wayland session (winit 0.30 only speaks
//! `zwlr-data-control`), so the “Paste & Sign In” button falls back to shelling out
//! to `wl-paste` / `xclip` / `xsel`.

use std::process::{Command, Stdio};
use std::time::Duration;

use anixart_core::{
    oauth, AnixartClient, Collection, Episode, EpisodeType, FilterRequest, Pageable,
    ProfileInfoResponse, Release, ReleaseSearchResponse, SocialSignInResponse, Source,
};
use anixart_parsers::ParsedVideo;

use crate::lists::{BookmarkTab, DiscoverKind, WatchStatus};
use crate::login::{google_callback_error, google_credential_from, random_verifier};
use crate::profile::ProfileSnapshot;

/// Builds an API client, attaching the session token when available.
fn api_client(token: Option<String>) -> Result<AnixartClient, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    if let Some(token) = token {
        client.set_token(token);
    }
    Ok(client)
}

/// `POST /auth/signin` — resolves to the session token.
pub async fn login(username: String, password: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    client
        .login(&username, &password)
        .await
        .map_err(|e| e.to_string())?;

    client
        .token()
        .map(str::to_owned)
        .ok_or_else(|| "Нет токена в ответе входа".to_owned())
}

/// Turns a social `auth/*` answer into either the session token or a message
/// that explains what the caller has to do next (see `docs/oauth.md` §1).
fn token_of(response: &SocialSignInResponse) -> Result<String, String> {
    if let Some(token) = response.token().filter(|t| !t.is_empty()) {
        return Ok(token.to_owned());
    }
    Err(describe_code(response.code))
}

/// Human-readable form of the `auth/{provider}` response codes.
fn describe_code(code: i32) -> String {
    match code {
        0 => "Нет токена в ответе входа".to_owned(),
        1 => "Anixart отклонил учётные данные (код 1)".to_owned(),
        2 => "Некорректный OAuth-запрос (код 2)".to_owned(),
        3 => "Этот социальный аккаунт ещё не привязан к профилю Anixart — \
              зарегистрируйте его в официальном клиенте с логином и почтой, \
              затем войдите здесь (код 3)"
            .to_owned(),
        4 => "Неверный логин (код 4)".to_owned(),
        5 => "Неверная почта (код 5)".to_owned(),
        6 => "Логин уже занят (код 6)".to_owned(),
        7 => "Почта уже занята (код 7)".to_owned(),
        402 => "Профиль заблокирован (код 402)".to_owned(),
        403 => "Профиль заблокирован навсегда (код 403)".to_owned(),
        other => format!("Anixart вернул код {other}"),
    }
}

/// `POST /auth/telegram` with a ready-made Login-Widget `id_token`.
pub async fn sign_in_telegram_id_token(id_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_telegram(&id_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// Telegram redirect → `code` → `POST https://oauth.telegram.org/token` →
/// `POST /auth/telegram`. `code_verifier` must belong to the challenge that was
/// sent to the browser.
pub async fn sign_in_telegram_redirect(
    redirect_url: String,
    code_verifier: String,
) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_telegram_redirect(&redirect_url, &code_verifier)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `POST /auth/vk` with a VK ID access token (scope `email`).
pub async fn sign_in_vk(access_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_vk(&access_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `POST /auth/yandex` with a Yandex ID OAuth access token (`ya29`-like opaque
/// string from the implicit flow's URL fragment).
pub async fn sign_in_yandex(access_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_yandex(&access_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// `POST /auth/google` with a **Firebase** ID token.
pub async fn sign_in_google(id_token: String) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let response = client
        .sign_in_google(&id_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// Google credential → Firebase ID token (`accounts:signInWithIdp`) →
/// `POST /auth/google`.
///
/// This is the desktop stand-in for the Android client's
/// `FirebaseAuth.signInWithCredential(GoogleAuthProvider …)`, which the backend
/// implicitly performs before it will accept `googleIdToken`.
pub async fn sign_in_google_via_firebase(
    google_token: String,
    is_access_token: bool,
    firebase_api_key: String,
) -> Result<String, String> {
    let mut client = AnixartClient::new().map_err(|e| e.to_string())?;
    let endpoint = oauth::firebase_sign_in_with_idp_url_at(
        oauth::FIREBASE_IDENTITY_TOOLKIT_BASE,
        &firebase_api_key,
    );
    let response = client
        .sign_in_google_via_firebase_at(&endpoint, &google_token, is_access_token)
        .await
        .map_err(|e| e.to_string())?;
    token_of(&response)
}

/// Manual paste fallback: works with *any* Google-shaped credential.
///
/// The input may be the redirect URL the browser landed on (query or fragment), a
/// bare `#id_token=…` fragment, a raw Google `id_token` JWT, a `ya29.…` OAuth
/// access token, or an already-exchanged Firebase ID token.
///
/// * An opaque access token has to go through Firebase, so it is exchanged first.
/// * A JWT could already be a Firebase ID token — that is what `auth/google`
///   wants — so it is posted as-is and only exchanged through Firebase when
///   Anixart refuses it. The original backend error is reported if both attempts
///   fail, because it is the more actionable one.
pub async fn sign_in_google_auto(
    pasted: String,
    firebase_api_key: String,
) -> Result<String, String> {
    let (token, is_access_token) = google_credential_from(&pasted)
        .ok_or_else(|| "В этом вводе нет Google-креденциала".to_owned())?;

    if is_access_token {
        return sign_in_google_via_firebase(token, true, firebase_api_key).await;
    }

    match sign_in_google(token.clone()).await {
        Ok(session) => Ok(session),
        Err(direct_error) => {
            match sign_in_google_via_firebase(token, false, firebase_api_key).await {
                Ok(session) => Ok(session),
                Err(_) => Err(direct_error),
            }
        }
    }
}

/// 1-click Google sign-in: loopback callback listener → browser → Firebase
/// exchange → `POST /auth/google`.
///
/// Binds an ephemeral port on `127.0.0.1`, opens the Google implicit-flow
/// authorize URL with that origin as `redirect_uri` (a web client id accepts the
/// loopback host at any port), serves a tiny relay page that ships the URL
/// fragment back to `POST /callback`, and signs the captured credential in
/// through Firebase. No token hunting and no manual paste required.
pub async fn sign_in_google_browser(
    google_client_id: String,
    firebase_api_key: String,
) -> Result<String, String> {
    let listener = google_callback::Listener::bind().map_err(|e| e.to_string())?;
    let port = listener.port();
    let nonce = random_verifier();
    let url = oauth::google_browser_authorize_url(&google_client_id, port, &nonce);

    // Handing the URL to the desktop has to succeed before the listener is given
    // away; the error already quotes the URL so it can be opened by hand.
    crate::app::open_url(&url)?;

    let waited = tokio::time::timeout(CALLBACK_TIMEOUT, google_callback::capture(listener)).await;
    let payload = match waited {
        Ok(captured) => captured?,
        Err(_) => {
            return Err(format!(
                "Ожидание Google-редиректа длилось больше {} секунд. Нажмите кнопку ещё раз \
                 либо вставьте URL, на который перешёл браузер, в поле ниже.",
                CALLBACK_TIMEOUT.as_secs()
            ))
        }
    };

    if let Some(reason) = google_callback_error(&payload) {
        return Err(format!("Google не разрешил вход: {reason}"));
    }
    let (token, is_access_token) = google_credential_from(&payload).ok_or_else(|| {
        "В Google-редиректе нет id_token / access_token — вставьте URL, на который перешёл \
         браузер, в поле ниже."
            .to_owned()
    })?;

    sign_in_google_via_firebase(token, is_access_token, firebase_api_key).await
}

/// How long the browser is given to complete the Google sign-in.
const CALLBACK_TIMEOUT: Duration = Duration::from_secs(180);

/// Loopback OAuth callback listener for the 1-click Google flow.
///
/// Google puts the implicit-flow credentials in the redirect **fragment**, which
/// browsers never send to a server, so the listener answers the redirect with a
/// relay page that reads `window.location.hash` and POSTs it back to `/callback`.
pub mod google_callback {
    use std::io;
    use std::net::SocketAddr;

    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::{TcpListener, TcpStream};

    /// Bound loopback listener on `127.0.0.1:0` (an OS-assigned free port).
    pub struct Listener {
        inner: TcpListener,
        addr: SocketAddr,
    }

    impl Listener {
        /// Reserves a free port on the loopback interface only.
        pub fn bind() -> io::Result<Self> {
            let std_listener = std::net::TcpListener::bind("127.0.0.1:0")?;
            std_listener.set_nonblocking(true)?;
            let addr = std_listener.local_addr()?;
            let inner = TcpListener::from_std(std_listener)?;
            Ok(Self { inner, addr })
        }

        /// The port the authorize URL's `redirect_uri` points at.
        pub fn port(&self) -> u16 {
            self.addr.port()
        }
    }

    /// Waits for the browser to hand the redirect payload back and returns it.
    ///
    /// Every `GET` is answered with [`RELAY_PAGE`] (the browser may probe the
    /// origin several times, and a reload must keep working); the first
    /// `POST /callback` carries the fragment and ends the wait.
    pub async fn capture(listener: Listener) -> Result<String, String> {
        loop {
            let (stream, _) = listener
                .inner
                .accept()
                .await
                .map_err(|e| format!("Слушатель Google-редиректа: {e}"))?;
            // `Ok(None)` = the relay page was served, `Err(_)` = the client hung
            // up mid-request. Neither ends the wait: browsers routinely open more
            // than one connection (favicons, reloads, redirects) to this origin.
            if let Ok(Some(payload)) = handle(stream).await {
                return Ok(payload);
            }
        }
    }

    /// Reads one request. `Ok(Some(body))` for `POST /callback`, `Ok(None)` for
    /// anything else (which is answered with the relay page).
    async fn handle(mut stream: TcpStream) -> io::Result<Option<String>> {
        let Request { method, path, body } = read_request(&mut stream).await?;

        if method == "POST" && path.starts_with("/callback") {
            respond(&mut stream, "200 OK", "text/html; charset=utf-8", DONE_PAGE).await?;
            return Ok(Some(body));
        }

        // GET (and anything else): serve the relay page, which reads the fragment
        // the browser withheld and posts it back.
        respond(&mut stream, "200 OK", "text/html; charset=utf-8", RELAY_PAGE).await?;
        Ok(None)
    }

    /// Parsed request line plus the body bytes that came with it.
    struct Request {
        method: String,
        path: String,
        body: String,
    }

    /// Reads the head, then exactly the `Content-Length` bytes the client promised.
    ///
    /// The head and the body frequently arrive in one TCP segment, so whatever
    /// `read_head` consumed past the separator is reused instead of being waited
    /// for on the socket.
    async fn read_request(stream: &mut TcpStream) -> io::Result<Request> {
        let raw = read_head(stream).await?;
        let separator = raw
            .windows(4)
            .position(|w| w == b"\r\n\r\n")
            .unwrap_or(raw.len());
        let head = String::from_utf8_lossy(&raw[..separator]).into_owned();
        // body bytes the client already pipelined past the separator
        let mut body = match separator.checked_add(4) {
            Some(start) if start <= raw.len() => raw[start..].to_vec(),
            _ => Vec::new(),
        };

        let Some((method, path, content_length)) = parse_head(&head) else {
            return Ok(Request { method: String::new(), path: String::new(), body: String::new() });
        };

        const MAX_BODY: usize = 32 * 1024;
        let wanted = content_length.min(MAX_BODY);
        while body.len() < wanted {
            let mut buf = vec![0u8; wanted - body.len()];
            match stream.read(&mut buf).await {
                Ok(0) => break,
                Ok(n) => body.extend_from_slice(&buf[..n]),
                Err(e) if e.kind() == io::ErrorKind::Interrupted => {}
                Err(e) => return Err(e),
            }
        }
        body.truncate(wanted);

        Ok(Request {
            method,
            path,
            body: String::from_utf8_lossy(&body).into_owned(),
        })
    }

    /// Reads up to and including the blank line that ends the header block, plus
    /// any body bytes the client pipelined into the same segment.
    async fn read_head(stream: &mut TcpStream) -> io::Result<Vec<u8>> {
        const MAX_HEAD: usize = 64 * 1024;
        let mut raw: Vec<u8> = Vec::with_capacity(1024);
        let mut buf = [0u8; 1024];
        loop {
            match stream.read(&mut buf).await {
                Ok(0) => break,
                Ok(n) => {
                    raw.extend_from_slice(&buf[..n]);
                    if raw.windows(4).any(|w| w == b"\r\n\r\n") {
                        break;
                    }
                }
                Err(e) if e.kind() == io::ErrorKind::Interrupted => {}
                Err(e) => return Err(e),
            }
            if raw.len() > MAX_HEAD {
                break; // confused or hostile client: stop growing the buffer
            }
        }
        Ok(raw)
    }

    /// `method`, `path`, `Content-Length`.
    fn parse_head(head: &str) -> Option<(String, String, usize)> {
        let mut lines = head.lines();
        let mut parts = lines.next()?.split_whitespace();
        let method = parts.next()?.to_ascii_uppercase();
        let path = parts.next()?.to_owned();

        let mut content_length = 0usize;
        for line in lines {
            let line = line.trim();
            if line.is_empty() {
                break;
            }
            // header names are case-insensitive; lines without a colon are ignored
            let (name, value) = match line.split_once(':') {
                Some(pair) => pair,
                None => continue,
            };
            if name.eq_ignore_ascii_case("content-length") {
                content_length = value.trim().parse().unwrap_or(0);
            }
        }
        Some((method, path, content_length))
    }

    async fn respond(
        stream: &mut TcpStream,
        status: &str,
        content_type: &str,
        body: &str,
    ) -> io::Result<()> {
        let head = format!(
            "HTTP/1.1 {status}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\n\
             Cache-Control: no-store\r\nConnection: close\r\n\r\n",
            body.len()
        );
        stream.write_all(head.as_bytes()).await?;
        stream.write_all(body.as_bytes()).await?;
        stream.flush().await
    }

    /// Served on the redirect: extracts the URL fragment (or query) and posts it
    /// back to `/callback`, so the desktop client receives what the browser hid.
    ///
    /// The page is Russian because the user reading it is Russian: it is the only
    /// screen of the login flow that lives outside the iced window.
    pub const RELAY_PAGE: &str = r#"<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Anixart — вход через Google</title>
<style>
 body{font:15px/1.5 system-ui,sans-serif;background:#141414;color:#f2f2f3;margin:0;
      display:flex;min-height:100vh;align-items:center;justify-content:center}
 main{max-width:34rem;padding:2rem;text-align:center}
 code{background:#2a2a2a;border-radius:.35rem;padding:.1rem .35rem}
 #hint{color:#9a9aa2;font-size:.9rem;word-break:break-all}
</style></head>
<body><main>
 <h2 id="title">Возвращаемся в Anixart…</h2>
 <p id="hint">Страница лишь передаёт редирект входа десктопному клиенту, который
 слушает <code>127.0.0.1</code>. После этого её можно закрыть.</p>
 <script>
 (function () {
   var hash = location.hash ? location.hash.slice(1) : "";
   var query = location.search ? location.search.slice(1) : "";
   var payload = hash || query;
   if (!payload) {
     document.getElementById("title").textContent = "В этом редиректе нет креденциала";
     return;
   }
   fetch("/callback", {
     method: "POST",
     headers: { "Content-Type": "text/plain;charset=utf-8" },
     body: payload,
     keepalive: true
   }).then(function () {
     document.getElementById("title").textContent = "Вход выполнен — вкладку можно закрыть";
   }).catch(function () {
     document.getElementById("hint").textContent =
       "Клиент не отвечает. Скопируйте это в Anixart вручную: " + location.href;
   });
 })();
 </script>
</main></body></html>"#;

    /// Acknowledgement of the captured fragment.
    pub const DONE_PAGE: &str = "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">\
        <title>Anixart — вход через Google</title></head><body style=\"font:15px system-ui,sans-serif;\
        background:#141414;color:#f2f2f3;margin:2rem\">Вход выполнен. Эту вкладку можно закрыть.\
        </body></html>";

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn parse_head_reads_method_path_and_content_length() {
            let head = "POST /callback HTTP/1.1\r\nHost: 127.0.0.1:43211\r\n\
                        Content-Length: 57\r\nContent-Type: text/plain;charset=utf-8\r\n\r\n";
            let (method, path, len) = parse_head(head).expect("parsed");
            assert_eq!(method, "POST");
            assert_eq!(path, "/callback");
            assert_eq!(len, 57);
        }

        #[test]
        fn parse_head_handles_a_case_insensitive_header() {
            let head = "post /callback?x=1 HTTP/1.1\r\nCONTENT-LENGTH: 12\r\n\r\n";
            let (method, path, len) = parse_head(head).expect("parsed");
            assert_eq!(method, "POST");
            assert_eq!(path, "/callback?x=1");
            assert_eq!(len, 12);
        }

        #[test]
        fn parse_head_defaults_to_a_bodyless_get() {
            let (method, path, len) = parse_head("GET / HTTP/1.1\r\nHost: x\r\n\r\n").expect("parsed");
            assert_eq!(method, "GET");
            assert_eq!(path, "/");
            assert_eq!(len, 0);
        }

        #[test]
        fn parse_head_rejects_garbage() {
            assert!(parse_head("").is_none());
            assert!(parse_head("GET\r\n\r\n").is_none());
        }

        #[tokio::test]
        async fn listener_binds_a_free_loopback_port() {
            let listener = Listener::bind().expect("bind");
            assert_ne!(listener.port(), 0);
            assert!(listener.addr.ip().is_loopback());
            // the address the browser will be told to redirect back to
            assert_eq!(
                listener.addr,
                std::net::SocketAddr::from(([127, 0, 0, 1], listener.port()))
            );
        }

        #[tokio::test]
        async fn two_listeners_get_different_ports() {
            let a = Listener::bind().expect("bind");
            let b = Listener::bind().expect("bind");
            assert_ne!(a.port(), b.port());
        }

        /// Drives the listener the way a browser does: a `GET` with the fragment
        /// stripped, then the relay page's `POST /callback`.
        #[tokio::test]
        async fn capture_returns_the_posted_fragment() {
            let listener = Listener::bind().expect("bind");
            let port = listener.port();
            let payload = "id_token=GOOG.JWT.tok&access_token=ya29.abc&expires_in=3600&scope=openid";

            let driver = tokio::spawn(async move {
                let mut stream = TcpStream::connect(std::net::SocketAddr::from(([127, 0, 0, 1], port)))
                    .await
                    .expect("connect");
                let get = format!(
                    "GET / HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
                );
                stream.write_all(get.as_bytes()).await.expect("write get");
                let mut page = String::new();
                let _ = stream.read_to_string(&mut page).await;
                assert!(page.contains("window.location.hash") || page.contains("location.hash"),
                    "relay page must read the fragment; got: {page}");

                let mut stream = TcpStream::connect(std::net::SocketAddr::from(([127, 0, 0, 1], port)))
                    .await
                    .expect("reconnect");
                let post = format!(
                    "POST /callback HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n\
                     Content-Type: text/plain;charset=utf-8\r\nContent-Length: {}\r\n\
                     Connection: close\r\n\r\n{payload}",
                    payload.len()
                );
                stream.write_all(post.as_bytes()).await.expect("write post");
                let mut ack = String::new();
                let _ = stream.read_to_string(&mut ack).await;
                assert!(ack.starts_with("HTTP/1.1 200"), "ack: {ack}");
            });

            let captured = capture(listener).await.expect("capture");
            driver.await.expect("driver");
            assert_eq!(captured, payload);
        }

        #[tokio::test]
        async fn capture_survives_a_malformed_request() {
            let listener = Listener::bind().expect("bind");
            let port = listener.port();
            let payload = "access_token=ya29.aftergarbage";

            let driver = tokio::spawn(async move {
                // Junk first: the listener must keep waiting instead of failing.
                let mut junk = TcpStream::connect(std::net::SocketAddr::from(([127, 0, 0, 1], port)))
                    .await
                    .expect("connect junk");
                let _ = junk.write_all(b"NOT-HTTP\r\n\r\n").await;
                let mut junk_reply = String::new();
                let _ = junk.read_to_string(&mut junk_reply).await;
                drop(junk);

                let mut stream = TcpStream::connect(std::net::SocketAddr::from(([127, 0, 0, 1], port)))
                    .await
                    .expect("connect post");
                let post = format!(
                    "POST /callback HTTP/1.1\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{payload}",
                    payload.len()
                );
                let _ = stream.write_all(post.as_bytes()).await;
                let mut ack = String::new();
                let _ = stream.read_to_string(&mut ack).await;
            });

            assert_eq!(capture(listener).await.expect("capture"), payload);
            driver.await.expect("driver");
        }

        #[test]
        fn relay_page_posts_to_the_callback_path() {
            assert!(RELAY_PAGE.contains("fetch(\"/callback\""));
            assert!(RELAY_PAGE.contains("location.hash"));
            assert!(RELAY_PAGE.contains("location.search"));
            assert!(DONE_PAGE.contains("Вход выполнен"));
        }
    }
}

/// How long the external clipboard readers are given between them.
const CLIPBOARD_TIMEOUT: Duration = Duration::from_secs(5);

/// One external clipboard reader: the executable plus its fixed argument list.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ClipboardReader {
    /// Executable name, looked up in `PATH`.
    pub program: &'static str,
    /// Arguments that make it print the selection and exit.
    pub args: &'static [&'static str],
}

impl ClipboardReader {
    /// Runs the reader. `Ok(None)` when it produced nothing usable, `Err` with the
    /// reason it could not run at all.
    fn run(&self) -> Result<Option<String>, String> {
        let output = Command::new(self.program)
            .args(self.args)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .output()
            .map_err(|e| e.to_string())?;

        if !output.status.success() {
            return Err(format!("не сработал ({})", output.status));
        }
        Ok(clipboard_text(&output.stdout))
    }
}

/// Clipboard readers tried in order once `iced::clipboard::read()` comes back
/// empty — the normal outcome on a GNOME / KDE Wayland session, where winit 0.30
/// finds no `zwlr_data_control_manager_v1` and has no way to ask for the selection.
///
/// Only text targets are requested, so an image on the clipboard can never be
/// served back as bytes; the primary selection is tried last, because a highlight
/// copied there is what the user most likely meant.
pub const CLIPBOARD_READERS: &[ClipboardReader] = &[
    ClipboardReader {
        program: "wl-paste",
        args: &["--no-newline", "--type", "text"],
    },
    ClipboardReader {
        program: "xclip",
        args: &["-selection", "clipboard", "-out"],
    },
    ClipboardReader {
        program: "xsel",
        args: &["--clipboard", "--output"],
    },
    ClipboardReader {
        program: "wl-paste",
        args: &["--no-newline", "--type", "text", "--primary"],
    },
    ClipboardReader {
        program: "xclip",
        args: &["-selection", "primary", "-out"],
    },
    ClipboardReader {
        program: "xsel",
        args: &["--primary", "--output"],
    },
];

/// Usable text out of a reader's stdout: UTF-8, trimmed, and rejected when it is
/// empty or looks like an image dump.
pub fn clipboard_text(stdout: &[u8]) -> Option<String> {
    if stdout.contains(&0) || stdout.starts_with(b"\x89PNG") {
        return None;
    }
    let text = String::from_utf8_lossy(stdout).trim().to_owned();
    (!text.is_empty()).then_some(text)
}

/// Reads the clipboard through [`CLIPBOARD_READERS`], first usable answer wins.
///
/// The readers are blocking, so they run on a tokio blocking thread and are bounded
/// by [`CLIPBOARD_TIMEOUT`] — a clipboard tool with no session behind it must never
/// pin the login screen.
pub async fn read_clipboard() -> Result<String, String> {
    let waited = tokio::time::timeout(
        CLIPBOARD_TIMEOUT,
        tokio::task::spawn_blocking(read_clipboard_blocking),
    )
    .await;

    match waited {
        Err(_) => {
            let secs = CLIPBOARD_TIMEOUT.as_secs();
            Err(format!(
                "буфер обмена (wl-paste / xclip / xsel) отвечал дольше {secs} с. \
                 Установите wl-clipboard или xclip и повторите, либо введите значение \
                 в поле вручную"
            ))
        }
        Ok(Err(crashed)) => Err(format!("читалка буфера обмена упала: {crashed}")),
        Ok(Ok(result)) => result,
    }
}

/// Tries every reader in turn and reports all of them when none worked.
fn read_clipboard_blocking() -> Result<String, String> {
    let mut failures = Vec::new();
    let mut saw_text_target = false;

    for reader in CLIPBOARD_READERS {
        let program = reader.program;
        match reader.run() {
            Ok(Some(text)) => return Ok(text),
            Ok(None) => {
                saw_text_target = true;
                failures.push(format!("{program}: нет текста"));
            }
            Err(reason) => failures.push(format!("{program}: {reason}")),
        }
    }

    Err(if saw_text_target {
        format!(
            "в буфере обмена нет читаемого текста ({}). Скопируйте URL или токен в \
             браузере и снова нажмите «Вставить и войти»",
            failures.join(", ")
        )
    } else {
        format!(
            "ни одна программа для чтения буфера не запустилась ({}). Установите \
             wl-clipboard или xclip, либо введите значение в поле вручную",
            failures.join(", ")
        )
    })
}

/// `POST /search/releases/{page}` — release search.
pub async fn search(
    token: Option<String>,
    query: String,
    page: u32,
) -> Result<ReleaseSearchResponse, String> {
    let client = api_client(token)?;
    client.search(&query, page).await.map_err(|e| e.to_string())
}

/// `GET /release/{id}` — extended release page.
///
/// The release body is several hundred bytes wide, so it travels behind a box to
/// keep every [`Message`](crate::app::Message) variant the same size.
pub async fn release(token: Option<String>, id: i64) -> Result<Box<Release>, String> {
    let client = api_client(token)?;
    let response = client.release(id, true).await.map_err(|e| e.to_string())?;
    response
        .release
        .map(Box::new)
        .ok_or_else(|| format!("Релиз {id} не найден"))
}

/// `GET /collection/{id}` — the opened collection with its releases.
///
/// A collection body carries every release it holds, so it is boxed for the same
/// reason [`release`] is: one small [`Message`](crate::app::Message) variant.
pub async fn collection(token: Option<String>, id: i64) -> Result<Box<Collection>, String> {
    let client = api_client(token)?;
    let response = client.collection(id).await.map_err(|e| e.to_string())?;
    response
        .collection
        .map(Box::new)
        .ok_or_else(|| format!("Коллекция {id} не найдена"))
}

/// `GET /episode/{id}` — voiceovers / translations.
pub async fn types(token: Option<String>, release_id: i64) -> Result<Vec<EpisodeType>, String> {
    let client = api_client(token)?;
    client.types(release_id).await.map(|r| r.types).map_err(|e| e.to_string())
}

/// `GET /episode/{releaseId}/{typeId}` — video sources of a voiceover.
pub async fn sources(
    token: Option<String>,
    release_id: i64,
    type_id: i64,
) -> Result<Vec<Source>, String> {
    let client = api_client(token)?;
    client
        .sources(release_id, type_id)
        .await
        .map(|r| r.sources)
        .map_err(|e| e.to_string())
}

/// `GET /episode/{releaseId}/{typeId}/{sourceId}` — episodes of a source.
pub async fn episodes(
    token: Option<String>,
    release_id: i64,
    type_id: i64,
    source_id: i64,
) -> Result<Vec<Episode>, String> {
    let client = api_client(token)?;
    client
        .episodes(release_id, type_id, source_id, 0)
        .await
        .map(|r| r.episodes)
        .map_err(|e| e.to_string())
}

/// Poster, cover or avatar bytes from the Anixart CDN.
///
/// The artwork host is a plain static file server, so no session token is sent:
/// the request rides the same [`reqwest::Client`] the API calls use, which carries
/// the shipping client's `User-Agent` (the CDN answers 403 to a bare one).
pub async fn poster_bytes(url: String) -> Result<Vec<u8>, String> {
    let client = api_client(None)?;
    let response = client
        .http()
        .get(url)
        .send()
        .await
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?;
    let bytes = response.bytes().await.map_err(|e| e.to_string())?;
    if bytes.is_empty() {
        return Err("Пустой ответ обложки".to_owned());
    }
    Ok(bytes.to_vec())
}

/// One page of the «Закладки» screen, routed to the endpoint its tab names.
///
/// Every section belongs to a profile, so all five of them are `require_token()`
/// calls upstream: without a token this returns the backend's own message rather
/// than issuing a request the server will refuse.
pub async fn bookmark_releases(
    token: Option<String>,
    tab: BookmarkTab,
    page: u32,
) -> Result<Pageable<Release>, String> {
    let client = api_client(token)?;
    let fetched = match tab {
        BookmarkTab::Collections | BookmarkTab::History | BookmarkTab::Favorites => {
            if tab.is_collections() {
                return Err("Раздел «Коллекции» содержит коллекции, а не релизы".to_owned());
            }
            if tab == BookmarkTab::History {
                client.history(page).await
            } else {
                client.favorites(page, None, None).await
            }
        }
        BookmarkTab::Watching
        | BookmarkTab::Plan
        | BookmarkTab::Completed
        | BookmarkTab::HoldOn
        | BookmarkTab::Dropped => match tab.status().and_then(WatchStatus::to_core) {
            Some(status) => client.watch_list(status, page, None, None).await,
            None => return Err("У раздела нет статуса списка".to_owned()),
        },
    };
    fetched.map_err(|e| e.to_string())
}

/// `GET /collectionFavorite/all/{page}` — the collections tab.
pub async fn bookmark_collections(
    token: Option<String>,
    page: u32,
) -> Result<Pageable<Collection>, String> {
    let client = api_client(token)?;
    client
        .favorite_collections(page)
        .await
        .map_err(|e| e.to_string())
}

/// `GET /favorite/add/{id}` / `GET /favorite/delete/{id}` — one toggle, two calls.
pub async fn set_favorite(
    token: Option<String>,
    release_id: i64,
    favorite: bool,
) -> Result<(), String> {
    let client = api_client(token)?;
    let response = if favorite {
        client.favorite_add(release_id).await
    } else {
        client.favorite_delete(release_id).await
    };
    response.map_err(|e| e.to_string())?;
    Ok(())
}

/// `GET /profile/list/add/{status}/{r_id}` — moves a release into a watch list.
pub async fn apply_watch_status(
    token: Option<String>,
    status: WatchStatus,
    release_id: i64,
) -> Result<(), String> {
    let Some(core) = status.to_core() else {
        return Err("Статус «Не смотрю» удаляет релиз из списка".to_owned());
    };
    let client = api_client(token)?;
    client
        .set_watch_status(core, release_id)
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// `GET /profile/list/delete/{status}/{r_id}` — takes a release out of a list.
pub async fn drop_watch_status(
    token: Option<String>,
    status: WatchStatus,
    release_id: i64,
) -> Result<(), String> {
    let Some(core) = status.to_core() else {
        return Ok(()); // nothing to delete: the release was in no list at all
    };
    let client = api_client(token)?;
    client
        .remove_watch_status(core, release_id)
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// `GET /history/add/{r_id}/{s_id}/{position}` — marks an episode as opened, which
/// is what puts the release on the «История» tab.
pub async fn history_add(
    token: Option<String>,
    release_id: i64,
    source_id: i64,
    position: i32,
) -> Result<(), String> {
    let client = api_client(token)?;
    client
        .history_add(release_id, source_id, position)
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// `GET /history/delete/{r_id}` — forgets a release on the «История» tab.
pub async fn history_delete(token: Option<String>, release_id: i64) -> Result<(), String> {
    let client = api_client(token)?;
    client
        .history_delete(release_id)
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// `POST /filter/{page}` — a catalogue shelf: the «Обзор» filter and every
/// «Главная» pill alike.
///
/// The body is the caller's [`FilterRequest`] verbatim, because a home shelf is a
/// filter and nothing else: [`crate::lists::FeedCategory::filter`] builds one, the
/// catalogue sort builds another. Core serialises the snake_case names the live
/// endpoint reads (`status_id`, `category_id`, `country`, `episodes_from` …) and
/// skips the fields left `None`, so an unfettered shelf posts just `{ "sort": 0 }`.
///
/// No token is needed: this is the shelf a guest opens «Главную» on.
pub async fn filter_releases(
    token: Option<String>,
    page: u32,
    filter: FilterRequest,
) -> Result<Pageable<Release>, String> {
    let client = api_client(token)?;
    client
        .filter_releases(page, &filter, None)
        .await
        .map_err(|e| e.to_string())
}

/// `POST /discover/{interesting,comments}` — the two editorial shelves.
pub async fn discover(
    token: Option<String>,
    kind: DiscoverKind,
) -> Result<Pageable<Release>, String> {
    let client = api_client(token)?;
    let found = match kind {
        DiscoverKind::Interesting => client.discover_interesting().await,
        DiscoverKind::Comments => client.discover_comments().await,
    };
    found.map_err(|e| e.to_string())
}

/// `GET /profile/info` → `GET /profile/{channel_id}` — the «Профиль» screen.
///
/// The info call only carries the numeric profile id, so the login, avatar and
/// list counters are read from the profile itself; a profile that has never been
/// filled in still yields the session's privilege level and rating.
pub async fn profile_snapshot(token: Option<String>) -> Result<ProfileSnapshot, String> {
    let client = api_client(token)?;
    let info: ProfileInfoResponse = client.profile_info().await.map_err(|e| e.to_string())?;

    let profile = match info.channel_id {
        Some(id) if id > 0 => client
            .profile(id)
            .await
            .map_err(|e| e.to_string())?
            .profile,
        // no channel id yet: the session itself is all this screen has
        _ => None,
    };

    Ok(ProfileSnapshot { info, profile })
}

/// Resolves an embed / host URL (Kodik, Sibnet, VK, RuTube, Anilibria, direct)
/// into playable stream variants via `anixart-parsers`.
pub async fn resolve(url: String) -> Result<ParsedVideo, String> {
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .build()
        .map_err(|e| e.to_string())?;

    anixart_parsers::resolve_with_client(&http, &url)
        .await
        .map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use anixart_core::ProfileToken;

    fn response(code: i32, token: &str) -> SocialSignInResponse {
        let profile_token = if token.is_empty() {
            None
        } else {
            Some(ProfileToken {
                id: 1,
                token: token.to_owned(),
            })
        };
        SocialSignInResponse {
            code,
            profile_token,
            ..SocialSignInResponse::default()
        }
    }

    #[test]
    fn a_token_in_the_response_becomes_the_session_token() {
        assert_eq!(token_of(&response(0, "tok-1")).unwrap(), "tok-1");
    }

    #[test]
    fn an_empty_token_is_not_a_success() {
        assert_eq!(
            token_of(&response(0, "")).unwrap_err(),
            "Нет токена в ответе входа"
        );
    }

    #[test]
    fn every_documented_code_gets_a_message() {
        for code in [1, 2, 3, 4, 5, 6, 7, 8, 402, 403, 999] {
            let message = describe_code(code);
            assert!(message.contains(&code.to_string()), "{code}: {message}");
        }
        assert!(describe_code(3).contains("ещё не привязан"));
        assert!(describe_code(402).contains("заблокирован"));
    }

    #[test]
    fn not_registered_explains_the_next_step() {
        let error = token_of(&response(3, "")).expect_err("code 3");
        assert!(error.starts_with("Этот социальный аккаунт ещё не привязан"), "{error}");
    }

    /// The paste fallback rejects a credential-less input *before* touching the
    /// network, so the manual path stays safe when the field was left empty.
    #[tokio::test]
    async fn a_google_paste_without_a_credential_fails_fast() {
        for pasted in ["", "   ", "#error=access_denied&error_description=User%20cancelled"] {
            let error = sign_in_google_auto(pasted.to_owned(), oauth::FIREBASE_API_KEY.to_owned())
                .await
                .expect_err("no credential");
            assert_eq!(error, "В этом вводе нет Google-креденциала", "{pasted:?}");
        }
    }

    #[test]
    fn the_callback_timeout_leaves_room_for_a_browser_login() {
        // Long enough to read a 2FA prompt, short enough not to pin a port forever.
        assert!((60..=600).contains(&CALLBACK_TIMEOUT.as_secs()));
    }

    #[test]
    fn clipboard_text_trims_what_a_reader_printed() {
        assert_eq!(
            clipboard_text(b"https://app1593901251-login.tg.dev/tglogin?code=abc\n").as_deref(),
            Some("https://app1593901251-login.tg.dev/tglogin?code=abc")
        );
        assert_eq!(
            clipboard_text("  profileToken\t\n".as_bytes()).as_deref(),
            Some("profileToken")
        );
        assert_eq!(clipboard_text(b""), None);
        assert_eq!(clipboard_text(b"   \n"), None);
    }

    #[test]
    fn clipboard_text_refuses_an_image_selection() {
        // `wl-paste` would serve the PNG the user screenshotted instead of text.
        assert_eq!(clipboard_text(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"), None);
        assert_eq!(clipboard_text(&[0x01, 0x00, 0x02, 0x00]), None);
    }

    #[test]
    fn clipboard_readers_cover_wayland_and_x11() {
        let programs: Vec<&str> = CLIPBOARD_READERS.iter().map(|r| r.program).collect();
        for expected in ["wl-paste", "xclip", "xsel"] {
            assert!(programs.contains(&expected), "{expected} is never tried");
        }
        // Every candidate needs arguments: a bare `wl-paste` would happily dump an
        // image, and `xclip` / `xsel` without a selection name read the wrong clip.
        assert!(
            CLIPBOARD_READERS.iter().all(|r| !r.args.is_empty()),
            "a reader must ask for a text selection"
        );
        assert!(CLIPBOARD_READERS[0].args.contains(&"--type"));
        // The primary selection is only reached after the clipboard one.
        assert!(CLIPBOARD_READERS.iter().skip(3).any(|r| r.args.contains(&"--primary")));
    }

    #[test]
    fn the_clipboard_timeout_is_short_enough_to_feel_instant() {
        assert!((2..=30).contains(&CLIPBOARD_TIMEOUT.as_secs()));
    }

    /// No Wayland / X session on the CI runner: the readers must fail fast and name
    /// themselves, never hang and never hand back an empty string.
    #[tokio::test]
    async fn read_clipboard_answers_even_without_a_desktop_session() {
        match read_clipboard().await {
            Ok(text) => assert!(!text.trim().is_empty()),
            Err(error) => {
                assert!(error.contains("wl-paste"), "{error}");
                assert!(error.contains("буфер"), "{error}");
            }
        }
    }
}
