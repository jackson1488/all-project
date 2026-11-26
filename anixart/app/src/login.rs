//! Экран входа: пароль, соцсети через системный браузер, прямой токен сессии и
//! гостевой пропуск.
//!
//! Все способы сходятся в одном месте — `profileToken.token` Anixart, — и
//! отличаются только тем, как этот токен добывают:
//!
//! * **Пароль** — `POST /auth/signIn` (`anixart-core`).
//! * **Telegram** — OAuth 2.0 + PKCE Login Widget. Приложение генерирует пару
//!   PKCE, открывает `https://oauth.telegram.org/auth` через `xdg-open`, а
//!   пользователь вставляет обратно то, на что браузер приземлился. Подходят все
//!   формы: полный `https://app…-login.tg.dev/tglogin?code=…`, только часть
//!   `?code=…` / `code=…` из адресной строки (код тогда меняется на `id_token`
//!   с verifier'ом этого окна) или `id_token`, полученный где угодно ещё.
//! * **VK** — VK ID. Браузер открывается на authorize URL VK ID; вставляют обратно
//!   редирект с `access_token` или сам токен.
//! * **Yandex** — Yandex ID, иммитуый поток (`response_type=token`). Клиентский id
//!   и хост берёт из `anixart_core::oauth`, редирект привязан к Android-пакету,
//!   поэтому единственный способ вернуть credencial — скопировать адресную строку.
//! * **Google** — один клик, без поиска токена. Google OAuth web client id и
//!   Firebase API key, которые возит с собой APK Anixart, уже вписаны
//!   (`oauth::GOOGLE_WEB_CLIENT_ID`, `oauth::FIREBASE_API_KEY`), и кнопка
//!   **«Войти через Google (браузер)»** поднимает loopback-слушатель на
//!   `127.0.0.1:0`, открывает иммитуый authorize URL, ловит редирект (страница
//!  -релей читает фрагмент URL и отправляет его обратно POST'ом), меняет
//!   credencial Google на **Firebase** ID token через
//!   `identitytoolkit.googleapis.com` `accounts:signInWithIdp` и постит его в
//!   `auth/google`. Поле вставки остаётся универсальным запасным путём.
//! * **Токен** — уже выпущенный токен сессии принимается мгновенно, без запроса.
//!
//! * **Гость** — «Продолжить без входа» открывает «Обзор» и «Главную»: ни один
//!   шельф этих экранов не требует токена, а `favorites` / `watch_list` /
//!   `history` / `profile` на время гостевого сеанса просто не запрашиваются:
//!   `App` сверяет `fetch_allowed()` перед каждым вызовом сети.
//!
//! Loopback-слушатель нужен только Google: web client id принимает
//! `http://127.0.0.1:<любой порт>. Хосты редиректа Telegram / VK
//! (`app1593901251-login.tg.dev`, `vk54701760://vk.ru/blank.html`) привязаны к
//! Android-пакету, поэтому `http://localhost` они отклонят — копирование URL из
//! адресной строки там равноценный, одобренный провайдером запасной путь.
//!
//! Копирование, впрочем, — то место, где Linux ломается: **Ctrl+V не доходит до
//! `text_input` в iced 0.13 под Wayland**, потому что winit 0.30 говорит только на
//! протокол `zwlr-data-control`, которого в GNOME и KDE нет. Поэтому каждая вкладка
//! со вставкой несёт кнопку **«Вставить и войти»** ([`PasteTarget`]): она тянет
//! выделение через [`iced::clipboard::read()`], а когда тот отвечает пустотой —
//! через читалки `wl-paste` / `xclip` / `xsel` из [`crate::net::read_clipboard`],
//! пишет текст в поле своей вкладки и сразу отправляет вход.

use anixart_core::oauth;
use iced::widget::{button, container, scrollable, text_input, Column, Row};
use iced::{alignment, Element, Length};

use crate::app::Message;
use crate::theme;
use crate::widgets;

/// Способы входа, которые принимает backend Anixart (`AuthApi.java`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum LoginMethod {
    #[default]
    Password,
    Telegram,
    Vk,
    Yandex,
    Google,
    /// Прямой токен сессии — мгновенный вход, без запроса.
    Token,
}

impl LoginMethod {
    /// Все способы в порядке пилюль.
    pub const ALL: [LoginMethod; 6] = [
        LoginMethod::Password,
        LoginMethod::Telegram,
        LoginMethod::Vk,
        LoginMethod::Yandex,
        LoginMethod::Google,
        LoginMethod::Token,
    ];

    /// Подпись пилюли.
    pub fn label(self) -> &'static str {
        match self {
            LoginMethod::Password => "Пароль",
            LoginMethod::Telegram => "Telegram",
            LoginMethod::Vk => "VK",
            LoginMethod::Yandex => "Yandex",
            LoginMethod::Google => "Google",
            LoginMethod::Token => "Токен",
        }
    }

    /// Устойчивый идентификатор, который пишется в `session.json`.
    pub fn as_str(self) -> &'static str {
        match self {
            LoginMethod::Password => "password",
            LoginMethod::Telegram => "telegram",
            LoginMethod::Vk => "vk",
            LoginMethod::Yandex => "yandex",
            LoginMethod::Google => "google",
            LoginMethod::Token => "token",
        }
    }
}

/// Что пользователь вставил на вкладке Telegram.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TelegramPaste {
    /// URI редиректа: его нужно превратить в `id_token` с verifier'ом PKCE,
    /// который приложение сгенерировало при открытии браузера.
    Redirect(String),
    /// Готовый `id_token` — уходит прямо в `auth/telegram`.
    IdToken(String),
}

/// Redirect URI ручной (со вставкой) Google-flow. Web client id Google принимает
/// loopback-origin, а `http://localhost` — тот URI, который `google-services.json`
/// регистрирует для `default_web_client_id`.
pub const GOOGLE_MANUAL_REDIRECT: &str = "http://localhost";

/// Подпись кнопки буфера обмена, которая есть на каждой вкладке со вставкой.
pub const PASTE_BUTTON: &str = "Вставить и войти";

/// Язык, по которому выбирается региональный хост Yandex OAuth.
///
/// Интерфейс этого клиента целиком русский, поэтому и вход идёт через
/// `oauth.yandex.ru` — тот же хост, что SDK даёт устройству с `ru` в локали.
pub const YANDEX_LANGUAGE: &str = "ru";

/// Какому полю вкладки принадлежит кнопка **«Вставить и войти»**.
///
/// Ctrl+V в iced 0.13 `text_input` под Wayland — тупик, поэтому выделение тянется
/// по требованию: сначала [`iced::clipboard::read()`], затем читалки
/// `wl-paste` / `xclip` / `xsel` из [`crate::net::read_clipboard`]. Текст попадает
/// в поле своей вкладки, и вход этой же вкладки сразу отправляется.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PasteTarget {
    Telegram,
    Vk,
    Yandex,
    Google,
    /// Прямой токен сессии Anixart.
    Token,
}

impl PasteTarget {
    /// Все вкладки со вставкой, в порядке пилюль.
    pub const ALL: [PasteTarget; 5] = [
        PasteTarget::Telegram,
        PasteTarget::Vk,
        PasteTarget::Yandex,
        PasteTarget::Google,
        PasteTarget::Token,
    ];

    /// Поле, в которое пишется текст буфера.
    pub fn field(self, login: &mut Login) -> &mut String {
        match self {
            PasteTarget::Telegram => &mut login.telegram_paste,
            PasteTarget::Vk => &mut login.vk_paste,
            PasteTarget::Yandex => &mut login.yandex_paste,
            PasteTarget::Google => &mut login.google_paste,
            PasteTarget::Token => &mut login.token,
        }
    }

    /// Вкладка, которой принадлежит поле — для подписи в сообщениях об ошибке.
    pub fn method(self) -> LoginMethod {
        match self {
            PasteTarget::Telegram => LoginMethod::Telegram,
            PasteTarget::Vk => LoginMethod::Vk,
            PasteTarget::Yandex => LoginMethod::Yandex,
            PasteTarget::Google => LoginMethod::Google,
            PasteTarget::Token => LoginMethod::Token,
        }
    }
}

/// Состояние экрана входа.
#[derive(Debug)]
pub struct Login {
    pub method: LoginMethod,
    pub loading: bool,

    // Пароль
    pub username: String,
    pub password: String,

    // Telegram
    pub telegram_verifier: String,
    pub telegram_paste: String,

    // VK
    pub vk_verifier: String,
    pub vk_paste: String,

    // Yandex
    pub yandex_paste: String,

    // Google — client id и Firebase API key приходят уже вписанными значениями,
    // которые возит с собой APK, поэтому входу через браузер не нужна никакая
    // подготовка.
    pub google_client_id: String,
    pub firebase_api_key: String,
    pub google_redirect: String,
    pub google_paste: String,

    // Прямой токен
    pub token: String,
}

impl Default for Login {
    /// Всё пустое, кроме двух идентификаторов Google / Firebase — те берутся из
    /// констант APK (`docs/oauth.md` §4). Очищенное поле снова падает обратно на
    /// вшитую константу, поэтому кнопка одного клика никогда не остаётся без
    /// материала.
    fn default() -> Self {
        Self {
            method: LoginMethod::default(),
            loading: false,
            username: String::new(),
            password: String::new(),
            telegram_verifier: String::new(),
            telegram_paste: String::new(),
            vk_verifier: String::new(),
            vk_paste: String::new(),
            yandex_paste: String::new(),
            google_client_id: oauth::GOOGLE_WEB_CLIENT_ID.to_owned(),
            firebase_api_key: oauth::FIREBASE_API_KEY.to_owned(),
            google_redirect: String::new(),
            google_paste: String::new(),
            token: String::new(),
        }
    }
}

impl Login {
    /// Authorize URL Telegram для текущего материала PKCE; генерирует и запоминает
    /// свежий verifier, когда его нет.
    pub fn prepare_telegram(&mut self) -> String {
        if self.telegram_verifier.is_empty() {
            self.telegram_verifier = match oauth::generate_pkce() {
                Ok(pkce) => pkce.verifier,
                Err(_) => random_verifier(),
            };
        }
        let challenge = oauth::code_challenge(&self.telegram_verifier);
        oauth::telegram_authorize_url(
            oauth::TELEGRAM_CLIENT_ID,
            oauth::TELEGRAM_REDIRECT_URI,
            oauth::TELEGRAM_SCOPE,
            &challenge,
        )
    }

    /// Authorize URL VK ID (PKCE), генерируя verifier при необходимости.
    ///
    /// SDK провайдера добавляет несколько своих параметров и сам делает обмен кода,
    /// поэтому этот URL — удобство: backend просит итоговый `access_token`.
    pub fn prepare_vk(&mut self) -> String {
        if self.vk_verifier.is_empty() {
            self.vk_verifier = match oauth::generate_pkce() {
                Ok(pkce) => pkce.verifier,
                Err(_) => random_verifier(),
            };
        }
        let challenge = oauth::code_challenge(&self.vk_verifier);
        let state: String = self.vk_verifier.chars().take(8).collect();
        oauth::vk_authorize_url(
            oauth::VK_CLIENT_ID,
            oauth::VK_REDIRECT_URI,
            &challenge,
            &state,
            oauth::VK_SCOPE,
        )
    }

    /// Authorize URL Yandex ID иммитуого потока.
    ///
    /// Редирект привязан к Android-пакету (`yx<client_id>.oauth.yandex.ru/auth/
    /// finish?platform=android`), поэтому в браузере он приземляется на страницу,
    /// которую нельзя открыть как приложение, — и `access_token` остаётся только
    /// скопировать из адресной строки. `state` берётся из первых восьми символов
    /// случайного verifier'а: он нужен провайдеру, но не участвует в обмене.
    pub fn prepare_yandex(&mut self) -> String {
        let host = oauth::yandex_host_for_language(YANDEX_LANGUAGE);
        let state = random_verifier();
        oauth::yandex_authorize_url(oauth::YANDEX_CLIENT_ID, &host, &state)
    }

    /// Authorize URL Google иммитуого потока для **ручного** flow со вставкой
    /// (кнопка одного клика использует вместо него loopback-слушатель).
    ///
    /// `response_type=token id_token` просит у Google оба credencila, поэтому URL,
    /// который вставляют обратно, несёт `id_token` — либо `access_token`, когда
    /// client id не включён для ID token. Оба меняются через Firebase прежде, чем
    /// дойдут до `auth/google`.
    pub fn prepare_google(&self) -> String {
        format!(
            "{}?client_id={}&redirect_uri={}&response_type=token%20id_token&scope={}&nonce={}",
            oauth::GOOGLE_OAUTH_AUTH_URL,
            urlencoding::encode(&self.effective_google_client_id()),
            urlencoding::encode(&pick(&self.google_redirect, GOOGLE_MANUAL_REDIRECT)),
            urlencoding::encode(oauth::GOOGLE_SCOPE),
            urlencoding::encode(&random_verifier()),
        )
    }

    /// Google OAuth client id для браузерных flow: вписанный, когда он непустой,
    /// иначе — тот, что возит APK.
    pub fn effective_google_client_id(&self) -> String {
        pick(&self.google_client_id, oauth::GOOGLE_WEB_CLIENT_ID)
    }

    /// Firebase API key, с которым делается обмен `accounts:signInWithIdp`, —
    /// по умолчанию тот, что возит APK.
    pub fn effective_firebase_api_key(&self) -> String {
        pick(&self.firebase_api_key, oauth::FIREBASE_API_KEY)
    }

    /// Credencial для flow со вставкой, если он есть.
    pub fn telegram_credential(&self) -> Option<TelegramPaste> {
        classify_telegram_paste(&self.telegram_paste)
    }

    pub fn vk_credential(&self) -> Option<String> {
        vk_access_token(&self.vk_paste)
    }

    /// Yandex OAuth access token: из редиректа с `access_token=` или из голого
    /// вставленного значения — тот же разбор, что у VK ID.
    pub fn yandex_credential(&self) -> Option<String> {
        vk_access_token(&self.yandex_paste)
    }

    /// Экран входа: заголовок, ряд пилюль способов, форма способа и гостевой
    /// пропуск внизу.
    pub fn view(&self) -> Element<'_, Message> {
        let current = self.method;
        let pills = LoginMethod::ALL.iter().fold(Row::new().spacing(8), |row, method| {
            row.push(widgets::pill(
                method.label().to_owned(),
                *method == current,
                Message::LoginTab(*method),
            ))
        });

        let form = match current {
            LoginMethod::Password => self.password_form(),
            LoginMethod::Telegram => self.telegram_form(),
            LoginMethod::Vk => self.vk_form(),
            LoginMethod::Yandex => self.yandex_form(),
            LoginMethod::Google => self.google_form(),
            LoginMethod::Token => self.token_form(),
        };

        let body = Column::new()
            .spacing(14)
            .push(widgets::headline("Anixart".to_owned()))
            .push(widgets::muted(
                "Войдите, чтобы увидеть свои закладки, историю серий и профиль. \
                 Каталог и лента читаются и без входа."
                    .to_owned(),
            ))
            .push(widgets::section("Войти с помощью".to_owned(), pills.wrap()))
            .push(form)
            .push(widgets::divider())
            .push(
                Row::new()
                    .spacing(10)
                    .align_y(alignment::Vertical::Center)
                    .push(widgets::action(
                        "Продолжить без входа",
                        (!self.loading).then_some(Message::GuestSkip),
                    ))
                    .push(widgets::muted(
                        "Гость видит «Главную» и «Обзор»; закладки и профиль \
                         останутся закрытыми до входа."
                            .to_owned(),
                    )),
            );

        // `Scrollable` 0.13 не знает `max_width`: ширину формы задаёт
        // обёртка, а прокрутка остаётся по высоте.
        container(scrollable(body).height(Length::Fill))
            .width(Length::Fill)
            .max_width(620)
            .center_x(Length::Fill)
            .padding(20)
            .into()
    }

    fn password_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::muted(
                "Логин или e-mail и пароль профиля Anixart — запрос уходит на \
                 auth/signin."
                    .to_owned(),
            ))
            .push(self.field("Логин или e-mail", &self.username, Message::LoginUsername))
            .push(self.secret("Пароль", &self.password, Message::LoginPassword))
            .push(self.submit("Войти", Message::LoginSubmit))
            .into()
    }

    fn telegram_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::note(
                "Ctrl+V в этом окне не работает (iced 0.13 под Wayland): подтвердите \
                 вход в Telegram, скопируйте весь URL из адресной строки — \
                 app1593901251-login.tg.dev/tglogin?code=… — и нажмите «Вставить и \
                 войти». Кнопка «Вернуться в приложение» нужна только Android, на \
                 десктопе она ничего не делает; адресная строка — всё, что нужно."
                    .to_owned(),
                widgets::Tone::Warn,
            ))
            .push(widgets::muted(
                "1. Откройте вход Telegram в браузере и разрешите приложению доступ. \
                 2. После подтверждения браузер попадёт на \
                 https://app1593901251-login.tg.dev/tglogin?code=… — даже если \
                 страница выглядит пустой, не загружается или сразу перенаправляет \
                 дальше, в адресной строке есть нужное. \
                 3. Вставьте это ниже: подходит весь URL или только часть ?code=… / \
                 code=… — код меняется на id_token с verifier'ом PKCE, который \
                 сгенерировало это окно. Готовый id_token Telegram тоже принимается."
                    .to_owned(),
            ))
            .push(self.wide("Открыть вход Telegram в браузере", Message::LoginTelegramOpen))
            .push(self.field(
                "URL редиректа, ?code=… или id_token Telegram",
                &self.telegram_paste,
                Message::LoginTelegramPaste,
            ))
            .push(self.paste_button(PasteTarget::Telegram))
            .push(self.submit("Продолжить с Telegram", Message::LoginTelegramSubmit))
            .into()
    }

    fn vk_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::note(
                "Ctrl+V в этом окне не работает (iced 0.13 под Wayland): скопируйте \
                 URL редиректа в браузере и нажмите «Вставить и войти»."
                    .to_owned(),
                widgets::Tone::Warn,
            ))
            .push(widgets::muted(
                "Откройте VK ID в браузере и вставьте URL, на который он перенаправит \
                 (в нём есть access_token=…), или токен доступа VK ID со скоупом \
                 email, полученный в любом клиенте VK ID."
                    .to_owned(),
            ))
            .push(self.wide("Открыть вход VK ID в браузере", Message::LoginVkOpen))
            .push(self.field(
                "URL редиректа или токен доступа VK",
                &self.vk_paste,
                Message::LoginVkPaste,
            ))
            .push(self.paste_button(PasteTarget::Vk))
            .push(self.submit("Продолжить с VK", Message::LoginVkSubmit))
            .into()
    }

    fn yandex_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::note(
                "Ctrl+V в этом окне не работает (iced 0.13 под Wayland): скопируйте \
                 адресную строку после подтверждения в Yandex ID и нажмите \
                 «Вставить и войти»."
                    .to_owned(),
                widgets::Tone::Warn,
            ))
            .push(widgets::muted(
                "Yandex ID отдаёт токен имплицитным потоком (`response_type=token`), \
                 а его redirect URI привязан к Android-пакету вида \
                 `yxa980f005….oauth.yandex.ru/auth/finish`, поэтому браузер \
                 приземляется туда без полезной страницы. Скопируйте весь URL — в \
                 нём будет `access_token=…` — или вставьте сам токен напрямую; оба \
                 варианта уходят в auth/yandex."
                    .to_owned(),
            ))
            .push(self.wide("Открыть вход Yandex ID в браузере", Message::LoginYandexOpen))
            .push(self.field(
                "URL редиректа или access_token Yandex",
                &self.yandex_paste,
                Message::LoginYandexPaste,
            ))
            .push(self.paste_button(PasteTarget::Yandex))
            .push(self.submit("Продолжить с Yandex", Message::LoginYandexSubmit))
            .into()
    }

    fn google_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::muted(
                "Достаточно одного клика — ничего искать не нужно. Client id Google \
                 и Firebase API key ниже — те самые, что возит с собой APK Anixart. \
                 «Войти через Google (браузер)» слушает локальный порт, открывает \
                 Google в браузере, забирает credencial из редиректа, на который тот \
                 приземлится, меняет его на Firebase ID token (identitytoolkit \
                 accounts:signInWithIdp) и входит через auth/google сам."
                    .to_owned(),
            ))
            .push(self.wide_accent(
                "Войти через Google (браузер)",
                (!self.loading).then_some(Message::LoginGoogleBrowser),
            ))
            .push(widgets::note(
                "Запасной путь: вставьте URL редиректа (в нём id_token=… или \
                 access_token=…) или любой токен Google / Firebase. Голый JWT Google \
                 или ya29… меняется через Firebase сам, если Anixart его отвергнет. \
                 Два идентификатора ниже правьте только тогда, когда вшитые \
                 перестанут работать."
                    .to_owned(),
                widgets::Tone::Info,
            ))
            .push(self.field(
                "Google OAuth client id (вписан из APK)",
                &self.google_client_id,
                Message::LoginGoogleClientId,
            ))
            .push(self.field(
                "Firebase API key (вписан из APK)",
                &self.firebase_api_key,
                Message::LoginFirebaseKey,
            ))
            .push(self.field(
                "Redirect URI ручного flow (необязательно, по умолчанию http://localhost)",
                &self.google_redirect,
                Message::LoginGoogleRedirect,
            ))
            .push(self.wide("Открыть Google в браузере (вручную, со вставкой)", Message::LoginGoogleOpen))
            .push(self.field(
                "URL редиректа или (Firebase) id_token",
                &self.google_paste,
                Message::LoginGooglePaste,
            ))
            .push(self.paste_button(PasteTarget::Google))
            .push(self.submit("Продолжить с Google", Message::LoginGoogleSubmit))
            .into()
    }

    fn token_form(&self) -> Element<'_, Message> {
        Column::new()
            .spacing(10)
            .push(widgets::muted(
                "Вставьте существующий токен сессии Anixart (profileToken.token) — \
                 вход произойдёт мгновенно, без запроса. Он сохранится в \
                 ~/.config/anixart-linux/session.json и будет использован при \
                 следующем запуске."
                    .to_owned(),
            ))
            .push(self.field("Токен сессии Anixart", &self.token, Message::LoginTokenPaste))
            .push(self.paste_button(PasteTarget::Token))
            .push(self.submit("Использовать этот токен", Message::LoginTokenSubmit))
            .into()
    }

    /// Округлое текстовое поле в стиле темы.
    fn field(&self, placeholder: &str, value: &str, on_input: fn(String) -> Message) -> Element<'_, Message> {
        text_input(placeholder, value)
            .on_input(on_input)
            .padding(12)
            .style(theme::styles::input)
            .into()
    }

    /// То же, но Enter отправляет вход.
    fn secret(
        &self,
        placeholder: &str,
        value: &str,
        on_input: fn(String) -> Message,
    ) -> Element<'_, Message> {
        text_input(placeholder, value)
            .secure(true)
            .on_input(on_input)
            .on_submit(Message::LoginSubmit)
            .padding(12)
            .style(theme::styles::input)
            .into()
    }

    /// Широкая кнопка на всю ширину формы.
    fn wide(&self, caption: &str, message: Message) -> Element<'_, Message> {
        button(widgets::button_label(caption, 15))
            .width(Length::Fill)
            .padding([12, 18])
            .style(theme::styles::ghost)
            .on_press_maybe((!self.loading).then_some(message))
            .into()
    }

    /// Широкая акцентная кнопка — главный клик вкладки.
    fn wide_accent(&self, caption: &str, message: Option<Message>) -> Element<'_, Message> {
        button(widgets::button_label(caption, 15))
            .width(Length::Fill)
            .padding([12, 18])
            .style(theme::styles::accent)
            .on_press_maybe(message)
            .into()
    }

    /// Главная кнопка вкладки: во время входа она становится неактивной подписью.
    fn submit(&self, caption: &str, message: Message) -> Element<'_, Message> {
        if self.loading {
            return widgets::busy("Вход выполняется…".to_owned());
        }
        button(widgets::button_label(caption, 15))
            .width(Length::Fill)
            .padding([12, 18])
            .style(theme::styles::accent)
            .on_press(message)
            .into()
    }

    /// **«Вставить и войти»** — читает выделение и сразу отправляет эту вкладку,
    /// поэтому клавиатурная вставка больше не нужна. Во время входа бездействует,
    /// как и [`Login::submit`].
    fn paste_button(&self, target: PasteTarget) -> Element<'_, Message> {
        button(widgets::button_label(PASTE_BUTTON, 14))
            .width(Length::Fill)
            .padding([10, 18])
            .style(theme::styles::pill)
            .on_press_maybe((!self.loading).then_some(Message::LoginClipboard(target)))
            .into()
    }
}

/// Восстанавливает `https://`, которое адресная строка браузера скрывает, чтобы URL,
/// скопированный без схемы, всё же разбирался. Голый токен или одиночная часть
/// `?code=…` / `code=…` остаются ровно теми, чем пришли.
fn with_scheme(input: &str) -> String {
    let input = input.trim();
    if input.contains("://") {
        return input.to_owned();
    }

    // URL — только ввод формы `host/path`: в хосте есть точка, и после него что-то
    // следует.
    let host = input.split(['/', '?', '#']).next().unwrap_or(input);
    if input.contains(['/', '?', '#']) && host.contains('.') {
        return format!("https://{input}");
    }
    input.to_owned()
}

/// Отличает вставленный URI редиректа от голого Telegram `id_token`.
pub fn classify_telegram_paste(input: &str) -> Option<TelegramPaste> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    // `tg://login?code=…` и https-редирект считаются URI редиректа.
    if input.contains("://") || input.contains("code=") {
        return Some(TelegramPaste::Redirect(with_scheme(input)));
    }
    Some(TelegramPaste::IdToken(input.to_owned()))
}

/// Вытаскивает access token из вставленного URL редиректа или из самого токена.
///
/// Работает и для VK ID, и для Yandex ID: оба провайдера отдают иммитуый или
/// code-редирект, у которого токен лежит в `access_token=` (в query или в
/// фрагменте), а голый ввод — это уже сам токен.
pub fn vk_access_token(input: &str) -> Option<String> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    // URL из адресной строки может быть без схемы; голый трогать нельзя.
    let input = if input.contains("access_token=") {
        with_scheme(input)
    } else {
        input.to_owned()
    };
    if input.contains("://") {
        return param_in_url(&input, "access_token");
    }
    Some(input)
}

/// Вытаскивает Google / Firebase ID token из URL редиректа, голого фрагмента
/// (`#id_token=…`) или из самого токена.
pub fn google_id_token(input: &str) -> Option<String> {
    google_credential_from(input).map(|(token, _)| token)
}

/// Разбирает всё, что породил Google-flow, на credencial для Firebase плюс ключ
/// `postBody`, в котором он пришёл.
///
/// Принимаются: полный URL редиректа (query *или* фрагмент), одиночный фрагмент /
/// query, который отдал loopback-релей, и голый токен — JWT из трёх сегментов
/// считается уже подписанным ID token, всё остальное (`ya29.…`) — OAuth access
/// token.
///
/// `None`, когда ввод несёт только ошибку OAuth или не несёт credencial вовсе.
pub fn google_credential_from(input: &str) -> Option<(String, bool)> {
    let input = input.trim();
    if input.is_empty() {
        return None;
    }
    let lookup = |key: &str| -> Option<String> {
        if input.contains("://") {
            param_in_url(input, key)
        } else {
            param_in_pairs(input, key)
        }
    };
    if let Some(token) = lookup("id_token") {
        return Some((token, false));
    }
    if let Some(token) = lookup("access_token") {
        return Some((token, true));
    }
    // Страница, у которой только `error=…` (или ничего), — не credencial.
    if input.contains("error=") || (input.contains("://") && !input.contains('=')) {
        return None;
    }
    Some((
        input.to_owned(),
        oauth::google_token_is_access_token(input),
    ))
}

/// Причина, по которой Google отверг вход, из редиректа колбэка
/// (`#error=access_denied&error_description=…`). `None` на успешной посадке.
pub fn google_callback_error(payload: &str) -> Option<String> {
    let payload = payload.trim();
    if payload.is_empty() {
        return None;
    }
    let lookup = |key: &str| -> Option<String> {
        if payload.contains("://") {
            param_in_url(payload, key)
        } else {
            param_in_pairs(payload, key)
        }
    };
    lookup("error_description").or_else(|| lookup("error"))
}

/// `value` без пробелов, либо `fallback`, когда поле пусто — так очищенный
/// идентификатор всё равно даёт рабочий браузерный flow.
fn pick(value: &str, fallback: &str) -> String {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        fallback.to_owned()
    } else {
        trimmed.to_owned()
    }
}

/// Читает `key` из голого query / фрагмента (`#id_token=…&state=…`, `?code=…`,
/// `code=…`), собрав его обратно как URL.
fn param_in_pairs(pairs: &str, key: &str) -> Option<String> {
    let query = pairs.trim_start_matches(['#', '?', '&']);
    let url = reqwest::Url::parse(&format!("https://anixart.invalid/?{query}")).ok()?;
    let (_, value) = url.query_pairs().find(|(name, _)| name == key)?;
    Some(value.into_owned())
}

/// Читает `key` из query, падая обратно на фрагмент URL (иммитуый OAuth flow носит
/// параметры там).
fn param_in_url(url: &str, key: &str) -> Option<String> {
    let parsed = reqwest::Url::parse(url).ok()?;
    if let Some((_, value)) = parsed.query_pairs().find(|(name, _)| name == key) {
        return Some(value.into_owned());
    }
    let fragment = parsed.fragment()?.trim_start_matches('?');
    let rebuilt = reqwest::Url::parse(&format!("https://anixart.invalid/?{fragment}")).ok()?;
    let (_, value) = rebuilt.query_pairs().find(|(name, _)| name == key)?;
    Some(value.into_owned())
}

/// Запасной материал PKCE / nonce OAuth, когда обёртка над ОС-RNG недоступна.
///
/// В часы атомический счётчик, чтобы два вызова в одну наносекунду всё равно
/// различались.
pub(crate) fn random_verifier() -> String {
    use std::sync::atomic::{AtomicU64, Ordering};

    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    static COUNTER: AtomicU64 = AtomicU64::new(0);

    let seed = match std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH) {
        Ok(delta) => delta.as_secs() ^ (u64::from(delta.subsec_nanos()) << 32),
        Err(_) => 0x2545_F491_4F6C_DD1D,
    };
    let tick = COUNTER.fetch_add(1, Ordering::Relaxed);
    let mut state = (seed ^ tick.wrapping_mul(0x9E37_79B9_7F4A_7C15)) | 1;

    let mut out = String::with_capacity(43);
    for _ in 0..43 {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        out.push(ALPHABET[(state % 64) as usize] as char);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn methods_cover_the_auth_api() {
        let labels: Vec<&str> = LoginMethod::ALL.iter().map(|m| m.as_str()).collect();
        assert_eq!(labels, ["password", "telegram", "vk", "yandex", "google", "token"]);
        assert_eq!(LoginMethod::default(), LoginMethod::Password);

        // каждая пилюля подписана по-русски или именем провайдера
        let captions: Vec<&str> = LoginMethod::ALL.iter().map(|m| m.label()).collect();
        assert_eq!(captions, ["Пароль", "Telegram", "VK", "Yandex", "Google", "Токен"]);
    }

    #[test]
    fn yandex_authorize_url_is_the_implicit_flow_on_the_ru_host() {
        let mut login = Login::default();
        let url = login.prepare_yandex();

        assert!(
            url.starts_with("https://oauth.yandex.ru/authorize?response_type=token"),
            "{url}"
        );
        assert!(url.contains(&format!("client_id={}", oauth::YANDEX_CLIENT_ID)), "{url}");
        assert!(url.contains("redirect_uri=https%3A%2F%2Fyx"), "{url}");
        assert!(url.contains("platform%3Dandroid"), "{url}");
        assert!(url.contains("&force_confirm=true&origin=android"), "{url}");

        // state обязателен провайдеру и отличается от запуска к запуску
        assert!(url.contains("&state="), "{url}");
        let again = login.prepare_yandex();
        assert_ne!(url, again);
    }

    #[test]
    fn yandex_token_comes_from_the_redirect_or_the_paste() {
        assert_eq!(
            vk_access_token("https://yxa980f005.oauth.yandex.ru/auth/finish?platform=android"),
            None,
            "a redirect without a token is not a credential"
        );
        assert_eq!(
            vk_access_token(
                "https://yxa980f005.oauth.yandex.ru/auth/finish?platform=android&access_token=YAN.123&expires_in=3600"
            )
            .as_deref(),
            Some("YAN.123")
        );
        assert_eq!(
            vk_access_token("yxa980f005.oauth.yandex.ru/auth/finish?access_token=FRA").as_deref(),
            Some("FRA"),
            "the address bar hides the scheme"
        );
        assert_eq!(vk_access_token("ya29.plain").as_deref(), Some("ya29.plain"));
        assert_eq!(vk_access_token(""), None);

        let mut login = Login::default();
        login.yandex_paste = "https://yxa980f005.oauth.yandex.ru/auth/finish?access_token=TOK".into();
        assert_eq!(login.yandex_credential().as_deref(), Some("TOK"));
        login.yandex_paste = "  ".into();
        assert_eq!(login.yandex_credential(), None);
    }

    #[test]
    fn telegram_redirect_is_detected() {
        let url = "https://app1593901251-login.tg.dev/tglogin?code=abc&state=1";
        assert_eq!(
            classify_telegram_paste(url),
            Some(TelegramPaste::Redirect(url.to_string()))
        );
        assert_eq!(
            classify_telegram_paste("tg://login?code=abc"),
            Some(TelegramPaste::Redirect("tg://login?code=abc".into()))
        );
    }

    #[test]
    fn bare_jwt_is_an_id_token() {
        let jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig";
        assert_eq!(classify_telegram_paste(jwt), Some(TelegramPaste::IdToken(jwt.into())));
        assert_eq!(classify_telegram_paste("   "), None);
    }

    #[test]
    fn vk_token_comes_from_query_or_paste() {
        assert_eq!(
            vk_access_token("https://vk.ru/?foo=1&access_token=VK123&expires=7200").as_deref(),
            Some("VK123")
        );
        assert_eq!(
            vk_access_token("vk54701760://vk.ru/blank.html#access_token=FRA&state=s").as_deref(),
            Some("FRA")
        );
        assert_eq!(vk_access_token("PLAIN").as_deref(), Some("PLAIN"));
        assert_eq!(vk_access_token(""), None);
        assert_eq!(vk_access_token("https://vk.ru/blank.html"), None);
    }

    #[test]
    fn google_prefers_id_token_then_access_token() {
        assert_eq!(
            google_id_token("https://localhost#id_token=GAP.eyJhbGciOiGS.access").as_deref(),
            Some("GAP.eyJhbGciOiGS.access")
        );
        assert_eq!(
            google_id_token("https://localhost/?access_token=FB").as_deref(),
            Some("FB")
        );
        assert_eq!(google_id_token("ey.abc.def").as_deref(), Some("ey.abc.def"));
        assert_eq!(google_id_token(""), None);
        assert_eq!(google_id_token("https://localhost/"), None);
    }

    #[test]
    fn google_credentials_carry_their_postbody_kind() {
        // Подписанный редирект — это id_token → `postBody: id_token=…`.
        assert_eq!(
            google_credential_from("https://localhost/#id_token=GOOG.JWT.tok&expires_in=3600"),
            Some(("GOOG.JWT.tok".to_owned(), false))
        );
        // Вернулся только access token → `postBody: access_token=…`.
        assert_eq!(
            google_credential_from("#access_token=ya29.a0AfB&token_type=Bearer"),
            Some(("ya29.a0AfB".to_owned(), true))
        );
        // Loopback-релей постит фрагмент без ведущего `#`.
        assert_eq!(
            google_credential_from("id_token=FB.JWT.tok&scope=openid%20email"),
            Some(("FB.JWT.tok".to_owned(), false))
        );
        assert_eq!(
            google_credential_from("?access_token=ya29.abc"),
            Some(("ya29.abc".to_owned(), true))
        );
        // Голые токены классифицируются по форме.
        assert_eq!(
            google_credential_from("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig"),
            Some(("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.sig".to_owned(), false))
        );
        assert_eq!(
            google_credential_from("ya29.a0AfB_byC"),
            Some(("ya29.a0AfB_byC".to_owned(), true))
        );
        // Ошибка OAuth — не credencial.
        assert_eq!(
            google_credential_from("https://localhost/#error=access_denied&error_description=User%20cancelled"),
            None
        );
        assert_eq!(google_credential_from("#error=access_denied"), None);
        assert_eq!(google_credential_from("   "), None);
    }

    #[test]
    fn google_callback_errors_are_read_from_query_or_fragment() {
        assert_eq!(
            google_callback_error(
                "https://localhost/#error=access_denied&error_description=User%20cancelled"
            )
            .as_deref(),
            Some("User cancelled")
        );
        assert_eq!(
            google_callback_error("#error=invalid_request").as_deref(),
            Some("invalid_request")
        );
        assert_eq!(
            google_callback_error("?error=access_denied").as_deref(),
            Some("access_denied")
        );
        assert_eq!(google_callback_error("#id_token=FB.JWT.tok"), None);
        assert_eq!(google_callback_error(""), None);
    }

    #[test]
    fn google_identifiers_default_to_the_apk_values() {
        let mut login = Login::default();
        // Уже вписаны из `anixart_core::oauth`, поэтому токен искать не нужно.
        assert_eq!(login.google_client_id, oauth::GOOGLE_WEB_CLIENT_ID);
        assert_eq!(login.firebase_api_key, oauth::FIREBASE_API_KEY);

        let url = login.prepare_google();
        assert!(
            url.starts_with("https://accounts.google.com/o/oauth2/v2/auth?client_id=983926366374"),
            "{url}"
        );
        assert!(url.contains("&redirect_uri=http%3A%2F%2Flocalhost&response_type=token%20id_token"));
        assert!(url.contains("&scope=openid%20email%20profile&nonce="));

        // Очищенное поле падает обратно на вшитую константу.
        login.google_client_id.clear();
        login.firebase_api_key.clear();
        assert_eq!(login.effective_google_client_id(), oauth::GOOGLE_WEB_CLIENT_ID);
        assert_eq!(login.effective_firebase_api_key(), oauth::FIREBASE_API_KEY);
        assert!(login.prepare_google().contains("client_id=983926366374"));

        // Явная подмена сильнее, как и свой redirect URI.
        login.google_client_id = "my-own.client.id".into();
        login.firebase_api_key = "AIzaMyOwnKey".into();
        login.google_redirect = "https://example.invalid/cb".into();
        let url = login.prepare_google();
        assert!(url.contains("client_id=my-own.client.id"));
        assert!(url.contains("redirect_uri=https%3A%2F%2Fexample.invalid%2Fcb"));
        assert_eq!(login.effective_firebase_api_key(), "AIzaMyOwnKey");
    }

    #[test]
    fn telegram_accepts_a_bare_code_paste() {
        // Страница tg.dev часто пуста / перенаправляет, поэтому копируется только
        // query адресной строки — и он обязан работать.
        assert_eq!(
            classify_telegram_paste("?code=abc123&state=xyz"),
            Some(TelegramPaste::Redirect("?code=abc123&state=xyz".into()))
        );
        assert_eq!(
            classify_telegram_paste("code=abc123"),
            Some(TelegramPaste::Redirect("code=abc123".into()))
        );
        let jwt = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature";
        assert_eq!(classify_telegram_paste(jwt), Some(TelegramPaste::IdToken(jwt.into())));
    }

    #[test]
    fn telegram_authorize_url_carries_generated_pkce() {
        let mut login = Login::default();
        let url = login.prepare_telegram();
        assert!(url.starts_with("https://oauth.telegram.org/auth?client_id=8789559054"));
        assert!(url.contains("&code_challenge_method=S256"));
        assert!(!login.telegram_verifier.is_empty());

        // Verifier живёт дальше, поэтому тот же challenge переживает повторное
        // открытие.
        let again = login.prepare_telegram();
        assert_eq!(url, again);

        let challenge = oauth::code_challenge(&login.telegram_verifier);
        assert!(url.contains(&format!("code_challenge={challenge}")));
    }

    #[test]
    fn vk_authorize_url_uses_the_baked_in_client() {
        let mut login = Login::default();
        let url = login.prepare_vk();
        assert!(url.starts_with("https://id.vk.ru/authorize?client_id=54701760"));
        assert!(url.contains("&scope=email"));
        assert!(url.contains("code_challenge="));
    }

    #[test]
    fn fallback_verifier_is_base64url_shaped() {
        let a = random_verifier();
        assert_eq!(a.len(), 43);
        assert!(a.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_'));
        assert_ne!(a, random_verifier());
    }

    #[test]
    fn paste_targets_own_their_tab_field() {
        let mut login = Login::default();
        for target in PasteTarget::ALL {
            let label = target.method().as_str();
            *target.field(&mut login) = format!("clip-{label}");
        }

        assert_eq!(login.telegram_paste, "clip-telegram");
        assert_eq!(login.vk_paste, "clip-vk");
        assert_eq!(login.yandex_paste, "clip-yandex");
        assert_eq!(login.google_paste, "clip-google");
        assert_eq!(login.token, "clip-token");

        // Каждая цель — вкладка со вставкой, пароля среди них нет.
        assert!(PasteTarget::ALL.iter().all(|t| t.method() != LoginMethod::Password));
        assert_eq!(PasteTarget::ALL.len(), 5);
    }

    #[test]
    fn an_address_bar_url_gets_its_scheme_back() {
        // Браузеры прячут `https://`, и именно это попадает в буфер.
        assert_eq!(
            classify_telegram_paste("app1593901251-login.tg.dev/tglogin?code=abc&state=1"),
            Some(TelegramPaste::Redirect(
                "https://app1593901251-login.tg.dev/tglogin?code=abc&state=1".into()
            ))
        );
        assert_eq!(
            vk_access_token("vk.ru/blank.html#access_token=FRA&state=s").as_deref(),
            Some("FRA")
        );
        // Одиночный query или голый токен не должны обрасти хостом.
        assert_eq!(
            classify_telegram_paste("code=abc"),
            Some(TelegramPaste::Redirect("code=abc".into()))
        );
        assert_eq!(vk_access_token("PLAIN").as_deref(), Some("PLAIN"));
        assert_eq!(
            classify_telegram_paste("eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature"),
            Some(TelegramPaste::IdToken(
                "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIn0.signature".into()
            ))
        );
    }

    #[test]
    fn the_clipboard_button_offers_one_caption() {
        assert_eq!(PASTE_BUTTON, "Вставить и войти");
    }
}
