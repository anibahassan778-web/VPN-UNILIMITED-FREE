# WireShield VPN - Prebuilt APK Releases

This directory contains prebuilt installable Android application packages (APKs) for WireShield VPN.

---

## 📦 Available Releases

### 🚀 Version 2.0 (Latest Release - Full Feature Suite)

| Attribute | Value |
| :--- | :--- |
| **Application Name** | WireShield VPN v2.0 |
| **Package ID (`applicationId`)** | `com.aistudio.wireshieldvpn.kzp` |
| **Version Name** | `2.0` |
| **Version Code** | `2` |
| **Min SDK** | `24` (Android 7.0 Nougat) |
| **Target SDK** | `36` (Android 15 / 16 Preview) |
| **File Names** | `WireShield-VPN-v2.0-debug.apk` / `app-debug.apk` |
| **File Size** | ~23.5 MB |
| **SHA-256 Checksum** | `c6bf6aebb20faa80c240705eedd6df463e818fe38c262364acd0a2e3775a1856` |
| **Build Type** | Debug (Pre-signed with debug keystore) |

#### 🌟 Key Features in v2.0:
- **6 Specialized VPN Profiles**: Balanced Shield, Gaming (lowest latency / 1360 MTU), Streaming (4K optimized / 1420 MTU), Stealth Mode (anti-DPI obfuscation / 1280 MTU), P2P High-Speed, and Battery Saver.
- **DNS Shield Protection**: Ad & Tracker blocker (AdGuard 94.140.14.14), Malware & Phishing protection (Quad9 9.9.9.9), Family Protection (CleanBrowsing SafeSearch), Ultra Fast (Cloudflare 1.1.1.1), and Custom upstream DNS.
- **Split Tunneling**: Native per-application routing allowing selected apps (e.g. banking, food delivery) to bypass the VPN tunnel.
- **Diagnostics & In-App Testing**: Built-in real-time Speed Test (Latency, Jitter, Download, Upload) and DNS/IP Leak diagnostic check.
- **Emergency Panic Lockdown & Purge**: Instant cryptographic key wipe, custom node flush, and complete factory reset.
- **Watchdog Kill Switch & Network Monitor**: Active background watchdog detecting silent connection drops and severing unencrypted traffic instantly.

---

### 📦 Version 1.0 (Initial Release)

| Attribute | Value |
| :--- | :--- |
| **File Name** | `WireShield-VPN-debug.apk` |
| **Version Name** | `1.0` (Version Code `1`) |
| **SHA-256 Checksum** | `cdbec1c6c3d19f8f1781ab2c41a8a3abe8f13a8a94a1e83775bfd32587c9d777` |

---

## 📱 How to Install on Android

### Method 1: Via ADB (USB Debugging)
Connect your phone with USB debugging enabled:
```bash
adb install -r apks/WireShield-VPN-v2.0-debug.apk
```

### Method 2: Direct Phone Download / File Transfer
1. Transfer `WireShield-VPN-v2.0-debug.apk` to your phone via USB, Google Drive, or local sharing.
2. Tap the `.apk` file in your phone's File Manager.
3. Allow "Install unknown apps" for your file manager if prompted.
4. Tap **Install** and open **WireShield VPN**.

---

## 🛠️ How to Rebuild APKs

To recompile and update the files in this directory:
```bash
# Clean and assemble debug APK
gradle :app:assembleDebug

# Copy to apks directory
mkdir -p apks
cp app/build/outputs/apk/debug/app-debug.apk apks/WireShield-VPN-v2.0-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk apks/app-debug.apk
```
