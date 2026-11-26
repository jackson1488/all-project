//! Async glue between the iced runtime and `anixart-core` / `anixart-parsers`.
//!
//! Every helper builds its client *inside* the spawned future: iced is powered by
//! the `tokio` executor, so all reqwest traffic then runs on a real tokio runtime.
//!
//! The 1-click Google sign-in additionally owns a short-lived loopback HTTP
//! listener ([`google_callback::Listener`]) that the browser hands its
//! implicit-flow redirect back to; the captured credential is exchanged for a
//! **Firebase** ID token through `anixart_core::oauth` before `POST /auth/google`.

use std::time::Duration;

use anixart_core::{
    oauth, AnixartClient, Episode, EpisodeType, FeedItem, Pageable, Release,
    ReleaseSearchResponse, SocialSignInResponse, Source,
};
use anixart_parsers::ParsedVideo;

use crate::login::{google_callback_error, google_credential_from, random_verifier};

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
        .ok_or_else(|| "No token in sign-in response".to_owned())
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
        0 => "No token in sign-in response".to_owned(),
        1 => "Anixart rejected the credential (code 1)".to_owned(),
        2 => "Invalid OAuth request (code 2)".to_owned(),
        3 => "This social account is not linked to an Anixart profile yet — \
              sign up with login + e-mail in the official client, then sign in \
              here (code 3)"
            .to_owned(),
        4 => "Invalid login (code 4)".to_owned(),
        5 => "Invalid e-mail (code 5)".to_owned(),
        6 => "Login already taken (code 6)".to_owned(),
        7 => "E-mail already taken (code 7)".to_owned(),
        402 => "This account is banned (code 402)".to_owned(),
        403 => "This account is permanently banned (code 403)".to_owned(),
        other => format!("Anixart returned code {other}"),
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
        .ok_or_else(|| "No Google credential in that input".to_owned())?;

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
                "Timed out after {} s waiting for the Google redirect. Press the button \
                 again, or paste the URL the browser landed on into the field below.",
                CALLBACK_TIMEOUT.as_secs()
            ))
        }
    };

    if let Some(reason) = google_callback_error(&payload) {
        return Err(format!("Google did not grant the sign-in: {reason}"));
    }
    let (token, is_access_token) = google_credential_from(&payload).ok_or_else(|| {
        "The Google redirect carried no id_token / access_token — paste the URL the \
         browser landed on into the field below."
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
                .map_err(|e| format!("Google callback listener: {e}"))?;
            match handle(stream).await {
                Ok(Some(payload)) => return Ok(payload),
                // `Ok(None)` = the relay page was served, `Err(_)` = the client hung
                // up mid-request. Neither ends the wait: browsers routinely open more
                // than one connection (favicons, reloads, redirects) to this origin.
                _ => {}
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
        let mut parts = lines.next()?.trim().split_whitespace();
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
    pub const RELAY_PAGE: &str = r#"<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Anixart — Google sign-in</title>
<style>
 body{font:15px/1.5 system-ui,sans-serif;background:#12141c;color:#e8eaf2;margin:0;
      display:flex;min-height:100vh;align-items:center;justify-content:center}
 main{max-width:34rem;padding:2rem;text-align:center}
 code{background:#1d2130;border-radius:.35rem;padding:.1rem .35rem}
 #hint{color:#8f96ad;font-size:.9rem;word-break:break-all}
</style></head>
<body><main>
 <h2 id="title">Returning to Anixart…</h2>
 <p id="hint">This page only forwards the sign-in redirect to the desktop client
 listening on <code>127.0.0.1</code>. It can be closed afterwards.</p>
 <script>
 (function () {
   var hash = location.hash ? location.hash.slice(1) : "";
   var query = location.search ? location.search.slice(1) : "";
   var payload = hash || query;
   if (!payload) {
     document.getElementById("title").textContent = "No credential in this redirect";
     return;
   }
   fetch("/callback", {
     method: "POST",
     headers: { "Content-Type": "text/plain;charset=utf-8" },
     body: payload,
     keepalive: true
   }).then(function () {
     document.getElementById("title").textContent = "Signed in — you can close this tab";
   }).catch(function () {
     document.getElementById("hint").textContent =
       "Could not reach the client. Copy this into Anixart instead: " + location.href;
   });
 })();
 </script>
</main></body></html>"#;

    /// Acknowledgement of the captured fragment.
    pub const DONE_PAGE: &str = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">\
        <title>Anixart — Google sign-in</title></head><body style=\"font:15px system-ui,sans-serif;\
        background:#12141c;color:#e8eaf2;margin:2rem\">Signed in. This tab can be closed.\
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
                let _ = junk.read_to_string(&mut String::new()).await;
                drop(junk);

                let mut stream = TcpStream::connect(std::net::SocketAddr::from(([127, 0, 0, 1], port)))
                    .await
                    .expect("connect post");
                let post = format!(
                    "POST /callback HTTP/1.1\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{payload}",
                    payload.len()
                );
                let _ = stream.write_all(post.as_bytes()).await;
                let _ = stream.read_to_string(&mut String::new()).await;
            });

            assert_eq!(capture(listener).await.expect("capture"), payload);
            driver.await.expect("driver");
        }

        #[test]
        fn relay_page_posts_to_the_callback_path() {
            assert!(RELAY_PAGE.contains("fetch(\"/callback\""));
            assert!(RELAY_PAGE.contains("location.hash"));
            assert!(RELAY_PAGE.contains("location.search"));
            assert!(DONE_PAGE.contains("Signed in"));
        }
    }
}

/// `GET /article/all/{page}` — feed page.
pub async fn feed(token: Option<String>, page: u32) -> Result<Pageable<FeedItem>, String> {
    let client = api_client(token)?;
    client.feed(page).await.map_err(|e| e.to_string())
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
        .ok_or_else(|| format!("Release {id} not found"))
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
        SocialSignInResponse {
            code,
            profile_token: (!token.is_empty()).then(|| ProfileToken {
                id: 1,
                token: token.to_owned(),
            }),
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
            "No token in sign-in response"
        );
    }

    #[test]
    fn every_documented_code_gets_a_message() {
        for code in [1, 2, 3, 4, 5, 6, 7, 8, 402, 403, 999] {
            let message = describe_code(code);
            assert!(message.contains(&code.to_string()), "{code}: {message}");
        }
        assert!(describe_code(3).contains("not linked to an Anixart profile"));
        assert!(describe_code(402).contains("banned"));
    }

    #[test]
    fn not_registered_explains_the_next_step() {
        let error = token_of(&response(3, "")).expect_err("code 3");
        assert!(error.starts_with("This social account is not linked"));
    }

    /// The paste fallback rejects a credential-less input *before* touching the
    /// network, so the manual path stays safe when the field was left empty.
    #[tokio::test]
    async fn a_google_paste_without_a_credential_fails_fast() {
        for pasted in ["", "   ", "#error=access_denied&error_description=User%20cancelled"] {
            let error = sign_in_google_auto(pasted.to_owned(), oauth::FIREBASE_API_KEY.to_owned())
                .await
                .expect_err("no credential");
            assert_eq!(error, "No Google credential in that input", "{pasted:?}");
        }
    }

    #[test]
    fn the_callback_timeout_leaves_room_for_a_browser_login() {
        // Long enough to read a 2FA prompt, short enough not to pin a port forever.
        assert!(CALLBACK_TIMEOUT.as_secs() >= 60 && CALLBACK_TIMEOUT.as_secs() <= 600);
    }
}
