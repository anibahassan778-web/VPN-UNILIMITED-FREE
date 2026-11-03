# WireShield VPN - System Architecture

WireShield is an open-source, privacy-first VPN ecosystem designed around the **WireGuard®** protocol. The platform delivers modern cryptography, low battery consumption, strict zero-logging policies, and server freedom (self-hosted nodes with no forced subscription paywalls).

---

## 1. Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                               ANDROID CLIENT                                      |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                 Jetpack Compose UI (Material 3 Dark Theme)                  |  |
|  |  [Shield Button] [Server Selector] [Live Speed & Stats] [Kill Switch Pill]  |  |
|  +---------------------------------------+-------------------------------------+  |
|                                          | StateFlow / User Actions               |
|  +---------------------------------------v-------------------------------------+  |
|  |                     VpnManager & Settings Repository                        |  |
|  |       (Coordinates State Machine, Key Management, Auto-Reconnect)           |  |
|  +-------------------+-----------------------------------+---------------------+  |
|                      |                                   |                        |
|  +-------------------v-------------------+       +-------v---------------------+  |
|  |      WireGuard Crypto (Curve25519)     |       |    Android VpnService       |  |
|  |  (Client Key Generation & Validation) |       |  (TUN Interface: 10.66.x.x) |  |
|  +---------------------------------------+       |  Routes: 0.0.0.0/0, ::/0    |  |
|                                                  |  Kill Switch & DNS Filter   |  |
|                                                  +---------------+-------------+  |
+------------------------------------------------------------------|----------------+
                                                                   | Encrypted UDP
                                                                   | Port 51820
                                                                   |
          +--------------------------------------------------------+--------------------------------+
          |                                                                                         |
          v                                                                                         v
+------------------------------------------+                             +------------------------------------------+
|      BACKEND MANAGEMENT API (Node.js)    |                             |      WIREGUARD LINUX NODE (wg0)          |
|                                          |                             |                                          |
|  +------------------------------------+  |                             |  +------------------------------------+  |
|  | REST Endpoints:                    |  |                             |  | Kernel wg0 Interface               |  |
|  |  GET  /api/servers                 |  |                             |  | ChaCha20-Poly1305 / Curve25519     |  |
|  |  POST /api/client/config           |  |                             |  +------------------+-----------------+  |
|  |  POST /api/client/revoke           |  |                             |                     |                    |
|  +------------------+-----------------+  |                             |  +------------------v-----------------+  |
|                     |                    |                             |  | iptables / UFW NAT Masquerade      |  |
|  +------------------v-----------------+  |                             |  | sysctl Forwarding & SYN Protection |  |
|  | SQLite / PostgreSQL Database       |  |                             |  +------------------+-----------------+  |
|  | (Servers, Public Keys, Allocations)|  |                             |                     |                    |
|  +------------------------------------+  |                             |  +------------------v-----------------+  |
|                                          |                             |  | Secure DNS (1.1.1.1 / Quad9)       |  |
+------------------------------------------+                             |  +------------------+-----------------+  |
                                                                         +---------------------|--------------------+
                                                                                               |
                                                                                               v
                                                                                   +-----------------------+
                                                                                   |       INTERNET        |
                                                                                   +-----------------------+
```

---

## 2. Component Breakdown

### 2.1 Android Client (`android/` / `app/`)
* **`WireShieldVpnService`**: Extends Android `VpnService`. Establishes the virtual network interface (`TUN`), applies routing policies (`0.0.0.0/0` for IPv4, `::/0` for IPv6 leak protection), enforces non-tunnel blocking for the **Kill Switch**, and manages the UDP socket packet loop with `protect()`.
* **`WireGuardCrypto`**: Pure Kotlin implementation of RFC 7748 Montgomery ladder scalar multiplication on Curve25519. Generates secure 32-byte key pairs and clamps private keys entirely on the device.
* **`VpnManager`**: Central singleton coordinating lifecycle states (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `DISCONNECTING`, `ERROR`), live byte transfer counters, session duration timer, and public egress IP verification.
* **`NetworkCallback`**: Listens to underlying network connectivity changes (e.g. Wi-Fi disconnect to 5G cellular handover) and seamlessly re-tunnels the session without exposing unencrypted packets.

### 2.2 Backend Management API (`backend/`)
* Built with Node.js & Express (or optionally Python FastAPI).
* Supplies active server list with latency, load metrics, and public keys.
* Allocates dynamic client tunnel IPs (e.g., `10.66.66.x/32`) upon receiving the client's public key.
* Never accepts or stores client private keys.
* Features rate limiting, zero-logging middleware, and token-based admin authorization.

### 2.3 WireGuard Gateway Servers (`server/`)
* Standard Linux kernel with native WireGuard module (`wg-quick@wg0`).
* Hardened network parameters in `/etc/sysctl.d/99-wireguard-hardening.conf` (anti-SYN flood, reverse path filtering, disabling ICMP redirects).
* Automated firewall NAT masquerading (`iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE`).
* Encrypted upstream recursive DNS (Cloudflare `1.1.1.1` and Quad9 `9.9.9.9`).

### 2.4 Administration CLI (`admin/`)
* Lightweight Python CLI tool for cluster administrators to register new server nodes, inspect health, check active peers, and revoke keys.
