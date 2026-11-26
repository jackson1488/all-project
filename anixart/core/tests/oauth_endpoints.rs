//! Offline integration tests for the social sign-in endpoints.
//!
//! A one-shot `TcpListener` stands in for `api-s.anixsekai.com` and for
//! `oauth.telegram.org`, so the exact request line, headers and form body of
//! every `POST /auth/{provider}` call are asserted without network access.

use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc::{self, Receiver};

use anixart_core::oauth::{self, SocialProvider, TelegramPkce};
use anixart_core::{AnixartClient, AnixartError, SocialSignInResponse};

const SIGN_IN_OK: &str = include_str!("../fixtures/social_sign_in.json");
const NOT_REGISTERED: &str = include_str!("../fixtures/social_not_registered.json");

struct MockServer {
    base: String,
    requests: Receiver<String>,
}

impl MockServer {
    /// Serves exactly one request with `status` + JSON `body`.
    fn spawn(status: &'static str, body: &'static str) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind ephemeral port");
        let addr = listener.local_addr().expect("local addr");
        let (tx, rx) = mpsc::channel();
        std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let request = read_request(&mut stream);
            let _ = tx.send(request);
            let head = format!(
                "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len()
            );
            let _ = stream.write_all(head.as_bytes());
            let _ = stream.write_all(body.as_bytes());
            let _ = stream.flush();
        });
        Self {
            base: format!("http://{addr}/"),
            requests: rx,
        }
    }

    fn request(&self) -> String {
        self.requests.recv().expect("client request")
    }
}

/// Reads headers + body of a single HTTP/1.x request.
fn read_request(stream: &mut TcpStream) -> String {
    let mut raw = Vec::new();
    let mut buf = [0u8; 4096];
    loop {
        match stream.read(&mut buf) {
            Ok(0) | Err(_) => break,
            Ok(n) => {
                raw.extend_from_slice(&buf[..n]);
                if request_is_complete(&raw) {
                    break;
                }
            }
        }
    }
    String::from_utf8_lossy(&raw).into_owned()
}

fn request_is_complete(raw: &[u8]) -> bool {
    expected_len(raw).is_some_and(|total| raw.len() >= total)
}

/// Number of bytes the whole request occupies once enough data was read:
/// end-of-headers plus `Content-Length`.
fn expected_len(raw: &[u8]) -> Option<usize> {
    let idx = raw.windows(4).position(|w| w == b"\r\n\r\n")?;
    let end = idx + 4;
    let headers = String::from_utf8_lossy(&raw[..idx]).to_lowercase();
    let body_len = match headers
        .lines()
        .find_map(|line| line.strip_prefix("content-length:"))
    {
        Some(raw_len) => raw_len.trim().parse::<usize>().ok()?,
        // bodyless request: the header block is all of it
        None => 0,
    };
    Some(end + body_len)
}

fn body_of(request: &str) -> &str {
    request.split_once("\r\n\r\n").map(|(_, body)| body).unwrap_or("")
}

fn head_of(request: &str) -> String {
    request
        .split_once("\r\n\r\n")
        .map(|(head, _)| head.to_lowercase())
        .unwrap_or_default()
}

fn client(mock: &MockServer) -> AnixartClient {
    AnixartClient::with_base_url(mock.base.clone()).expect("client")
}

#[tokio::test]
async fn sign_in_telegram_posts_exact_field_and_stores_token() {
    let mock = MockServer::spawn("200 OK", SIGN_IN_OK);
    let mut client = client(&mock);
    assert!(client.token().is_none());

    let resp = client.sign_in_telegram("id.tok.sig").await.expect("sign in");

    assert!(resp.is_success());
    assert_eq!(resp.token(), Some("tok_social"));
    assert_eq!(client.token(), Some("tok_social"));
    assert_eq!(resp.profile.map(|p| p.login).as_deref(), Some("neko"));

    let request = mock.request();
    assert!(
        request.starts_with("POST /auth/telegram HTTP/1.1"),
        "request line: {request}"
    );
    assert!(head_of(&request).contains("content-type: application/x-www-form-urlencoded"));
    assert_eq!(body_of(&request), "telegramIdToken=id.tok.sig");
}

/// Calls the provider-specific sign-in method so the test can stay exhaustive.
async fn call_sign_in(
    client: &mut AnixartClient,
    provider: SocialProvider,
    credential: &str,
) -> anixart_core::Result<SocialSignInResponse> {
    match provider {
        SocialProvider::Telegram => client.sign_in_telegram(credential).await,
        SocialProvider::Vk => client.sign_in_vk(credential).await,
        SocialProvider::Google => client.sign_in_google(credential).await,
        SocialProvider::Yandex => client.sign_in_yandex(credential).await,
    }
}

#[tokio::test]
async fn every_provider_maps_to_its_own_path_and_field() {
    for provider in SocialProvider::ALL {
        let mock = MockServer::spawn("200 OK", SIGN_IN_OK);
        let mut client = client(&mock);
        let resp = call_sign_in(&mut client, provider, "cred")
            .await
            .expect("sign in");
        assert!(resp.is_success(), "{provider:?}");
        assert_eq!(client.token(), Some("tok_social"), "{provider:?}");

        let request = mock.request();
        let expected_head = format!("POST {} HTTP/1.1", provider.endpoint());
        assert!(request.starts_with(&expected_head), "{request}");
        assert_eq!(
            body_of(&request),
            format!("{}=cred", provider.credential_field())
        );
        // no X-Long-Code header on the plain sign-in variant
        assert!(!head_of(&request).contains("x-long-code"));
    }
}

#[tokio::test]
async fn credential_values_are_form_encoded() {
    let mock = MockServer::spawn("200 OK", SIGN_IN_OK);
    let mut client = client(&mock);
    client
        .sign_in_yandex("a b=c/d&e")
        .await
        .expect("sign in");
    assert_eq!(
        body_of(&mock.request()),
        "yandexAccessToken=a%20b%3Dc%2Fd%26e"
    );
}

#[tokio::test]
async fn sign_up_sends_login_email_credential_and_long_code_header() {
    let mock = MockServer::spawn("200 OK", NOT_REGISTERED);
    let mut client = client(&mock);

    let resp = client
        .sign_up_telegram("neko", "Neko@Mail.RU", "idtok")
        .await
        .expect("sign up");

    let request = mock.request();
    assert!(request.starts_with("POST /auth/telegram HTTP/1.1"));
    assert!(head_of(&request).contains("x-long-code: 1"), "{}", head_of(&request));
    assert_eq!(
        body_of(&request),
        "login=neko&email=neko%40mail.ru&telegramIdToken=idtok"
    );
    // code 3 => no session token stored yet
    assert!(resp.is_not_registered());
    assert!(client.token().is_none());
    assert!(resp.needs_email_code());
    assert_eq!(resp.code_length, Some(6));
    assert_eq!(resp.code_time_left, Some(240));
    assert_eq!(resp.code_timestamp_expires, 1_790_000_000);
    assert_eq!(resp.suggested_logins, ["neko", "neko_1"]);
    assert_eq!(resp.email.as_deref(), Some("Neko@mail.ru"));
    assert_eq!(resp.hash, "h_9f2c");
}

#[tokio::test]
async fn sign_up_variants_share_the_field_order_for_every_provider() {
    for provider in SocialProvider::ALL {
        let mock = MockServer::spawn("200 OK", SIGN_IN_OK);
        let mut client = client(&mock);
        let cred = "cc";
        let resp = match provider {
            SocialProvider::Telegram => client.sign_up_telegram("lg", "a@b.c", cred).await,
            SocialProvider::Vk => client.sign_up_vk("lg", "a@b.c", cred).await,
            SocialProvider::Google => client.sign_up_google("lg", "a@b.c", cred).await,
            SocialProvider::Yandex => client.sign_up_yandex("lg", "a@b.c", cred).await,
        }
        .expect("sign up");
        assert!(resp.is_success(), "{provider:?}");
        let request = mock.request();
        assert!(request.starts_with(&format!("POST {} HTTP/1.1", provider.endpoint())));
        assert_eq!(
            body_of(&request),
            format!("login=lg&email=a%40b.c&{}=cc", provider.credential_field())
        );
    }
}

#[tokio::test]
async fn telegram_token_exchange_uses_the_documented_body() {
    let mock = MockServer::spawn(
        "200 OK",
        r#"{"access_token":"x","id_token":"JWT-from-telegram","expires_in":86400}"#,
    );
    let client = client(&mock);
    let token = client
        .exchange_telegram_code_at(&mock.base, "the code", "the verifier")
        .await
        .expect("exchange");
    assert_eq!(token, "JWT-from-telegram");

    let request = mock.request();
    assert!(request.starts_with("POST / HTTP/1.1"), "{request}");
    let head = head_of(&request);
    assert!(head.contains("accept: application/json"), "{head}");
    assert!(head.contains("content-type: application/x-www-form-urlencoded"));
    assert_eq!(
        body_of(&request),
        "grant_type=authorization_code&client_id=8789559054&code=the+code\
         &redirect_uri=https%3A%2F%2Fapp1593901251-login.tg.dev%2Ftglogin&code_verifier=the+verifier"
    );
}

#[tokio::test]
async fn telegram_token_exchange_errors_become_oauth_errors() {
    let mock = MockServer::spawn("400 Bad Request", r#"{"error":"invalid_grant"}"#);
    let client = client(&mock);
    let err = client
        .exchange_telegram_code_at(&mock.base, "c", "v")
        .await
        .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg.contains("HTTP 400")),
        "{err:?}"
    );

    let mock = MockServer::spawn("200 OK", r#"{"access_token":"no id token here"}"#);
    let client = client(&mock);
    let err = client
        .exchange_telegram_code_at(&mock.base, "c", "v")
        .await
        .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg == "No id_token in response"),
        "{err:?}"
    );
}

#[tokio::test]
async fn telegram_inapp_url_is_returned_when_offered() {
    let mock = MockServer::spawn(
        "200 OK",
        r#"{"result":{"url":"tg://proxy?reference=1&code=abc"}}"#,
    );
    let client = client(&mock);
    let pkce = TelegramPkce {
        verifier: "v".into(),
        challenge: "c".into(),
    };
    let url = client
        .fetch_telegram_inapp_url_at(&mock.base)
        .await
        .expect("crossapp");
    assert_eq!(url.as_deref(), Some("tg://proxy?reference=1&code=abc"));
    // the real helper builds the same crossapp URL the mock was fed
    assert_eq!(
        oauth::telegram_crossapp_url_with(&pkce),
        format!(
            "{}crossapp?client_id=8789559054&response_type=code&scope=openid%20profile\
             &redirect_uri=https%3A%2F%2Fapp1593901251-login.tg.dev%2Ftglogin\
             &android_sdk=1&code_challenge=c&code_challenge_method=S256",
            "https://oauth.telegram.org/"
        )
    );
}

#[tokio::test]
async fn telegram_inapp_url_none_on_non_200() {
    let mock = MockServer::spawn("502 Bad Gateway", "{}");
    let client = client(&mock);
    let url = client
        .fetch_telegram_inapp_url_at(&mock.base)
        .await
        .expect("crossapp");
    assert!(url.is_none());
}

#[tokio::test]
async fn full_telegram_redirect_flow_signs_in() {
    let token = MockServer::spawn("200 OK", r#"{"id_token":"FINAL-ID-TOKEN"}"#);
    let api = MockServer::spawn("200 OK", SIGN_IN_OK);
    let mut client = client(&api);

    let redirect = "https://app1593901251-login.tg.dev/tglogin?code=CODE&state=st";
    let resp = client
        .sign_in_telegram_redirect_at(&token.base, redirect, "verifier")
        .await
        .expect("flow");

    assert!(resp.is_success());
    assert_eq!(client.token(), Some("tok_social"));
    assert_eq!(
        body_of(&token.request()),
        "grant_type=authorization_code&client_id=8789559054&code=CODE\
         &redirect_uri=https%3A%2F%2Fapp1593901251-login.tg.dev%2Ftglogin&code_verifier=verifier"
    );
    assert_eq!(body_of(&api.request()), "telegramIdToken=FINAL-ID-TOKEN");
    assert!(api.request().starts_with("POST /auth/telegram HTTP/1.1"));
}

#[tokio::test]
async fn denied_redirect_short_circuits_without_network() {
    let mut client = AnixartClient::with_base_url("http://127.0.0.1:1/").expect("client");
    let err = client
        .sign_in_telegram_redirect_at(
            "http://127.0.0.1:1/token",
            "https://app1593901251-login.tg.dev/tglogin?error=access_denied&error_description=User%20cancelled",
            "v",
        )
        .await
        .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg == "User cancelled"),
        "{err:?}"
    );
    assert!(client.token().is_none());
}

#[test]
fn social_response_accepts_both_key_styles() {
    let snake: SocialSignInResponse = serde_json::from_str(
        r#"{"code":0,"profile_token":{"id":1,"token":"t"},"code_length":4,"code_time_left":9}"#,
    )
    .expect("snake");
    let camel: SocialSignInResponse = serde_json::from_str(
        r#"{"code":0,"profileToken":{"id":1,"token":"t"},"codeLength":4,"codeTimeLeft":9}"#,
    )
    .expect("camel");
    assert_eq!(snake, camel);
    assert_eq!(snake.token(), Some("t"));
    assert!(!snake.needs_email_code(), "hash is empty in both payloads");
}

#[test]
fn google_codes_differ_from_the_other_providers() {
    assert_eq!(oauth::codes::CODE_ALREADY_SEND, 8);
    assert_eq!(oauth::codes::google::EMAIL_CHANGED, 8);
    assert_eq!(oauth::codes::google::TOO_MANY_REGISTRATIONS, 11);
    assert_eq!(oauth::codes::TOO_MANY_REGISTRATIONS, 10);
}
