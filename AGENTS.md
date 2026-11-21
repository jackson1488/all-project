# Neongram Kotlin Native Android Client — Rules & Guidelines (AGENTS.md)

## 1. Строгая изоляция репозитория (`neongram-kotlin`)
- Этот репозиторий находится строго в `/home/jackson/Neongram/neongram-kotlin/` и пушится в `https://github.com/jackson1488/all-project.git`.
- **ЗАПРЕЩЕНО** трогать или коммитить любые файлы за пределами `/home/jackson/Neongram/neongram-kotlin/`. Родительская директория содержит приватные ключи и другие сервисы, которые никогда не должны попадать в этот репозиторий.

## 2. Полный запрет локального `./gradlew` (Cloud CI Only)
- **СТРОГО ЗАПРЕЩЕНО запускать `./gradlew` или `gradle` локально на компьютере пользователя**.
- Компиляция APK происходит **ТОЛЬКО в GitHub Actions** (`https://github.com/jackson1488/all-project`):
  1. `git add <files>` (внутри `/home/jackson/Neongram/neongram-kotlin`)
  2. `git commit -m "feat: ..."`
  3. `git push origin main`
  4. Мониторинг GitHub Actions и скачивание готового артефакта APK.

## 3. Тихая установка по ADB
- При установке на устройство выполнять **ТОЛЬКО** `adb install -r <apk>`.
- **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** запускать приложение через `am start`, `monkey` и т.п.

## 4. Архитектура и Бэкенд
- Единый бэкенд: `https://cloud.neongram.space` (REST API `/api/*` + Realtime Socket.IO).
- Сборка только под `arm64-v8a`.
- Интерфейс: Jetpack Compose (Material 3), неоновая тема Neongram, поддержка локализации (RU / EN / KY).
