# Anixart Rust Client / Rust-клиент Anixart

[![Anixart Rust CI](https://github.com/jackson1488/all-project/actions/workflows/anixart-rust.yml/badge.svg)](https://github.com/jackson1488/all-project/actions/workflows/anixart-rust.yml)

Неофициальный десктопный клиент Anixart на Rust. Unofficial Anixart desktop client in Rust.

> **Дисклеймер / Disclaimer.** Проект не аффилирован с Anixart. API изучено реверс-инжинирингом
> Android-приложения (`com.swiftsoft.anixartd`) только в исследовательских целях; справочник —
> [`API.md`](API.md). Не публикуйте токены, учётные данные и APK в этом репозитории.
> Not affiliated with Anixart. The API was obtained by reverse-engineering the Android client
> (`com.swiftsoft.anixartd`) for research purposes only; see [`API.md`](API.md). Never commit
> tokens, credentials or APKs here.

---

## 🇷🇺 Русский

### Обзор

`anixart/` — Cargo-workspace из трёх крейтов: сетевой слой Anixart API, парсеры видео-хостингов
и десктопное приложение на iced. Воспроизведение выполняется **внешним плеером `mpv`** —
`libmpv` не линкуется, поэтому у проекта нет C-зависимостей — именно это делает возможной
экспериментальную сборку на Windows (см. дорожную карту).

Вся компиляция и тесты идут **только в GitHub Actions**. Локально `cargo`, `gradle` и `jadx`
не запускаются — цикл работы агента: `git pull --rebase` → коммит → `git push` →
`~/anixsart/orch/ci_wait.sh` до статуса `CI_GREEN`.

### Архитектура

| Крейт | Тип | Назначение |
|---|---|---|
| [`core/`](core) | `anixart-core` (lib) | Асинхронный клиент Anixart API (`AnixartClient`), модели, ошибки, пагинация, auth-token |
| [`parsers/`](parsers) | `anixart-parsers` (lib) | Embed-URL → готовые m3u8/mp4-ссылки |
| [`app/`](app) | `anixart` (bin) | UI на iced + запуск `mpv` как внешнего процесса |

Зависимости: `core` и `parsers` — `reqwest` (rustls), `tokio`, `serde`, `thiserror`
(плюс `regex` у парсеров и `urlencoding` у клиента); `app` — `iced 0.4` и оба либы.
Workspace: edition 2021, `resolver = "2"`, release-профиль `opt-level = 3`, `panic = "abort"`.

**Поток данных** (см. [`docs/video-sources.md`](docs/video-sources.md)):

```
login/password → AnixartClient::login()             → profile_token → set_token()
releaseId      → client.types(id)                   → типы (озвучка)
               → client.sources(id, type_id)        → источники
               → client.episodes(id, type_id, sid)  → серии с Episode.url
               → parsers::detect_source(url)        → провайдер
               → parsers::resolve(url)              → ParsedVideo { qualities, headers }
               → ParsedVideo::best_url()            → ссылка максимальной доступной версии
               → mpv <url> (+ headers)              → воспроизведение
```

`anixart-core` публично: `AnixartClient` (`login`, `search`, `feed` / `feed_all`, `release`,
`types`, `sources`, `episodes`, `favorites`, `favorite_add` / `favorite_delete`, `history` /
`history_add` / `history_delete`, `profile`, `profile_info`, `collection`, `set_token` /
`clear_token` / `token`), модели `Release`, `Episode`, `EpisodeType`, `Source`, `FeedItem`,
`Profile`, `Collection`, обёртки `Pageable<T>` / `CodeResponse` с константами кодов
(`SUCCESS = 0`, `INVALID_LOGIN = 2`, `BANNED = 402`, …) и ошибка `AnixartError`
(`Http`, `Json`, `Api { code }`, `MissingToken`, `Unexpected`). Токен передаётся **в query**
(`?token=…`), а не в заголовке `Authorization`; эндпоинты, требующие токен, возвращают
`MissingToken` без сетевого запроса. Фикстуры ответов лежат в [`core/fixtures/`](core/fixtures).

`anixart-parsers` публично: `is_direct_stream`, `detect_source`, `resolve`,
`resolve_with_client`, модели `ParsedVideo` / `VideoSource` и ошибка `ParserError`.
`ParsedVideo.qualities` ключи — `Default`, `360p` … `2160p`; `ParsedVideo.headers` —
`Referer` / `User-Agent` / `Cookie`, которые плеер обязан отправить вместе с запросом.

Реализованные провайдеры: **Kodik** (`kodikres.com/api/video-links`), **Sibnet** (скрейпинг
embed + редирект), **VK** (`video.get` + анонимный токен), **RuTube** (`api/play/options` +
разбор m3u8-вариантов), **AniLibria** (HLS по release/episode с fallback на embed) и прямые
`mp4`/`m3u8`. Каждый модуль держит чистые функции разбора (`extract_*`, `parse_*`) отдельно от
сети (`resolve`), поэтому логика проверяется офлайн-фикстурами.

**Статус:** `anixart-core` — готовый асинхронный клиент (`AnixartClient` + модели + фикстуры
ответов), `anixart-parsers` — 5 провайдеров плюс прямые потоки. `app` пока — рабочая форма
логина на iced, которая спавнит `mpv` на тестовом потоке; интеграция с `AnixartClient` — M3/M4.
Тестов: **38** (22 в `parsers` + 16 в `core`, включая 13 serde-фикстур), все офлайн —
сеть в юнит-тестах не нужна.

### Сборка в CI

Workflow: [`.github/workflows/anixart-rust.yml`](../.github/workflows/anixart-rust.yml)
(`Anixart Rust CI (Linux + Windows)`). Запускается на push в `main` по путям `anixart/**` и
самого workflow, либо вручную (`workflow_dispatch`). Обе задачи наследуют
`working-directory: anixart`.

| Задача | Runner | Шаги | Артефакт |
|---|---|---|---|
| `linux` | `ubuntu-latest` | apt: `libxkbcommon-dev`, `mpv` → toolchain + clippy → `rust-cache` → `cargo clippy --workspace --all-targets -- -D warnings` → `cargo test --workspace` → `cargo build --workspace --release` | `anixart-linux` |
| `windows` *(experimental)* | `windows-latest` | toolchain `x86_64-pc-windows-msvc` → `rust-cache` → clippy **без** `-D warnings` → `cargo test` → `cargo build --release` | `anixart-windows` |

Правила: `linux` — единственный блокирующий гейт (строгий clippy с `-D warnings`);
`windows` идёт с `continue-on-error: true` и трактует предупреждения clippy как сигнал, поэтому
она никогда не красит прогон — это зонд портативности, а не требование. Системные пакеты
добавляются в шаг apt, если появляется C-зависимость.

### Запуск на Linux (с mpv)

Скачайте артефакт `anixart-linux` со страницы прогона Actions и сделайте его исполняемым:

```bash
# 1. плеер обязателен — приложение вызывает его как внешний процесс
sudo apt install mpv            # или dnf/pacman/flatpak install mpv

# 2. развернуть бинарник
install -Dm755 anixart ~/.local/bin/anixart

# 3. запустить (iced 0.4 требует X11/Wayland, headless не получится)
anixart
```

Проверить парсеры без UI можно напрямую через mpv — не забудьте заголовки, иначе провайдер
ответит 403:

```bash
mpv --http-header-fields="Referer: https://kodik.cc/" "<720p-url>"
mpv --no-video --http-header-fields="User-Agent: <UA из ParsedVideo.headers>" "<vk-url>"
```

Полезные флаги mpv: `--http-header-fields=<field1,field2>` — дополнительные заголовки,
`--hls-bitrate=max` — максимальная версия в HLS, `--really-quiet` — тише в консоли.
Если `mpv` не найден в `PATH`, спавн молча падает (ошибка пишется в stderr).

### Дорожная карта

| Этап | Содержание | Статус |
|---|---|---|
| **M1** | Workspace, `anixart-parsers` (5 провайдеров + direct), CI Linux + Windows-зонд, документация | ✅ готово |
| **M2** | `anixart-core`: клиент `AnixartClient` на `reqwest`/`serde` — `login`, `token` в query, конверт `{code,…}`, пагинация, 12 JSON-фикстур, 16 тестов | ✅ готово |
| **M3** | UI: лента, поиск, страница релиза, список серий, хранение токена, выбор качества и озвучки | ⏭️ следующий |
| **M4** | Плеер: проброс `Referer`/`UA`/`Cookie` в mpv, продолжение по `playbackPosition`, отметки `episode/watch` и `unwatch` | 📋 в плане |
| **M5** | **Windows** (см. ниже) | 📋 в плане |
| **M6** | Оставшиеся провайдеры: SovetRomantica, MailRu, Myvi, Allvideo, StudioMir, OK.ru + фикстуры на каждый | 📋 в плане |
| **M7** | Локализация RU/EN/KY, конфиг, автообновление, упаковка Flatpak/AppImage | 📋 в плане |

**Windows (M5).** Сейчас — неблокирующая задача `windows` в том же workflow. Следующие шаги:

1. Довести `cargo clippy --workspace --all-targets -- -D warnings` и `cargo test` до зелёного
   на `windows-latest` (цель — убрать `continue-on-error: true` и сделать гейтом наравне с Linux).
2. Релиз на `x86_64-pc-windows-msvc` (+ опционально `aarch64`), ZIP с портативным `anixart.exe`,
   затем MSI через `cargo-wix` либо `NSIS`.
3. Проверить путь воспроизведения на Windows: `mpv.exe` рядом с exe или в `PATH`, либо
   `whereami`-поиск; альтернатива — интеграция `libmpv` через `mpv-sys`/`render` (тогда
   понадобятся C-зависимости и матрица CI по целевым тройкам).
4. Шрифты/локаль и обработка длинных путей (`\\?\`), корректное завершение дочернего mpv.
5. Переезд на матрицу `strategy.matrix.os: [ubuntu-latest, windows-latest]` с одним набором шагов.

**Риски.** Сервер может требовать обфусцированный заголовок `Sign` и отпечаток кодеков
(`X-Media-Session`, `X-Codec-Profile`), включает certificate pinning и rate limiting;
провайдерские API (Kodik, VK, RuTube) меняются часто — их спасают офлайн-фикстуры.
Юридически проект остаётся неофициальным: только исследования, никаких встроенных учётных данных.

---

## 🇬🇧 English

### Overview

`anixart/` is a Cargo workspace of three crates: the Anixart API network layer, video-host
parsers, and a desktop app on iced. Playback is delegated to an **external `mpv` process** —
`libmpv` is never linked, so the project has no C dependencies — which is exactly what makes an
experimental Windows build feasible (see the roadmap).

All compilation and testing happen **only in GitHub Actions**. `cargo`, `gradle` and `jadx` are
never run locally; the agent loop is `git pull --rebase` → commit → `git push` →
`~/anixsart/orch/ci_wait.sh` until it prints `CI_GREEN`.

### Architecture

| Crate | Kind | Purpose |
|---|---|---|
| [`core/`](core) | `anixart-core` (lib) | Async Anixart API client (`AnixartClient`), models, errors, paging, auth token |
| [`parsers/`](parsers) | `anixart-parsers` (lib) | Embed URL → playable m3u8/mp4 variants |
| [`app/`](app) | `anixart` (bin) | iced UI + spawning `mpv` as an external process |

Dependencies: `core` and `parsers` use `reqwest` (rustls), `tokio`, `serde`, `thiserror`
(plus `regex` in the parsers and `urlencoding` in the client); `app` uses `iced 0.4` plus both
libraries. Workspace: edition 2021, `resolver = "2"`, release profile `opt-level = 3`,
`panic = "abort"`.

**Data flow** (details in [`docs/video-sources.md`](docs/video-sources.md)):

```
login/password → AnixartClient::login()             → profile_token → set_token()
releaseId      → client.types(id)                   → types (dubbing groups)
               → client.sources(id, type_id)        → sources
               → client.episodes(id, type_id, sid)  → episodes with Episode.url
               → parsers::detect_source(url)        → provider
               → parsers::resolve(url)              → ParsedVideo { qualities, headers }
               → ParsedVideo::best_url()            → highest available variant
               → mpv <url> (+ headers)              → playback
```

`anixart-core` public surface: `AnixartClient` (`login`, `search`, `feed` / `feed_all`,
`release`, `types`, `sources`, `episodes`, `favorites`, `favorite_add` / `favorite_delete`,
`history` / `history_add` / `history_delete`, `profile`, `profile_info`, `collection`,
`set_token` / `clear_token` / `token`), the `Release`, `Episode`, `EpisodeType`, `Source`,
`FeedItem`, `Profile`, `Collection` models, the `Pageable<T>` / `CodeResponse` envelopes with
their code constants (`SUCCESS = 0`, `INVALID_LOGIN = 2`, `BANNED = 402`, …) and `AnixartError`
(`Http`, `Json`, `Api { code }`, `MissingToken`, `Unexpected`). The token travels as a **query
parameter** (`?token=…`), not an `Authorization` header; token-required methods fail fast with
`MissingToken` before any network call. Response fixtures live in [`core/fixtures/`](core/fixtures).

`anixart-parsers` public surface: `is_direct_stream`, `detect_source`, `resolve`,
`resolve_with_client`, the `ParsedVideo` / `VideoSource` models and `ParserError`.
`ParsedVideo.qualities` is keyed `Default`, `360p` … `2160p`; `ParsedVideo.headers` carries the
`Referer` / `User-Agent` / `Cookie` the player must send with the stream request.

Implemented providers: **Kodik** (`kodikres.com/api/video-links`), **Sibnet** (embed scrape +
redirect), **VK** (`video.get` + anonymous token), **RuTube** (`api/play/options` + m3u8 variant
parsing), **AniLibria** (HLS by release/episode with an embed fallback) plus direct `mp4`/`m3u8`.
Each module keeps pure parsing helpers (`extract_*`, `parse_*`) separate from the network call
(`resolve`), so the logic is covered by offline fixtures.

**Status:** `anixart-core` is a finished async client (`AnixartClient` + models + response
fixtures), and `anixart-parsers` covers 5 providers plus direct streams. `app` is still a working
iced login form that spawns `mpv` on a test stream; wiring it to `AnixartClient` is M3/M4.
Tests: **38** (22 in `parsers` + 16 in `core`, including 13 serde fixtures), all offline —
no network needed for unit tests.

### CI build

Workflow: [`.github/workflows/anixart-rust.yml`](../.github/workflows/anixart-rust.yml)
(`Anixart Rust CI (Linux + Windows)`). It triggers on pushes to `main` touching `anixart/**` or
the workflow itself, and on `workflow_dispatch`. Both jobs inherit
`working-directory: anixart`.

| Job | Runner | Steps | Artifact |
|---|---|---|---|
| `linux` | `ubuntu-latest` | apt: `libxkbcommon-dev`, `mpv` → toolchain + clippy → `rust-cache` → `cargo clippy --workspace --all-targets -- -D warnings` → `cargo test --workspace` → `cargo build --workspace --release` | `anixart-linux` |
| `windows` *(experimental)* | `windows-latest` | toolchain `x86_64-pc-windows-msvc` → `rust-cache` → clippy **without** `-D warnings` → `cargo test` → `cargo build --release` | `anixart-windows` |

Rules: `linux` is the only blocking gate (strict clippy with `-D warnings`). The `windows` job
carries `continue-on-error: true` and treats lints as signals, so it can never redden the run —
it is a portability probe, not a requirement. Add system packages to the apt step if a C
dependency ever appears.

### Running on Linux (with mpv)

Download the `anixart-linux` artifact from the Actions run page and make it executable:

```bash
# 1. the player is required — the app invokes it as an external process
sudo apt install mpv            # or dnf/pacman/flatpak install mpv

# 2. install the binary
install -Dm755 anixart ~/.local/bin/anixart

# 3. run it (iced 0.4 needs X11/Wayland — not headless)
anixart
```

Exercise the parsers without the UI by handing a resolved URL straight to mpv — pass the
headers, or the provider answers 403:

```bash
mpv --http-header-fields="Referer: https://kodik.cc/" "<720p-url>"
mpv --no-video --http-header-fields="User-Agent: <UA from ParsedVideo.headers>" "<vk-url>"
```

Useful mpv flags: `--http-header-fields=<field1,field2>` for extra headers,
`--hls-bitrate=max` for the top HLS variant, `--really-quiet` for less console noise.
If `mpv` is not on `PATH`, the spawn fails quietly (the error goes to stderr).

### Roadmap

| Milestone | Scope | Status |
|---|---|---|
| **M1** | Workspace, `anixart-parsers` (5 providers + direct), Linux CI + Windows probe, docs | ✅ done |
| **M2** | `anixart-core`: `AnixartClient` on `reqwest`/`serde` — `login`, `token` in query, `{code,…}` envelope, paging, 12 JSON fixtures, 16 tests | ✅ done |
| **M3** | UI: feed, search, release page, episode list, token storage, quality & dubbing pickers | ⏭️ next |
| **M4** | Player: forward `Referer`/`UA`/`Cookie` to mpv, resume via `playbackPosition`, `episode/watch` + `unwatch` reporting | 📋 planned |
| **M5** | **Windows** (below) | 📋 planned |
| **M6** | Remaining providers: SovetRomantica, MailRu, Myvi, Allvideo, StudioMir, OK.ru + per-provider fixtures | 📋 planned |
| **M7** | i18n RU/EN/KY, config, auto-update, Flatpak/AppImage packaging | 📋 planned |

**Windows (M5).** Today it is the non-blocking `windows` job in the same workflow. Next steps:

1. Get `cargo clippy --workspace --all-targets -- -D warnings` and `cargo test` green on
   `windows-latest` (goal: drop
   `continue-on-error: true` and make it a gate equal to Linux).
2. Release on `x86_64-pc-windows-msvc` (+ optionally `aarch64`), a ZIP with a portable
   `anixart.exe`, then MSI via `cargo-wix` or NSIS.
3. Validate the playback path on Windows: `mpv.exe` beside the exe or on `PATH`, with a
   lookup helper; alternatively embed `libmpv` via `mpv-sys`/render (that reintroduces C deps
   and needs a per-target CI matrix).
4. Fonts/locale and long paths (`\\?\`), and clean shutdown of the child mpv.
5. Move to a `strategy.matrix.os: [ubuntu-latest, windows-latest]` matrix sharing one step set.

**Risks.** The server may demand the obfuscated `Sign` header plus codec fingerprints
(`X-Media-Session`, `X-Codec-Profile`), and it uses certificate pinning and rate limiting;
provider APIs (Kodik, VK, RuTube) change frequently, which is exactly what the offline fixtures
absorb. Legally the project stays unofficial: research only, no bundled credentials.
