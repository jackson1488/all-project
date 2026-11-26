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
        let expected_head = format!("POST /{} HTTP/1.1", provider.endpoint());
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
        assert!(request.starts_with(&format!("POST /{} HTTP/1.1", provider.endpoint())));
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
    let c1 = client(&mock);
    let err = c1
        .exchange_telegram_code_at(&mock.base, "c", "v")
        .await
        .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg.contains("HTTP 400")),
        "{err:?}"
    );

    let mock = MockServer::spawn("200 OK", r#"{"access_token":"no id token here"}"#);
    let client2 = client(&mock);
    let err = client2
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
    let api_req = api.request();
    assert_eq!(body_of(&api_req), "telegramIdToken=FINAL-ID-TOKEN");
    assert!(api_req.starts_with("POST /auth/telegram HTTP/1.1"));
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

// ---------------------------------------------------------------------------
// Google → Firebase accounts:signInWithIdp
// ---------------------------------------------------------------------------

const FIREBASE_IDP_OK: &str = r#"{"kind":"identitytoolkit#VerifyAssertionResponse",
"localId":"1234567890","email":"neko@gmail.com","verified_email":true,
"idToken":"FIREBASE-ID-JWT","refreshToken":"fb-refresh","expiresIn":"3600",
"federatedId":"1097654321","providerId":"google.com","rawId":"1097654321"}"#;

fn http_client() -> reqwest::Client {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(5))
        .build()
        .expect("http client")
}

#[tokio::test]
async fn firebase_exchange_posts_the_google_id_token_as_postbody() {
    let mock = MockServer::spawn("200 OK", FIREBASE_IDP_OK);
    let endpoint = oauth::firebase_sign_in_with_idp_url_at(&mock.base, "AIzaTestKey");

    let token = oauth::exchange_google_to_firebase_id_token_at(
        &http_client(),
        &endpoint,
        "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature",
        false,
    )
    .await
    .expect("exchange");

    assert_eq!(token, "FIREBASE-ID-JWT");

    let request = mock.request();
    assert!(
        request.starts_with("POST /v1/accounts:signInWithIdp?key=AIzaTestKey HTTP/1.1"),
        "request line: {request}"
    );
    assert!(head_of(&request).contains("content-type: application/json"), "{request}");
    let body: serde_json::Value = serde_json::from_str(body_of(&request)).expect("json body");
    assert_eq!(
        body["postBody"],
        "id_token=eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature&providerId=google.com"
    );
    assert_eq!(body["requestUri"], "http://localhost");
    assert_eq!(body["returnIdpCredential"], true);
    assert_eq!(body["returnSecureToken"], true);
}

#[tokio::test]
async fn firebase_exchange_marks_access_tokens_differently() {
    let mock = MockServer::spawn("200 OK", FIREBASE_IDP_OK);
    let endpoint = oauth::firebase_sign_in_with_idp_url_at(&mock.base, "K");

    let token = oauth::exchange_google_to_firebase_id_token_at(
        &http_client(),
        &endpoint,
        "ya29.a0AfB_byC7Qm8H",
        oauth::google_token_is_access_token("ya29.a0AfB_byC7Qm8H"),
    )
    .await
    .expect("exchange");

    assert_eq!(token, "FIREBASE-ID-JWT");
    let body: serde_json::Value = serde_json::from_str(body_of(&mock.request())).expect("json");
    assert_eq!(body["postBody"], "access_token=ya29.a0AfB_byC7Qm8H&providerId=google.com");
}

#[tokio::test]
async fn firebase_exchange_errors_become_oauth_errors() {
    // Firebase answers 400 with an INVALID_IDP_RESPONSE payload.
    let mock = MockServer::spawn(
        "400 Bad Request",
        r#"{"error":{"code":400,"message":"INVALID_IDP_RESPONSE","errors":[{"message":"INVALID_IDP_RESPONSE","domain":"global","reason":"invalid"}]}}"#,
    );
    let err = oauth::exchange_google_to_firebase_id_token_at(
        &http_client(),
        &oauth::firebase_sign_in_with_idp_url_at(&mock.base, "K"),
        "bogus",
        false,
    )
    .await
    .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg.contains("HTTP 400") && msg.contains("INVALID_IDP_RESPONSE")),
        "{err:?}"
    );

    // A 200 without `idToken` is refused too.
    let mock = MockServer::spawn("200 OK", r#"{"federatedId":"1097654321"}"#);
    let err = oauth::exchange_google_to_firebase_id_token_at(
        &http_client(),
        &oauth::firebase_sign_in_with_idp_url_at(&mock.base, "K"),
        "bogus",
        false,
    )
    .await
    .expect_err("must fail");
    assert!(
        matches!(&err, AnixartError::OAuth(msg) if msg == "Firebase signInWithIdp returned no idToken"),
        "{err:?}"
    );
}

#[tokio::test]
async fn full_google_flow_exchanges_through_firebase_then_signs_in() {
    let idp = MockServer::spawn("200 OK", FIREBASE_IDP_OK);
    let api = MockServer::spawn("200 OK", SIGN_IN_OK);
    let mut client = client(&api);
    assert!(client.token().is_none());

    let google_jwt = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature";
    let endpoint = oauth::firebase_sign_in_with_idp_url_at(&idp.base, oauth::FIREBASE_API_KEY);
    let resp = client
        .sign_in_google_via_firebase_at(&endpoint, google_jwt, false)
        .await
        .expect("flow");

    assert!(resp.is_success());
    assert_eq!(client.token(), Some("tok_social"));

    // step 1: identitytoolkit received the Google JWT as `id_token=…`
    let idp_request = idp.request();
    assert!(
        idp_request.starts_with(&format!(
            "POST /v1/accounts:signInWithIdp?key={} HTTP/1.1",
            oauth::FIREBASE_API_KEY
        )),
        "request line: {idp_request}"
    );
    let body: serde_json::Value = serde_json::from_str(body_of(&idp_request)).expect("json");
    assert_eq!(
        body["postBody"],
        format!("id_token={google_jwt}&providerId=google.com")
    );

    // step 2: auth/google received the **Firebase** idToken, not the Google one
    let api_request = api.request();
    assert!(api_request.starts_with("POST /auth/google HTTP/1.1"), "{api_request}");
    assert_eq!(body_of(&api_request), "googleIdToken=FIREBASE-ID-JWT");
}

#[tokio::test]
async fn firebase_exchange_failure_stops_before_anixart() {
    let idp = MockServer::spawn("400 Bad Request", r#"{"error":{"message":"INVALID_ID_TOKEN"}}"#);
    let api = MockServer::spawn("200 OK", SIGN_IN_OK);
    let mut client = client(&api);

    let endpoint = oauth::firebase_sign_in_with_idp_url_at(&idp.base, "K");
    let err = client
        .sign_in_google_via_firebase_at(&endpoint, "ey.abc.def", false)
        .await
        .expect_err("must fail");
    assert!(matches!(&err, AnixartError::OAuth(msg) if msg.contains("HTTP 400")), "{err:?}");
    assert!(client.token().is_none());
    // auth/google was never reached: its mock socket is still unanswered, so a
    // request there would hang instead of returning.
    assert_eq!(
        api.requests.recv_timeout(std::time::Duration::from_millis(50)),
        Err(mpsc::RecvTimeoutError::Timeout)
    );
}

#[test]
fn the_baked_in_firebase_endpoint_matches_the_documented_url() {
    assert_eq!(
        oauth::firebase_sign_in_with_idp_url(oauth::FIREBASE_API_KEY),
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp\
         ?key=AIzaSyBFPckWOsp0MEqb_1gwszvM1ILdUixM-uw"
    );
    // The client's own HTTP handle is reusable, which is what lets
    // `sign_in_google_via_firebase_at` share its pool and default headers.
    let client = AnixartClient::with_base_url("http://127.0.0.1:1/").expect("client");
    let request = client.http().post("https://example.test/").build().expect("request");
    assert_eq!(request.url().as_str(), "https://example.test/");
    assert!(request.headers().contains_key("user-agent"));
}
