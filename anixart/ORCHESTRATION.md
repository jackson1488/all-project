# Anixart Rust client — orchestration

Orchestrator (Antigravity) writes specs; OpenCode agents write code; heavy work runs in GitHub Actions only.

## Model split (OpenCode)
- Free pool (`OpenCode` provider): boilerplate, parsers, tests, docs.
- `lishko` pool: harder code (API client, player/UI glue).
- OmniRoute AGY / Claude: reverse analysis & architecture review.

## Pipeline
1. `anixart-reverse` workflow -> API hints artifact -> API.md
2. `anixart/core` (reqwest/serde/tokio)
3. video parsers (m3u8/mp4)
4. Linux UI (iced + libmpv); Windows later via CI matrix
