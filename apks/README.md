# WireShield VPN - Prebuilt APK Releases

This directory contains prebuilt installable Android application packages (APKs) for WireShield VPN.

---

## 📦 APK Details

| Attribute | Value |
| :--- | :--- |
| **Application Name** | WireShield VPN |
| **Package ID (`applicationId`)** | `com.aistudio.wireshieldvpn.kzp` |
| **Version Name** | `1.0` |
| **Version Code** | `1` |
| **Min SDK** | `24` (Android 7.0 Nougat) |
| **Target SDK** | `36` (Android 15 / 16 Preview) |
| **File Name** | `WireShield-VPN-debug.apk` / `app-debug.apk` |
| **File Size** | ~23.3 MB |
| **SHA-256 Checksum** | `cdbec1c6c3d19f8f1781ab2c41a8a3abe8f13a8a94a1e83775bfd32587c9d777` |
| **Build Type** | Debug (Pre-signed with debug keystore) |

---

## 📱 How to Install on Android

### Method 1: Via ADB (USB Debugging)
Connect your phone with USB debugging enabled:
```bash
adb install -r apks/WireShield-VPN-debug.apk
```

### Method 2: Direct Phone Download / File Transfer
1. Transfer `WireShield-VPN-debug.apk` to your phone via USB, Google Drive, or local sharing.
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
cp app/build/outputs/apk/debug/app-debug.apk apks/WireShield-VPN-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk apks/app-debug.apk
```
