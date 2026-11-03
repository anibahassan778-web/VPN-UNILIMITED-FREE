# WireShield Privacy Model

## 1. Zero-Logs Policy by Design
WireShield is built with strict privacy-by-design principles:
* **No Browsing History**: We never monitor, intercept, inspect, or log the websites, IP addresses, domains, or online services you connect to.
* **No DNS Query Logging**: Upstream DNS resolvers configured by default (Cloudflare `1.1.1.1` and Quad9 `9.9.9.9`) do not log client query IPs.
* **No Traffic Inspection**: All payload packets traveling through the WireGuard tunnel are encrypted end-to-end between the client device and the selected VPN exit node.
* **No Ad Trackers or Third-Party Analytics SDKs**: The Android application does not include Firebase Analytics, Google Ads, Facebook SDK, or third-party telemetry libraries.

## 2. Technical Data Boundary
To run a functional WireGuard tunnel, the system processes only the following ephemeral in-memory state:
* **Client WireGuard Public Key**: Required by WireGuard's Cryptokey Routing table to associate incoming encrypted UDP packets with an authorized internal IP.
* **Assigned Virtual IP**: An internal private RFC 1918 address (e.g. `10.66.66.x/32`) retained in memory while the tunnel is active.
* **Last Handshake Timestamp**: Maintained natively by the Linux WireGuard kernel module to decide when re-keying is necessary (typically renegotiated every 120 seconds).
* **Aggregate Traffic Statistics**: Kept strictly local to the Android device to display your active session download/upload bytes and transfer speeds.

## 3. Infrastructure & Cost Realities
* **Software Freedom**: WireShield is 100% free and open-source software (FOSS). The app imposes no artificial artificial speed limits, monthly quotas, or paywalls.
* **Hosting Transparency**: "Free software" does not mean internet bandwidth and high-speed data center VPS instances are free of cost. Server operators incur hosting and transit fees from infrastructure providers (Hetzner, OVH, Linode, AWS, Oracle Cloud, etc.).
* **Self-Hosting Freedom**: WireShield includes a built-in Server Manager so users or organizations can add their own self-hosted WireGuard servers with full control over hardware and bandwidth.
