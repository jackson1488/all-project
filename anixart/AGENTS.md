# Anixart Rust Project — Provider & Orchestration Rules

## Forbidden Providers (NEVER USE)
- NEVER use `omniroute-antigravity/*` or `antigravity/*` or `agy/*`.
- NEVER use `omniroute-codex/*` or `codex/*` or any `*terra*` model.

## Allowed Providers & Concurrency
1. `iishko/*`: **MAX 2 parallel sessions** at a time (API key concurrency limit).
2. `omniroute-free/*`: free models (`claude-haiku-4-5`, `gpt-5.6-luna`, `gpt-5.4-mini`, `gemma4-31b`, `gpt-oss-120b`, `mistral-small-2603`) — can run in parallel.
3. `google/*`: direct Google provider (`gemini-*`, `gemma-*`) — allowed.
4. `omniroute-github/*` (`gh/auto`, `cu/auto`, `cu/default`): allowed (200 req/month student plan; note: may be temporarily down on OmniRoute).
5. `opencode/*-free`: allowed when free quota is available.

## Build & Repo Rules
- Repo: `jackson1488/all-project` (`~/anixsart/ci-repo`), work ONLY in `anixart/` and `.github/workflows/anixart-*.yml`.
- NEVER run `cargo`, `jadx`, or `gradle` locally — all builds/tests run in GitHub Actions via `~/anixsart/orch/ci_wait.sh`.
- NEVER commit APKs, decompiled sources, or secrets.
