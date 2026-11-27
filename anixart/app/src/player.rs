//! Встроенный экран плеера: `mpv` с управляющим сокетом рядом с окном клиента.
//!
//! Плеер здесь — не внешний мир, а часть приложения. [`Player`] держит запущенный
//! процесс [`Mpv`] и его IPC‑сокет, а [`crate::app::App::route`] пересылает этому
//! процессу команды по тому же принципу, по какому mpv принимает их из терминала:
//! одно соединение Unix‑сокета — одна команда в JSON, один ответ. Отсюда все
//! кнопки экрана: «Пауза / Продолжить», «−10 сек», «+10 сек», «+85 сек» против
//! опенинга, «Предыдущая / Следующая серия» и пилюли качества.
//!
//! ## Как стартует `mpv`
//!
//! Аргументы собирает [`mpv_arguments`]. Три ключевых флага: `--hwdec=auto-safe`
//! (декодирование на видеопротоцессоре, но только проверенными путями),
//! `--keep-open=yes` (последний кадр остаётся на экране, а не пустое окно) и
//! `--input-ipc-server=<сокет>` — тот канал, по которому приходят команды.
//!
//! ## Сокет, а не pipe
//!
//! На Linux это `tokio::net::UnixStream` в `/tmp`. Клиент собирается только под
//! Linux, поэтому двойной протокол (именованные pipes Windows) здесь не нужен:
//! [`spawn_mpv`] возвращает ошибку, если сокет не завёлся, а приложение падает на
//! внешний процесс через [`play_external`].
//!
//! ## Tick
//!
//! Часы и состояние плеера читаются подпиской [`crate::app::App::subscription`]:
//! каждые 500 мс приходит [`Message::PlayerTick`](crate::app::Message::PlayerTick)
//! с [`Player::tick`] — тремя ответами сразу (`time-pos`, `duration`, `pause`).
//! Пока процесс жив, экран обновляет позицию; когда mpv закрывают в своём окне,
//! строка шапки гаснет, а список серий остаётся рабочим — из него можно взять
//! следующую серию, не выходя из плеера.

use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use anixart_parsers::ParsedVideo;
use iced::widget::{button, horizontal_space, progress_bar, Column, Row};
use iced::{alignment, Element, Length};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream;

use crate::app::Message;
use crate::posters::{self, PosterCache};
use crate::release::{self, ReleaseView};
use crate::theme;
use crate::util;
use crate::widgets;

/// Счётчик сокетов этого процесса: путь обязан быть разным у каждого плеера.
static SOCKET_SEQ: AtomicU64 = AtomicU64::new(0);

/// Интервал опроса часов плеера — 500 ms достаточно живо двигает полосу и не
/// будит сокет лишними соединениями.
pub const TICK_INTERVAL: Duration = Duration::from_millis(500);

/// Шаг перемотки кнопками «−10 сек» / «+10 сек».
pub const SEEK_STEP: f64 = 10.0;

/// Шаг «Пропустить опенинг»: типичная опенинг‑вставка в тайтлах — 85 секунд.
pub const SKIP_INTRO: f64 = 85.0;

/// Порядок приоритета качеств: от высшего к низшему — так пилюли выстраиваются
/// на экране, и «1080p» оказывается слева, а не в произвольном месте карты.
const QUALITY_ORDER: [&str; 8] =
    ["2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "Default"];

/// Один запущенный `mpv` с его управляющим сокетом.
#[derive(Debug)]
pub struct Mpv {
    /// Процесс: он жив, пока [`Child::try_wait`] отвечает `Ok(None)`.
    pub child: Child,
    /// Путь `--input-ipc-server`, существующий как Unix‑сокет.
    pub socket: PathBuf,
}

impl Mpv {
    /// Убивает процесс и стирает сокет. Ошибки возвращаются текстом — плеер
    /// мог умереть сам, и это не беда пользователя.
    pub fn shutdown(mut self) -> Option<String> {
        let error = match self.child.kill() {
            Ok(()) => None,
            Err(error) => Some(format!("mpv не остановился: {error}")),
        };
        let _ = std::fs::remove_file(&self.socket);
        error
    }
}

/// Состояние встроенного плеера: что играет и как его достать.
#[derive(Debug, Default)]
pub struct Player {
    /// Сокет и процесс; `None` — плеер не поднят или уже закрыт.
    pub mpv: Option<Mpv>,
    /// Разрешённое видео текущей серии — из него читаются пилюли качества.
    pub video: Option<ParsedVideo>,
    /// Индекс серии в `ReleaseView::episodes`, которую смотрим сейчас.
    pub episode: Option<usize>,
    /// Заголовок окна `mpv` и подпись на экране: «Релиз — Серия».
    pub caption: String,
    /// Название озвучки и видеохостинга — вторая строка шапки.
    pub voiceover: String,
    pub source: String,
    /// Играет ли сейчас (иначе — на паузе).
    pub paused: bool,
    /// Известные позиции и длительность в секундах.
    pub position: f64,
    pub duration: f64,
    /// Последняя отказавшая команда: подсказка в шапке, а не баннер приложения.
    pub last_error: Option<String>,
}

impl Player {
    /// Поднимает `mpv` с IPC и делает его текущим плеером.
    ///
    /// Старый процесс, если он был, убивается до запуска нового: два `mpv` на
    /// одном сокете не помещаются, а второй поверх первого оставил бы сироту.
    pub fn start(
        &mut self,
        video: ParsedVideo,
        caption: &str,
        voiceover: &str,
        source: &str,
        resume: Option<f64>,
    ) -> Result<(), String> {
        if let Some(old) = self.mpv.take() {
            if let Some(error) = old.shutdown() {
                self.last_error = Some(error);
            }
        }

        let mpv = spawn_mpv(&video, caption, resume)?;
        self.mpv = Some(mpv);
        self.video = Some(video);
        self.caption = caption.to_owned();
        self.voiceover = voiceover.to_owned();
        self.source = source.to_owned();
        self.paused = false;
        self.position = resume.unwrap_or(0.0);
        self.duration = 0.0;
        self.last_error = None;
        Ok(())
    }

    /// Жив ли процесс прямо сейчас.
    pub fn alive(&self) -> bool {
        self.mpv.is_some()
    }

    /// Путь сокета для подписки и команд.
    pub fn socket(&self) -> Option<&Path> {
        self.mpv.as_ref().map(|mpv| mpv.socket.as_path())
    }

    /// Пилюля «Пауза / Продолжить»: одно и то же свойство в обе стороны.
    pub fn set_paused(&self, paused: bool) -> Option<Vec<String>> {
        self.socket()?;
        Some(vec![
            "set_property".to_owned(),
            "pause".to_owned(),
            serde_json::json!(paused).to_string(),
        ])
    }

    /// Перемотка на `delta` секунд вперёд или назад.
    pub fn seek(&self, delta: f64) -> Option<Vec<String>> {
        self.socket()?;
        Some(vec![
            "seek".to_owned(),
            format!("{delta}"),
            "relative".to_owned(),
        ])
    }

    /// Смена качества: то же видео, другая ссылка, та же позиция.
    pub fn switch_quality(&self, quality: &str) -> Option<(ParsedVideo, f64)> {
        let video = self.video.as_ref()?;
        let url = video.qualities.get(quality)?;
        let mut next = ParsedVideo::new(video.source);
        next = next.with_quality(quality, url.clone());
        for (name, value) in &video.headers {
            next = next.with_header(name.clone(), value.clone());
        }
        Some((next, self.position))
    }

    /// Опрос часов: три свойства одним соединением.
    ///
    /// `None` — плеер не поднят, подписка в этот такт молчит.
    pub fn tick(&self) -> Option<impl std::future::Future<Output = Result<Vec<Option<serde_json::Value>>, String>>> {
        let socket = self.socket()?.to_owned();
        Some(ipc_multi(
            socket,
            vec![
                vec!["get_property".to_owned(), "time-pos".to_owned()],
                vec!["get_property".to_owned(), "duration".to_owned()],
                vec!["get_property".to_owned(), "pause".to_owned()],
            ],
        ))
    }

    /// Закрыть плеер: убить процесс, стереть сокет, оставить состояние серий.
    pub fn stop(&mut self) {
        if let Some(mpv) = self.mpv.take() {
            if let Some(error) = mpv.shutdown() {
                self.last_error = Some(error);
            }
        }
        self.video = None;
        self.episode = None;
        self.paused = false;
        self.position = 0.0;
        self.duration = 0.0;
    }
}

/// Уникальный путь сокета: `/tmp/anixart-mpv-<pid>-<n>.sock`.
fn socket_path() -> PathBuf {
    let seq = SOCKET_SEQ.fetch_add(1, Ordering::Relaxed);
    let name = format!("anixart-mpv-{}-{seq}.sock", std::process::id());
    std::env::temp_dir().join(name)
}

/// Аргументы `mpv` со встроенным управлением.
///
/// * `--force-window=immediate` — окно показывается до первой декады кадров.
/// * `--no-terminal` + `--really-quiet` — плеер молчит в stderr родителя.
/// * `--hwdec=auto-safe` — аппаратное декодирование, но только проверенными
///   путями: на сетевой ссылке сбойный декодер роняет окно целиком.
/// * `--keep-open=yes` — после последней секунды остаётся кадр и управление,
///   а не пустое окно: отсюда «Следующая серия» работает и на финале.
/// * `--title=<Релиз — Серия>` — узнаваемая подпись в списке окон.
/// * `--input-ipc-server=<сокет>` — канал команд для этого экрана.
/// * `--start=<секунда>` — только при смене качества посреди просмотра.
pub fn mpv_arguments(video: &ParsedVideo, caption: &str, socket: &Path, resume: Option<f64>) -> Result<Vec<String>, String> {
    let url = video
        .best_url()
        .ok_or_else(|| "Ни одной играбельной ссылки не найдено".to_owned())?;

    let mut arguments = vec![
        "--force-window=immediate".to_owned(),
        "--no-terminal".to_owned(),
        "--really-quiet".to_owned(),
        "--hwdec=auto-safe".to_owned(),
        "--keep-open=yes".to_owned(),
    ];
    let title = caption.trim();
    if !title.is_empty() {
        arguments.push(format!("--title={title}"));
    }
    arguments.push(format!("--input-ipc-server={}", socket.display()));
    if let Some(second) = resume {
        if second > 0.0 {
            arguments.push(format!("--start={second}"));
        }
    }
    arguments.push(url.to_owned());
    for (name, value) in &video.headers {
        arguments.push(format!("--http-header-fields={name}: {value}"));
    }
    Ok(arguments)
}

/// Запуск `mpv` с управляющим сокетом.
pub fn spawn_mpv(
    video: &ParsedVideo,
    caption: &str,
    resume: Option<f64>,
) -> Result<Mpv, String> {
    let socket = socket_path();
    let arguments = mpv_arguments(video, caption, &socket, resume)?;

    let mut command = Command::new("mpv");
    command.args(&arguments);
    command.stdin(Stdio::null());

    let child = command
        .spawn()
        .map_err(|error| format!("mpv не запустился: {error}"))?;

    Ok(Mpv { child, socket })
}

/// наружный запуск без IPC — прежний путь, когда сокет не нужен или не заводится.
pub fn play_external(video: &ParsedVideo, caption: &str) -> Result<(), String> {
    let mut command = Command::new("mpv");
    command.args(mpv_arguments(video, caption, &socket_path(), None)?);
    command.stdin(Stdio::null());
    command
        .spawn()
        .map_err(|error| format!("mpv не запустился: {error}"))?;
    Ok(())
}

/// Одна управляющая команда в сокет mpv.
pub async fn ipc(
    socket: PathBuf,
    command: Vec<String>,
) -> Result<Option<serde_json::Value>, String> {
    let mut values = ipc_multi(socket, vec![command]).await?;
    Ok(values.pop().flatten())
}

/// Несколько команд одним соединением: mpv отвечает на каждую своей строкой.
///
/// Ответ `i` — это `data[0]` строки `i`: `Some(Value)` значение свойства,
/// `Some(Value::Null)` свойство без значения (например, длительность до первой
/// декады кадров) и `None` строки не пришло вовсе.
async fn ipc_multi(
    socket: PathBuf,
    commands: Vec<Vec<String>>,
) -> Result<Vec<Option<serde_json::Value>>, String> {
    let mut stream = UnixStream::connect(&socket)
        .await
        .map_err(|error| format!("сокет mpv недоступен ({error})"))?;

    let mut request = String::new();
    for command in &commands {
        request.push_str(&encode_command(command)?);
        request.push('\n');
    }

    stream
        .write_all(request.as_bytes())
        .await
        .map_err(|error| format!("команда не дошла до mpv: {error}"))?;
    let _ = stream.shutdown().await;

    // mpv закрывает соединение после ответа, но чтение всё равно ограничено:
    // зависший сокет не должен будить экран бесконечным ожиданием.
    let mut buffer = Vec::new();
    let read = tokio::time::timeout(Duration::from_secs(2), stream.read_to_end(&mut buffer));
    match read.await {
        Ok(Ok(_)) => {}
        Ok(Err(error)) => return Err(format!("ответ mpv не прочитан: {error}")),
        Err(_) => return Err("mpv молчит в ответ на команду".to_owned()),
    }

    let mut replies: Vec<Option<serde_json::Value>> = Vec::with_capacity(commands.len());
    for line in String::from_utf8_lossy(&buffer).lines() {
        if replies.len() == commands.len() {
            break;
        }
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let value: serde_json::Value = match serde_json::from_str(line) {
            Ok(value) => value,
            // «шестерёнка» события тоже приходит в этот поток — просто молча
            // пропускаем строку, которую не смогли разобрать.
            Err(_) => continue,
        };
        replies.push(
            value
                .get("data")
                .and_then(|data| data.as_array())
                .and_then(|data| data.first().cloned()),
        );
    }
    while replies.len() < commands.len() {
        replies.push(None);
    }
    Ok(replies)
}

/// JSON‑строка команды mpv: `["seek", "10", "relative"]` / `["set_property", "pause", true]`.
fn encode_command(arguments: &[String]) -> Result<String, String> {
    let values: Vec<serde_json::Value> = arguments
        .iter()
        .map(|arg| match arg.as_str() {
            "true" => serde_json::Value::Bool(true),
            "false" => serde_json::Value::Bool(false),
            _ => serde_json::Value::String(arg.clone()),
        })
        .collect();
    serde_json::to_string(&values).map_err(|error| format!("команда не кодируется: {error}"))
}

/// Три ответа тика: позиция, длительность, пауза.
///
/// Пропавший ответ не трогает известное: `time-pos` без строки — часы остаются на
/// последнем значении, а вот `duration` без строки — это «серия ещё не досчитана»,
/// и полоса должна быть пустой. Ошибка соединения закрывает плеер, если процесс
/// умер (пользователь нажал «X» в окне mpv), и оставляет подсказку, если жив.
pub fn apply_tick(player: &mut Player, reply: Result<Vec<Option<serde_json::Value>>, String>) {
    match reply {
        Err(error) => {
            if !player.alive() {
                // Штатный конец просмотра: окно mpv закрыли снаружи.
                player.mpv = None;
                player.last_error = None;
            } else {
                player.last_error = Some(error);
            }
        }
        Ok(values) => {
            let mut iter = values.into_iter();
            if let Some(Some(position)) = iter.next() {
                player.position = position.as_f64().unwrap_or(player.position);
            }
            if let Some(Some(duration)) = iter.next() {
                // свойство есть, но значения пока нет (`duration` до первой
                // декады кадров) — полоса становится пустой, а не вральной
                player.duration = duration.as_f64().unwrap_or(0.0);
            }
            if let Some(Some(paused)) = iter.next() {
                player.paused = paused.as_bool().unwrap_or(player.paused);
            }
            player.last_error = None;
        }
    }
}

/// Качество этой ссылки в порядке убывания: «1080p» слева, «360p» справа.
pub fn quality_list(video: &ParsedVideo) -> Vec<String> {
    let mut known: Vec<String> = QUALITY_ORDER
        .iter()
        .filter(|quality| video.qualities.contains_key(**quality))
        .map(|quality| (*quality).to_owned())
        .collect();
    let mut extra: Vec<String> = video
        .qualities
        .keys()
        .filter(|quality| !QUALITY_ORDER.contains(&quality.as_str()))
        .cloned()
        .collect();
    extra.sort();
    known.append(&mut extra);
    known
}

/// Часы в формате `M:SS` или `H:MM:SS` — то, что стоит под полосой.
pub fn time_label(seconds: f64) -> String {
    if !seconds.is_finite() || seconds <= 0.0 {
        return "0:00".to_owned();
    }
    let total = seconds.round() as u64;
    let (hours, minutes, seconds) = (total / 3600, (total % 3600) / 60, total % 60);
    if hours > 0 {
        format!("{hours}:{minutes:02}:{seconds:02}")
    } else {
        format!("{minutes}:{seconds:02}")
    }
}

/// Индекс следующей серии в порядке списка (`None` — за концом).
pub fn neighbour_episode(order: &[usize], current: usize, forward: bool) -> Option<usize> {
    let place = order.iter().position(|index| *index == current)?;
    let next = if forward { place + 1 } else { place.checked_sub(1)? };
    order.get(next).copied()
}

/// Экран плеера: обложка и название релиза, полоса, пилюли качества, управление
/// и список серий — всё в одном окне клиента.
pub fn view<'a>(
    player: &'a Player,
    releases: &'a ReleaseView,
    cache: &'a PosterCache,
    zone_width: f32,
) -> Element<'a, Message> {
    let header = header_block(player, releases, cache);
    widgets::sticky_body(header, zone_width, false, move |_| {
        episode_column(player, releases)
    })
}

/// Прикреплённая шапка: возврат, постер, название, озвучка и источник, полоса,
/// качество, управление.
fn header_block<'a>(
    player: &'a Player,
    releases: &'a ReleaseView,
    cache: &'a PosterCache,
) -> Element<'a, Message> {
    let mut head = Column::new().spacing(10);

    let top = Row::new()
        .spacing(10)
        .align_y(alignment::Vertical::Center)
        .push(
            button(widgets::button_label("← Назад к сериям", 14))
                .padding([8, 16])
                .style(theme::styles::ghost)
                .on_press(Message::PlayerBack),
        )
        .push(widgets::headline_row(player.caption.clone()))
        .push(widgets::toggle_pill(
            if player.paused { "Продолжить" } else { "Пауза" }.to_owned(),
            player.paused,
            Message::PlayerPauseToggle,
        ));
    head = head.push(top);

    // Обложка открытого релиза — тот же постер, что на его странице: прилёт
    // байтов не меняет структуру дерева, только содержимое картинки.
    if let Some(release) = releases.release.as_ref() {
        let title = widgets::release_title(release);
        let poster = widgets::artwork(
            cache,
            &posters::release_key(release.id),
            &title,
            widgets::THUMB_WIDTH,
            widgets::THUMB_HEIGHT,
        );
        let meta = widgets::join_meta(vec![
            player.voiceover.clone(),
            player.source.clone(),
            if player.alive() {
                String::new()
            } else {
                "mpv закрыт".to_owned()
            },
        ]);
        let mut facts = Column::new().spacing(6).width(Length::Fill).push(widgets::muted(
            release
                .title_ru
                .as_deref()
                .map(str::trim)
                .filter(|text| !text.is_empty())
                .unwrap_or(title.as_str())
                .to_owned(),
        ));
        if !meta.is_empty() {
            facts = facts.push(widgets::muted(meta));
        }
        if let Some(error) = &player.last_error {
            facts = facts.push(widgets::muted(format!("Команда не прошла: {error}")));
        }
        head = head.push(
            Row::new()
                .spacing(16)
                .align_y(alignment::Vertical::Top)
                .push(poster)
                .push(facts),
        );
    } else {
        let meta = widgets::join_meta(vec![
            player.voiceover.clone(),
            player.source.clone(),
            if player.alive() {
                String::new()
            } else {
                "mpv закрыт".to_owned()
            },
        ]);
        if !meta.is_empty() {
            head = head.push(widgets::muted(meta));
        }
        if let Some(error) = &player.last_error {
            head = head.push(widgets::muted(format!("Команда не прошла: {error}")));
        }
    }

    // Полоса: известная длительность — делитель, иначе полоса пустая.
    let duration = if player.duration > 0.0 { player.duration as f32 } else { 1.0 };
    let value = (player.position as f32).clamp(0.0, duration);
    head = head.push(
        Row::new()
            .spacing(10)
            .align_y(alignment::Vertical::Center)
            .push(widgets::muted(time_label(player.position)))
            .push(
                progress_bar(0.0..=duration, value)
                    .height(6)
                    .width(Length::Fill),
            )
            .push(widgets::muted(time_label(player.duration))),
    );

    if let Some(video) = &player.video {
        let qualities = quality_list(video);
        if qualities.len() > 1 {
            let pills = qualities.into_iter().fold(Row::new().spacing(8), |row, quality| {
                row.push(widgets::pill(
                    quality.clone(),
                    is_current_quality(video, &quality),
                    Message::PlayerQuality(quality),
                ))
            });
            head = head.push(widgets::section("Качество".to_owned(), pills.wrap()));
        }
    }

    let controls = Row::new()
        .spacing(8)
        .align_y(alignment::Vertical::Center)
        .push(widgets::action("−10 сек", Some(Message::PlayerSeek(-SEEK_STEP))))
        .push(widgets::action("+10 сек", Some(Message::PlayerSeek(SEEK_STEP))))
        .push(widgets::action(
            "Пропустить опенинг (+85 сек)",
            Some(Message::PlayerSeek(SKIP_INTRO)),
        ))
        .push(widgets::action("Предыдущая серия", Some(Message::PlayerPrevious)))
        .push(widgets::action("Следующая серия", Some(Message::PlayerNext)))
        .push(horizontal_space());
    head.push(controls).into()
}

/// Текущее качество — то, чья ссылка равна играемой.
fn is_current_quality(video: &ParsedVideo, quality: &str) -> bool {
    video
        .best_url()
        .map(|url| video.qualities.get(quality).map(|candidate| candidate == url).unwrap_or(false))
        .unwrap_or(false)
}

/// Тело: список серий тем же компактным рядом, что и на экранах источников.
fn episode_column<'a>(player: &'a Player, releases: &'a ReleaseView) -> Column<'a, Message> {
    let mut body = Column::new().spacing(12);

    if releases.episodes.is_empty() {
        return body.push(widgets::busy(
            "Список серий пуст — вернитесь к озвучкам и выберите другой источник".to_owned(),
        ));
    }

    let waiting = releases.pending.is_some();
    let mut rows = Column::new().spacing(6);
    for index in releases.ordered_episodes() {
        let episode = &releases.episodes[index];
        let current = player.episode == Some(index);
        let action = if current && player.alive() {
            if player.paused { "Пауза" } else { "Играет" }
        } else if waiting {
            "Решаю ссылку…"
        } else {
            "Смотреть"
        };

        let on_press = if current && player.alive() {
            Some(Message::PlayerPauseToggle)
        } else {
            (!waiting).then_some(Message::PlayEpisode(index))
        };

        rows = rows.push(release::episode_row(
            episode,
            index,
            current,
            action,
            on_press,
        ));
    }

    let caption = format!(
        "Серии · {} · {}",
        util::count_label(releases.episodes.len() as i64, "эпизод", "эпизода", "эпизодов"),
        releases.episode_order.label()
    );
    body = body.push(widgets::section(caption, rows));
    body
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;

    use anixart_parsers::VideoSource;

    fn video() -> ParsedVideo {
        ParsedVideo::new(VideoSource::Kodik)
            .with_quality("1080p", "https://kodik.example/1080.mp4")
            .with_quality("360p", "https://kodik.example/360.mp4")
            .with_quality("Default", "https://kodik.example/default.mp4")
            .with_header("Referer", "https://kodik.info/")
    }

    #[test]
    fn mpv_starts_with_a_window_hardware_decoding_and_an_ipc_socket() {
        let socket = Path::new("/tmp/anixart-mpv-test.sock");
        let arguments = mpv_arguments(&video(), "Наруто — №12", socket, None).unwrap();

        assert_eq!(arguments[0], "--force-window=immediate");
        assert_eq!(arguments[1], "--no-terminal");
        assert_eq!(arguments[2], "--really-quiet");
        assert_eq!(arguments[3], "--hwdec=auto-safe");
        assert_eq!(arguments[4], "--keep-open=yes");
        assert!(arguments.contains(&"--title=Наруто — №12".to_owned()), "{arguments:?}");
        assert!(
            arguments.contains(&"--input-ipc-server=/tmp/anixart-mpv-test.sock".to_owned()),
            "{arguments:?}"
        );
        assert!(
            arguments.contains(&"https://kodik.example/1080.mp4".to_owned()),
            "лучшее качество играется по умолчанию: {arguments:?}"
        );
        assert!(arguments.contains(&"--http-header-fields=Referer: https://kodik.info/".to_owned()));
        assert!(
            !arguments.iter().any(|argument| argument.starts_with("--start=")),
            "обычный запуск не мотает вперёд: {arguments:?}"
        );

        // пустая подпись не рождает голого `--title=`
        let arguments = mpv_arguments(&video(), "   ", socket, None).unwrap();
        assert!(arguments.iter().all(|argument| !argument.starts_with("--title=")));

        // смена качества продолжает с той же секунды
        let arguments = mpv_arguments(&video(), "Наруто — №12", socket, Some(42.0)).unwrap();
        assert!(arguments.contains(&"--start=42".to_owned()), "{arguments:?}");

        // нулевая позиция — не аргумент
        let arguments = mpv_arguments(&video(), "Наруто — №12", socket, Some(0.0)).unwrap();
        assert!(!arguments.iter().any(|argument| argument.starts_with("--start=")));
    }

    #[test]
    fn mpv_needs_a_resolved_stream() {
        let empty = ParsedVideo::new(VideoSource::Direct);
        let socket = Path::new("/tmp/unused.sock");
        assert!(mpv_arguments(&empty, "Наруто — №1", socket, None).is_err());
        assert!(spawn_mpv(&empty, "Наруто — №1", None).is_err());
    }

    #[test]
    fn sockets_are_unique_per_player() {
        let first = socket_path();
        let second = socket_path();
        assert_ne!(first, second, "каждый плеер получает свой сокет");
        assert!(first.file_name().unwrap().to_string_lossy().contains("anixart-mpv-"));
    }

    #[test]
    fn qualities_are_ordered_from_the_highest_down() {
        let list = quality_list(&video());
        assert_eq!(list, vec!["1080p", "360p", "Default"]);

        // неизвестные подписи не теряются, а идут по алфавиту в конце
        let odd = ParsedVideo::new(VideoSource::Vk)
            .with_quality("zzz", "https://vk.example/z")
            .with_quality("720p", "https://vk.example/h");
        assert_eq!(quality_list(&odd), vec!["720p", "zzz"]);

        assert!(quality_list(&ParsedVideo::new(VideoSource::Direct)).is_empty());
    }

    #[test]
    fn commands_encode_to_mpv_json() {
        let player = Player {
            mpv: Some(Mpv {
                child: spawn_true_process(),
                socket: PathBuf::from("/tmp/anixart-mpv-test.sock"),
            }),
            ..Default::default()
        };

        let seek = player.seek(-10.0).unwrap();
        assert_eq!(encode_command(&seek).unwrap(), r#"["seek","-10","relative"]"#);
        let pause = player.set_paused(true).unwrap();
        assert_eq!(encode_command(&pause).unwrap(), r#"["set_property","pause",true]"#);

        // без поднятого процесса команд нет — и паники тоже
        let idle = Player::default();
        assert!(idle.seek(10.0).is_none());
        assert!(idle.set_paused(false).is_none());
        assert!(idle.tick().is_none());
    }

    /// Процесс‑заглушка: `sleep` нужен только чтобы показать «сокет есть».
    fn spawn_true_process() -> Child {
        Command::new("sleep")
            .arg("30")
            .stdin(Stdio::null())
            .spawn()
            .expect("`sleep` есть в CI")
    }

    #[test]
    fn quality_switch_keeps_headers_and_position() {
        let mut player = Player {
            video: Some(video()),
            position: 123.0,
            ..Default::default()
        };
        let (next, resume) = player.switch_quality("360p").unwrap();
        assert_eq!(next.qualities.get("360p").unwrap(), "https://kodik.example/360.mp4");
        assert_eq!(next.qualities.len(), 1, "переключается одна ссылка");
        assert_eq!(next.headers.get("Referer").unwrap(), "https://kodik.info/");
        assert_eq!(resume, 123.0);

        player.video = None;
        assert!(player.switch_quality("1080p").is_none());
        assert!(Player::default().switch_quality("1080p").is_none());
    }

    #[test]
    fn tick_answers_move_the_clock_and_errors_close_a_dead_player() {
        let mut player = Player {
            position: 10.0,
            ..Default::default()
        };
        let reply = Ok(vec![
            Some(serde_json::json!(95.0)),
            Some(serde_json::json!(1440.0)),
            Some(serde_json::json!(true)),
        ]);
        apply_tick(&mut player, reply);
        assert_eq!(player.position, 95.0);
        assert_eq!(player.duration, 1440.0);
        assert!(player.paused);
        assert!(player.last_error.is_none());

        // пустой ответ — mpv ещё не досчитал серию: часы не прыгают
        let mut half = Player {
            position: 7.0,
            duration: 100.0,
            ..Default::default()
        };
        apply_tick(&mut half, Ok(vec![None, None, Some(serde_json::json!(false))]));
        assert_eq!(half.position, 7.0, "пропавший `time-pos` не обнуляет позицию");
        assert_eq!(half.duration, 100.0, "пропавший `duration` не трогает известные");
        assert!(!half.paused);

        // `duration: null` — свойство есть, но значения пока нет: полоса пуста
        let mut fresh = Player {
            duration: 100.0,
            ..Default::default()
        };
        apply_tick(
            &mut fresh,
            Ok(vec![
                Some(serde_json::json!(0.0)),
                Some(serde_json::Value::Null),
                Some(serde_json::json!(false)),
            ]),
        );
        assert_eq!(fresh.duration, 0.0, "null длится как отсутствие длительности");

        // сокет умер вместе с процессом: подсказки нет, плеер закрыт
        let mut dead = Player::default();
        apply_tick(&mut dead, Err("сокет mpv недоступен".to_owned()));
        assert!(dead.last_error.is_none(), "закрытый пользователем mpv — не ошибка");
        assert!(dead.mpv.is_none());

        // а вот живой процесс с невнятным ответом получает подсказку
        let mut alive = Player {
            mpv: Some(Mpv {
                child: spawn_true_process(),
                socket: PathBuf::from("/tmp/anixart-mpv-test.sock"),
            }),
            ..Default::default()
        };
        apply_tick(&mut alive, Err("mpv молчит в ответ на команду".to_owned()));
        assert_eq!(alive.last_error.as_deref(), Some("mpv молчит в ответ на команду"));
        let _ = alive.mpv.take().map(Mpv::shutdown);
    }

    #[test]
    fn clocks_print_minutes_and_hours_without_leading_zeroes() {
        assert_eq!(time_label(0.0), "0:00");
        assert_eq!(time_label(-5.0), "0:00");
        assert_eq!(time_label(f64::NAN), "0:00");
        assert_eq!(time_label(9.4), "0:09");
        assert_eq!(time_label(65.0), "1:05");
        assert_eq!(time_label(5999.0), "1:39:59");
        assert_eq!(time_label(3600.0), "1:00:00");
    }

    #[test]
    fn neighbours_walk_the_visible_order_not_the_server_one() {
        // экран показывает №1, №2, №3 — индексы вектора [2, 0, 1]
        let order = vec![2, 0, 1];
        assert_eq!(neighbour_episode(&order, 2, true), Some(0));
        assert_eq!(neighbour_episode(&order, 0, true), Some(1));
        assert_eq!(neighbour_episode(&order, 1, true), None, "за концом — ничего");
        assert_eq!(neighbour_episode(&order, 1, false), Some(0));
        assert_eq!(neighbour_episode(&order, 2, false), None, "до начала — ничего");
        assert_eq!(neighbour_episode(&order, 9, true), None, "чужой индекс не паникует");
        assert_eq!(neighbour_episode(&[], 0, true), None);
    }
}
