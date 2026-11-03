-- ============================================================
-- WireShield Open-Source VPN Server & Peer Database Schema
-- Compatible with PostgreSQL and SQLite
-- ============================================================

-- Table: servers
-- Manages self-hosted or authorized WireGuard VPN nodes
CREATE TABLE IF NOT EXISTS servers (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    country VARCHAR(64) NOT NULL,
    country_code VARCHAR(4) NOT NULL DEFAULT 'UN',
    city VARCHAR(64) NOT NULL,
    endpoint_host VARCHAR(255) NOT NULL,
    endpoint_port INTEGER NOT NULL DEFAULT 51820,
    public_key VARCHAR(64) NOT NULL,
    dns_servers VARCHAR(255) NOT NULL DEFAULT '1.1.1.1, 1.0.0.1',
    subnet_cidr VARCHAR(32) NOT NULL DEFAULT '10.66.66.0/24',
    max_clients INTEGER NOT NULL DEFAULT 250,
    current_clients INTEGER NOT NULL DEFAULT 0,
    load_percent INTEGER NOT NULL DEFAULT 10,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: client_peers
-- Tracks active WireGuard client public keys and allocated internal IPs
-- CRITICAL SECURITY RULE: Private keys are NEVER received or stored in this DB!
CREATE TABLE IF NOT EXISTS client_peers (
    id VARCHAR(64) PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL,
    client_public_key VARCHAR(64) NOT NULL UNIQUE,
    allocated_ip VARCHAR(32) NOT NULL,
    preshared_key VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REVOKED, EXPIRED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
);

-- Table: server_health_logs
-- Stores periodic latency and availability ping metrics
CREATE TABLE IF NOT EXISTS server_health_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    server_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL, -- ONLINE, DEGRADED, OFFLINE
    latency_ms INTEGER DEFAULT -1,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
);

-- Seed Initial Default Cluster Nodes
INSERT OR IGNORE INTO servers (id, name, country, country_code, city, endpoint_host, endpoint_port, public_key, dns_servers, load_percent)
VALUES 
('srv-de-fra', 'Frankfurt Edge 01', 'Germany', 'DE', 'Frankfurt', 'de-fra.wireshield-relay.net', 51820, '0a5dY+T0w6O3kU2sW1V5a8b7C9d0E2f4G6h8I0j2K4M=', '1.1.1.1, 1.0.0.1', 32),
('srv-nl-ams', 'Amsterdam Fast Relay', 'Netherlands', 'NL', 'Amsterdam', 'nl-ams.wireshield-relay.net', 51820, '8b9cE0f2A4c6E8g0I2k4M6o8Q0s2U4w6Y8a0C2e4G6I=', '9.9.9.9, 149.112.112.112', 45),
('srv-ch-zur', 'Zurich Privacy Vault', 'Switzerland', 'CH', 'Zurich', 'ch-zur.wireshield-relay.net', 51820, '5f7aB9d1F3h5J7l9N1p3R5t7V9x1Z3b5D7f9H1j3L5N=', '1.1.1.1, 9.9.9.9', 18);
