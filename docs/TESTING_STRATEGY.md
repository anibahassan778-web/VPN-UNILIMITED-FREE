# WireShield Testing Strategy

## 1. Automated Test Suite (Local JVM & Robolectric)
Located in `app/src/test/java/com/example/`:
* **`WireGuardCryptoTest`**:
  * RFC 7748 Curve25519 Montgomery ladder scalar multiplication validation.
  * Clamping verification (clearing lowest 3 bits of byte 0, clearing bit 7 of byte 31, setting bit 6 of byte 31).
  * Base64 encoding/decoding and 32-byte key length enforcement.
  * Public key derivation accuracy.
* **`WireGuardConfigTest`**:
  * Parsing standard `.conf` files (`[Interface]` and `[Peer]` sections).
  * Generation of valid `.conf` file output with MTU, Keepalive, and DNS parameters.
* **`VpnStatsAndLeakPolicyTest`**:
  * Formatted duration string conversion (seconds to `HH:MM:SS`).
  * Byte rate calculations and human-readable throughput formatting (KB/s, MB/s, GB).
  * Validation of default leak protection policies (DNS leak and IPv6 leak flags enabled by default).
* **`ExampleRobolectricTest`**:
  * `SettingsRepository` preference persistence.
  * `ServerRepository` node loading and custom server CRUD.

## 2. Integration & Leak Verification Testing
* **DNS Leak Testing**:
  * Tool: `dnsleaktest.com` and `browserleaks.com/dns`.
  * Expected Outcome: All DNS queries resolve exclusively to configured upstream servers (`1.1.1.1` or `9.9.9.9`), showing zero ISP DNS servers.
* **IPv6 Leak Testing**:
  * Tool: `ipv6leak.com` and `test-ipv6.com`.
  * Expected Outcome: Dual-stack mobile connections route IPv6 over `fd00::2/128` through the tunnel or block cleartext IPv6 leaks.
* **Kill Switch Integrity**:
  * Trigger: Terminate background VPN service abruptly or simulate network drop.
  * Expected Outcome: Socket connections outside the tunnel fail immediately (`ECONNREFUSED` / blocked), preventing unencrypted IP exposure.
* **Network Handover (Wi-Fi <-> Cellular 4G/5G)**:
  * Trigger: Toggle Wi-Fi off while VPN is connected.
  * Expected Outcome: `ConnectivityManager.NetworkCallback` detects network transition, re-protects the UDP socket with `protect()`, and re-establishes the tunnel with zero UI crash.
