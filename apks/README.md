# WireShield VPN - Prebuilt APK Releases

This directory contains prebuilt installable Android application packages (APKs) for WireShield VPN, split by CPU architecture for maximum performance and minimal download size.

---

## 📦 Architecture-Specific Releases (v2.0 - Split & Optimized)

By enabling **ABI Splits** and **R8 Code/Resource Minification**, individual APK sizes have been reduced from ~35 MB down to **~9.6 MB – 11 MB** (over 70% reduction in file size).

| Architecture / Target Device | APK File Name | Size | SHA-256 Checksum | Recommended For |
| :--- | :--- | :--- | :--- | :--- |
| **ARM64 (64-bit)** | `WireShield-v2.0-arm64-v8a.apk` | **11.0 MB** | `6f60db3dc500e775281b5e341ab384584b7f92dbcc10875de19c275a4f87c96f` | 95%+ of modern smartphones (Pixel, Galaxy S/A, Xiaomi, OnePlus, Pixel, etc.) |
| **ARMv7 (32-bit)** | `WireShield-v2.0-armeabi-v7a.apk` | **9.6 MB** | `27819ef63f6c77f125d45ed8c4364bd12659a7a4a4b26f4a88c521aa245f84a2` | Older phones, budget Android devices, legacy TV sticks |
| **x86_64 (64-bit Intel/AMD)** | `WireShield-v2.0-x86_64.apk` | **11.0 MB** | `d00d66819a96a05e9321beb5b67cbca9dbdc4372db487d67a85db186f5be3c43` | 64-bit Android Emulators, ChromeOS devices with x86 CPUs |
| **x86 (32-bit Intel)** | `WireShield-v2.0-x86.apk` | **9.6 MB** | `84921e4811633d46ad715504efc859e4e9ba3e90c595fb9a11ce8154156d3c19` | 32-bit Emulators, Asus ZenFone Intel legacy devices |
| **Universal (All-in-One)** | `WireShield-v2.0-universal.apk` | **19.0 MB** | `9b4a82ab566d4b84d2fe06864bdec4a30fafe4779185a12f54a0e7e41d6c5b8c` | Universal fallback containing native binaries for all 4 architectures |

---

## 🌟 Key Features & Architecture Highlights in v2.0

- **Real Native WireGuard Tunneling**: Direct integration with GoBackend (`com.wireguard.android:tunnel`).
- **Pre-Tunnel DNS Resolution & Caching**: Pre-resolves endpoint IP before tunnel creation to eliminate resolution deadlocks with Kill Switch.
- **Dynamic Config Import**: Quick manual configuration via `.conf` files and QR Code scanning.
- **6 Specialized Performance Profiles**: Balanced, Low Latency Gaming, 4K Streaming, Anti-DPI Stealth, High-Speed P2P, and Battery Saver.
- **DNS Shield Protection**: Upstream DNS filtering including AdGuard, Quad9, CleanBrowsing SafeSearch, and Cloudflare 1.1.1.1.
- **Per-App Split Tunneling**: Route specific apps inside or outside the VPN tunnel.
- **Emergency Panic Lockdown & Key Purge**: Wipe active keys and clear sensitive configurations instantly.

---

## 📱 How to Install on Android

### Method 1: Via ADB (USB Debugging)
Select the APK matching your target device/emulator architecture:
```bash
# For modern 64-bit phones (Pixel, Samsung, Xiaomi, etc.)
adb install -r apks/WireShield-v2.0-arm64-v8a.apk

# For 64-bit Android Studio Emulator / ChromeOS
adb install -r apks/WireShield-v2.0-x86_64.apk
```

### Method 2: Direct Installation on Phone
1. Download `WireShield-v2.0-arm64-v8a.apk` (or `WireShield-v2.0-universal.apk` if unsure).
2. Tap the `.apk` file in your Android File Manager.
3. Allow "Install unknown apps" if prompted.
4. Tap **Install** and launch **WireShield VPN**.

---

## 🛠️ How to Rebuild Architecture-Split APKs

To recompile release builds with ABI splitting:
```bash
# Clean and assemble release APK splits
gradle :app:assembleRelease

# Split APKs will be output to:
# app/build/outputs/apk/release/app-arm64-v8a-release.apk
# app/build/outputs/apk/release/app-armeabi-v7a-release.apk
# app/build/outputs/apk/release/app-x86-release.apk
# app/build/outputs/apk/release/app-x86_64-release.apk
# app/build/outputs/apk/release/app-universal-release.apk
```
