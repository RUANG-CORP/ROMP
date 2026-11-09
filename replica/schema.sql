-- Schema: ROMP / Valleta Data Persistence
-- Compatible with SQLite, Room, or external relational database.

CREATE TABLE IF NOT EXISTS servers (
    id TEXT PRIMARY KEY,
    ip TEXT NOT NULL,
    port INTEGER NOT NULL CHECK (port > 0 AND port <= 65535),
    name TEXT NOT NULL,
    is_favorite INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_servers_ip_port ON servers(ip, port);
CREATE INDEX IF NOT EXISTS idx_servers_is_favorite ON servers(is_favorite);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial default settings
INSERT OR IGNORE INTO settings (key, value) VALUES 
('player_nickname', 'Player_Valleta'),
('fps_limit', '90'),
('fast_chat', '1'),
('cef_overlay', '1'),
('voice_chat', '0');

CREATE TABLE IF NOT EXISTS download_tasks (
    id TEXT PRIMARY KEY,
    source_url TEXT NOT NULL,
    dest_path TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('IDLE', 'DOWNLOADING', 'EXTRACTING', 'COMPLETED', 'FAILED')),
    progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (progress_percent >= 0 AND progress_percent <= 100),
    total_bytes INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

