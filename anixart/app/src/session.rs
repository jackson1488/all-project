//! Persistent Anixart session token.
//!
//! The token is stored as JSON under `~/.config/anixart-linux/session.json`
//! (`$ANIXART_CONFIG_DIR` wins when set, then `$XDG_CONFIG_HOME`), so a restart
//! logs the user straight back in without another round trip to the IdP.
//!
//! Only the session token, the method it came from and a timestamp are written;
//! passwords, PKCE verifiers and third-party tokens are never persisted.

use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

/// Environment override of the config directory (used by the tests too).
pub const CONFIG_DIR_ENV: &str = "ANIXART_CONFIG_DIR";
/// Directory name under `$XDG_CONFIG_HOME` / `$HOME/.config`.
pub const CONFIG_DIR_NAME: &str = "anixart-linux";
/// File name of the stored session.
pub const SESSION_FILE: &str = "session.json";
/// Schema version of the file.
pub const SESSION_VERSION: i64 = 1;

/// A restored session.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Session {
    /// Anixart `profileToken.token`, sent as `?token=` on every call.
    pub token: String,
    /// Which login method produced the token (`password`, `telegram`, …).
    pub method: String,
    /// Unix seconds at save time (`0` when unknown).
    pub saved_at: i64,
}

/// Where the session lives. `root` is the directory holding [`SESSION_FILE`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SessionStore {
    root: Option<PathBuf>,
}

impl Default for SessionStore {
    fn default() -> Self {
        Self::from_env()
    }
}

impl SessionStore {
    /// Resolves the config directory from the environment.
    pub fn from_env() -> Self {
        Self {
            root: config_dir(),
        }
    }

    /// Explicit directory, mainly for tests and portable installs.
    pub fn with_root(root: impl Into<PathBuf>) -> Self {
        Self {
            root: Some(root.into()),
        }
    }

    /// A store that never touches the disk.
    pub fn disabled() -> Self {
        Self { root: None }
    }

    /// Full path of `session.json`, or `None` when persistence is unavailable.
    pub fn path(&self) -> Option<PathBuf> {
        self.root.as_ref().map(|root| root.join(SESSION_FILE))
    }

    /// Reads the stored session, tolerating malformed or legacy files.
    pub fn load(&self) -> Option<Session> {
        let path = self.path()?;
        let text = fs::read_to_string(path).ok()?;
        parse_session(&text)
    }

    /// Convenience wrapper used at startup.
    pub fn load_token(&self) -> Option<String> {
        self.load().map(|session| session.token)
    }

    /// Writes the session (creating the directory when missing).
    pub fn save(&self, token: &str, method: &str) -> Result<(), String> {
        let path = match self.path() {
            Some(path) => path,
            None => return Ok(()),
        };
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| format!("{}: {e}", parent.display()))?;
        }
        let text = render_session(token, method, now_unix());
        fs::write(&path, text).map_err(|e| format!("{}: {e}", path.display()))?;
        restrict_to_owner(&path);
        Ok(())
    }

    /// Removes the stored session; a missing file is not an error.
    pub fn clear(&self) -> Result<(), String> {
        let Some(path) = self.path() else {
            return Ok(());
        };
        match fs::remove_file(&path) {
            Ok(()) => Ok(()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Err(error) => Err(format!("{}: {error}", path.display())),
        }
    }
}

/// `$ANIXART_CONFIG_DIR`, else `$XDG_CONFIG_HOME/anixart-linux`,
/// else `$HOME/.config/anixart-linux`.
pub fn config_dir() -> Option<PathBuf> {
    if let Some(override_dir) = std::env::var_os(CONFIG_DIR_ENV) {
        if !override_dir.is_empty() {
            return Some(PathBuf::from(override_dir));
        }
    }
    let base = match std::env::var_os("XDG_CONFIG_HOME") {
        Some(xdg) if !xdg.is_empty() => PathBuf::from(xdg),
        _ => {
            let home = std::env::var_os("HOME")?;
            if home.is_empty() {
                return None;
            }
            PathBuf::from(home).join(".config")
        }
    };
    Some(base.join(CONFIG_DIR_NAME))
}

/// Serialises the session file (pretty JSON, `token` always present).
pub fn render_session(token: &str, method: &str, saved_at: i64) -> String {
    let mut map = serde_json::Map::with_capacity(4);
    map.insert("version".into(), SESSION_VERSION.into());
    map.insert("token".into(), token.into());
    map.insert("method".into(), method.into());
    map.insert("saved_at".into(), saved_at.into());
    serde_json::to_string_pretty(&serde_json::Value::Object(map))
        .unwrap_or_else(|_| format!("{{\"token\":\"{token}\"}}"))
}

/// Parses the session file. A bare JSON string is accepted as a legacy token.
pub fn parse_session(text: &str) -> Option<Session> {
    let value: serde_json::Value = serde_json::from_str(text).ok()?;
    if let Some(token) = value.as_str() {
        return non_empty_session(token, Session::default());
    }
    let token = value.get("token")?.as_str()?.to_string();
    let session = Session {
        method: value
            .get("method")
            .and_then(serde_json::Value::as_str)
            .unwrap_or("unknown")
            .to_string(),
        saved_at: value
            .get("saved_at")
            .and_then(serde_json::Value::as_i64)
            .unwrap_or(0),
        token: String::new(),
    };
    non_empty_session(&token, session)
}

fn non_empty_session(token: &str, mut session: Session) -> Option<Session> {
    let token = token.trim();
    if token.is_empty() {
        return None;
    }
    session.token = token.to_string();
    Some(session)
}

fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|delta| delta.as_secs() as i64)
        .unwrap_or(0)
}

/// `chmod 600` on Unix; a no-op elsewhere (the file already lives in the user's
/// own config directory).
#[cfg(unix)]
fn restrict_to_owner(path: &Path) {
    use std::os::unix::fs::PermissionsExt;

    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o600));
}

#[cfg(not(unix))]
fn restrict_to_owner(_path: &Path) {}

#[cfg(test)]
mod tests {
    use super::*;

    fn unique_root(tag: &str) -> PathBuf {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.subsec_nanos())
            .unwrap_or(0);
        std::env::temp_dir().join(format!("anixart-session-{tag}-{nanos:09}"))
    }

    #[test]
    fn render_and_parse_round_trip() {
        let text = render_session("tok-123", "telegram", 1_700_000_000);
        let session = parse_session(&text).expect("session");
        assert_eq!(session.token, "tok-123");
        assert_eq!(session.method, "telegram");
        assert_eq!(session.saved_at, 1_700_000_000);
        assert!(text.contains("\"version\""));
    }

    #[test]
    fn parse_tolerates_legacy_and_broken_files() {
        // A bare JSON string is treated as a token.
        assert_eq!(parse_session("\"legacy\"").map(|s| s.token).as_deref(), Some("legacy"));
        // Missing / empty token, junk, and non-JSON all fail.
        assert!(parse_session("{}").is_none());
        assert!(parse_session(r#"{"token":"  "}"#).is_none());
        assert!(parse_session("not json").is_none());
        assert!(parse_session(r#"{"token":42}"#).is_none());
        // Unknown / absent metadata falls back to defaults.
        let session = parse_session(r#"{"token":"abc"}"#).expect("minimal");
        assert_eq!((session.method.as_str(), session.saved_at), ("unknown", 0));
    }

    #[test]
    fn store_saves_loads_and_clears() {
        let root = unique_root("roundtrip");
        let _ = fs::remove_dir_all(&root);
        let store = SessionStore::with_root(&root);

        assert!(store.load().is_none());
        store.save("tok-9", "password").expect("save");
        assert_eq!(store.path(), Some(root.join(SESSION_FILE)));

        let session = store.load().expect("load");
        assert_eq!(session.token, "tok-9");
        assert_eq!(session.method, "password");
        assert!(session.saved_at > 0);
        assert_eq!(store.load_token().as_deref(), Some("tok-9"));

        assert!(store.clear().is_ok());
        assert!(store.load().is_none());
        // Clearing twice is not an error.
        assert!(store.clear().is_ok());
        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn a_disabled_store_never_touches_the_disk() {
        let store = SessionStore::disabled();
        assert_eq!(store.path(), None);
        assert!(store.load().is_none());
        assert!(store.save("tok", "vk").is_ok());
        assert!(store.clear().is_ok());
    }

    #[test]
    fn saving_creates_the_directory_and_keeps_the_token_private() {
        let root = unique_root("perms");
        let _ = fs::remove_dir_all(&root);
        let store = SessionStore::with_root(&root);
        store.save("secret-token", "google").expect("save");

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = fs::metadata(store.path().unwrap()).unwrap().permissions().mode();
            assert_eq!(mode & 0o777, 0o600, "session.json must be owner-only");
        }

        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn config_dir_prefers_the_override_then_xdg_then_home() {
        let root = unique_root("env");
        std::env::set_var(CONFIG_DIR_ENV, &root);
        assert_eq!(config_dir(), Some(root.clone()));

        std::env::remove_var(CONFIG_DIR_ENV);
        if let Some(xdg) = std::env::var_os("XDG_CONFIG_HOME") {
            assert_eq!(config_dir(), Some(PathBuf::from(xdg).join(CONFIG_DIR_NAME)));
        }

        std::env::set_var(CONFIG_DIR_ENV, "");
        // An empty override must not produce a relative path.
        assert!(config_dir().is_some());
        std::env::remove_var(CONFIG_DIR_ENV);
        let _ = fs::remove_dir_all(&root);
    }
}
