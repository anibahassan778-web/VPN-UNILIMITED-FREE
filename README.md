# WireShield VPN (Open-Source WireGuard® VPN Ecosystem)

> **مشروع VPN مفتوح المصدر بالكامل مع حماية الخصوصية، ومفتاح القفل (Kill Switch)، ومنع تسريب DNS/IPv6، وإدارة الخوادم المخصصة.**
>
> An open-source, privacy-first WireGuard® VPN system featuring a modern Jetpack Compose Android client, automated Linux server provisioning, and a lightweight Node.js/PostgreSQL management backend.

---

## 🌟 الفلسفة والنموذج المفتوح (Open-Source Philosophy)

1. **برنامج مجاني ومفتوح المصدر 100% (FOSS)**: لا توجد اشتراكات إجبارية، ولا إعلانات، ولا حد مصطنع للسرعة أو استهلاك البيانات داخل التطبيق.
2. **شفافية البنية التحتية**: "تطبيق VPN مجاني" يعني أن الكود مفتوح ومجاني تمامًا للاستخدام والتطوير، بينما تعتمد سرعة وتكلفة نقل البيانات وحجم الباندويث على مواصفات الخوادم التي يملكها أو يشغّلها المستخدم أو المجتمع.
3. **حرية استضافة الخوادم (Self-Hosted Freedom)**: يتضمن التطبيق **Server Manager** متكاملًا يتيح لك إضافة أي خادم WireGuard تملكه أو لديك تصريح باستخدامه بسهولة عبر إدخال البيانات أو استيراد ملف `.conf`.
4. **الخصوصية الصارمة (Zero-Logs)**: لا يتم تسجيل سجل المواقع، ولا استعلامات DNS، ولا محتوى الاتصالات، ولا يتم إرسال المفاتيح الخاصة (Private Keys) نهائيًا إلى الخادم؛ يتم توليدها محليًا على جهاز العميل.

---

## 🏗️ هيكلية المشروع (Project Structure)

```text
wireshield-vpn/
├── app/                        # تطبيق Android (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt     # النشاط الرئيسي وإدارة الشاشات وBackHandler
│   │   ├── crypto/             # محرك التشفير Curve25519 ومفاتيح WireGuard
│   │   ├── vpn/                # خدمة WireShieldVpnService وإدارة Tunnel
│   │   ├── data/               # مستودع الخوادم والإعدادات والتحقق من IP
│   │   ├── model/              # نماذج البيانات (VpnServer, VpnStats, VpnConfig)
│   │   └── ui/                 # واجهة المستخدم Material 3 (MainScreen, Settings, Sheets)
│   └── src/test/java/          # اختبارات الوحدة واختبارات Robolectric
├── backend/                    # واجهة برمجة التطبيقات لإدارة الخوادم (Node.js/Express)
│   ├── server.js               # خادم REST API مع Rate Limiting وBearer Auth
│   ├── schema.sql              # مخطط قاعدة البيانات (PostgreSQL / SQLite)
│   ├── Dockerfile              # بناء حاوية Docker
│   └── docker-compose.yml      # تشغيل فوري للحاوية وقاعدة البيانات
├── server/                     # نصوص وتكوين خادم WireGuard على Linux
│   ├── install-wireguard.sh    # سكريبت التثبيت التلقائي لخادم WireGuard
│   ├── wg0.conf.template       # قالب إعدادات الواجهة wg0
│   ├── sysctl-hardening.conf   # تكوين النواة (IP Forwarding, SYN cookies)
│   ├── iptables-rules.sh       # قواعد الجدار الناري وتوجيه NAT
│   ├── add-peer.sh             # إنشاء وإضافة عميل جديد وتوليد QR Code
│   └── revoke-peer.sh          # إلغاء وسحب مفتاح عميل
├── admin/                      # أداة إدارة سطر الأوامر (Admin CLI)
│   └── admin-cli.py            # سكريبت Python لإضافة/حذف الخوادم واختبار Ping
├── docs/                       # وثائق التصميم والأمان
│   ├── ARCHITECTURE.md         # المخطط المعماري الكامل وتدفق البيانات
│   ├── SECURITY_MODEL.md       # نموذج التشفير وحماية البيانات
│   ├── PRIVACY_MODEL.md        # سياسة عدم الاحتفاظ بالسجلات (Zero-Logs)
│   ├── API_SPECIFICATION.md    # توثيق REST API التفصيلي
│   └── TESTING_STRATEGY.md     # خطة الاختبارات والتحقق من التسريب
└── README.md                   # الدليل الشامل
```

---

## 📐 مخطط البنية التحتية (Architecture Diagram)

```text
+---------------------------------------------------------------------------------+
|                               ANDROID CLIENT                                    |
|                                                                                 |
|  +---------------------------------------------------------------------------+  |
|  |                 Jetpack Compose UI (Material 3 Dark Theme)                |  |
|  |  [Shield Button] [Server Selector] [Live Speed & Stats] [Kill Switch Pill] | |
|  +-------------------------------------+-------------------------------------+  |
|                                        |                                        |
|  +-------------------------------------v-------------------------------------+  |
|  |                    VpnManager & SettingsRepository                        |  |
|  |         (إدارة دورة حياة الاتصال، مؤقت المدة، وعداد استهلاك البيانات)     |  |
|  +-----------------+-----------------------------------+---------------------+  |
|                    |                                   |                        |
|  +-----------------v-----------------+       +---------v---------------------+  |
|  |      WireGuard Crypto             |       |     Android VpnService        |  |
|  |  (Curve25519 RFC 7748 Pure Kotlin)|       |  (واجهة TUN مع حظر التسريب)   |  |
|  +-----------------------------------+       |  مسارات: 0.0.0.0/0 و ::/0     |  |
|                                              +---------------+---------------+  |
+--------------------------------------------------------------|------------------+
                                                               | نفق WireGuard المشفر
                                                               | UDP Port: 51820
                                                               |
                     +-----------------------------------------+------------------+
                     |                                                            |
                     v                                                            v
+----------------------------------------+               +----------------------------------------+
|      BACKEND MANAGEMENT API            |               |      WIREGUARD LINUX NODE (wg0)        |
|                                        |               |                                        |
|  • GET /api/servers (قائمة الخوادم)    |               |  • تشفير ChaCha20-Poly1305             |
|  • POST /api/client/config (تسجيل)     |               |  • توجيه NAT Masquerade عبر iptables   |
|  • POST /api/client/revoke (إلغاء)     |               |  • حماية النواة ضد هجمات SYN Spoofing  |
|  • SQLite / PostgreSQL Database        |               |  • خوادم DNS مشفرة (1.1.1.1 / 9.9.9.9) |
+----------------------------------------+               +----------------------------------------+
```

---

## 📱 مميزات تطبيق Android

1. **واجهة تحكم حديثة (Material 3)**:
   * زر اتصال رئيسي بتأثير نبضي مضيء يعكس حالة الاتصال بدقة (متصل، جارٍ الاتصال، منقطع، خطأ).
   * عرض عنوان IP العام الحالي مع إمكانية النسخ بزر واحد.
   * بطاقة خادم متصل مع العلم واسم المدينة وزمن الاستجابة (Ping) وشريط الحمل (Load).
   * إحصائيات فورية لسرعة التحميل (Download) والرفع (Upload) مع إجمالي البيانات المستهلكة.
   * عداد مدة الاتصال (00:00:00).
2. **حماية متقدمة ضد التسريب ومراقبة الشبكة (Network Monitoring & Leak Protection)**:
   * **خدمة مراقبة الشبكة (Network Monitoring Service)**: خدمة خلفية نشطة تراقب سلامة نفق WireGuard واستجابة الخادم وحزم البيانات، وتكتشف أي انقطاع فوري أو صامت في الاتصال.
   * **مفتاح القفل الصارم (Strict Kill Switch & Blackhole Lockdown)**: عند انقطاع الـVPN، يتم حظر مرور أي اتصال بالإنترنت فورًا وبشكل تلقائي عبر واجهة TUN عازلة (Blackhole) لمنع كشف الـIP الحقيقي أو تسريب البيانات الحساسة إلى مزود الخدمة (ISP).
   * **إعادة الاتصال والاستعادة التلقائية (Auto-Recovery)**: يراقب النظام عودة توفر مسار الشبكة ويعيد إنشاء نفق WireGuard المشفر تلقائيًا بدون تدخل يدوي.
   * **DNS Leak Protection**: فرض استعلامات DNS مشفرة وموثوقة (Cloudflare/Quad9 أو خادم DNS مخصص) لمنع مزود الخدمة من التجسس على أسماء النطاقات.
   * **IPv6 Leak Protection**: التقاط وتوجيه مسار IPv6 (`::/0`) لمنع تسريب الحزم غير المشفرة عبر شبكات 4G/5G ثنائية البروتوكول.
3. **إعادة الاتصال التلقائي (Auto-Reconnect)**:
   * مراقبة الانتقال بين شبكات Wi-Fi وشبكات الهاتف الخلوي (4G/5G) عبر `ConnectivityManager.NetworkCallback` والحفاظ على أمان النفق.
4. **إدارة الخوادم المخصصة (Server Manager)**:
   * إمكانية إضافة أي خادم WireGuard خارجي أو خاص يدويًا أو عبر استيراد ملف `.conf`.
   * اختبار زمن الاستجابة (Ping) لجميع الخوادم بنقرة واحدة.
   * مزامنة الخوادم تلقائيًا مع خادم الـBackend الخاص بك (`/api/servers`).
5. **تشفير محلي بالكامل**:
   * توليد مفاتيح Curve25519 محليًا داخل جهاز العميل بدون أي اعتماديات خارجية أو JNI C blobs.

---

## ⚙️ النشر التلقائي في GitHub (Releases & Packages)

يحتوي المشروع على تكوين متكامل ومختبر لـ **GitHub Actions** عبر ملفات `.github/workflows/build.yml` و `.github/workflows/release.yml`:
* **Releases (في الشريط الجانبي الأيمن لصفحة GitHub)**:
  * يقوم الـ Workflow ببناء الـ APK وتشغيل الاختبارات، ثم إنشاء إصدار Release تلقائي (مثل `v1.0.0`) وإرفاق ملفات `WireShield-VPN-v1.0-debug.apk` و `app-debug.apk` كأصول تنزيل مباشرة.
  * يظهر الإصدار في قسم **Releases** على الصفحة الرئيسية لمستودعك على GitHub مع رابط تنزيل مباشر بنقرة واحدة.
* **Packages (في قسم Packages على GitHub)**:
  * يقوم سير العمل ببناء ونشر صورة الحاوية في **GitHub Packages / GitHub Container Registry (`ghcr.io`)** بحيث تظهر في قسم **Packages** في الشريط الجانبي لمستودع GitHub.
* **مجلد `apks/` داخل المستودع**:
  * يتوفر الـ APK المُجمّع مسبقاً داخل مجلد `apks/` في المستودع ومتاح للتنزيل المباشر أو التثبيت عبر ADB:
  ```bash
  adb install -r apks/WireShield-VPN-debug.apk
  ```

---

## 🚀 تشغيل النظام (Deployment Guide)

### 1. تشغيل خادم WireGuard على Linux (Ubuntu/Debian)

انسخ ملفات مجلد `server/` إلى خادم VPS الخاص بك:

```bash
# اعطاء صلاحيات التنفيذ
chmod +x install-wireguard.sh add-peer.sh revoke-peer.sh

# تثبيت وتشغيل WireGuard تلقائيًا
sudo ./install-wireguard.sh 51820

# لإنشاء ملف عميل جديد للموبايل:
sudo ./add-peer.sh phone-alice
```
سيقوم السكريبت بإظهار رمز QR يمكنك نسخه أو مسحه داخل تطبيق WireShield.

---

### 2. تشغيل الـBackend (Node.js & Docker)

لتشغيل واجهة برمجة التطبيقات لإدارة الخوادم:

```bash
cd backend

# التثبيت والتشغيل المحلي
npm install
npm start

# أو عبر Docker Compose بنقرة واحدة:
docker-compose up -d
```

سيعمل الخادم على المنفذ `http://localhost:3000`.

---

### 3. أداة الإدارة (Admin CLI)

```bash
cd admin
chmod +x admin-cli.py

# عرض الخوادم المسجلة
./admin-cli.py --list

# فحص كفاءة واستجابة الخوادم (Health Check)
./admin-cli.py --health

# إضافة خادم جديد
./admin-cli.py --add --name "Frankfurt 02" --country "Germany" --city "Frankfurt" --host "de2.vpn.org" --port 51820 --pubkey "YourWireGuardServerPublicKey="
```

---

### 4. بناء تطبيق Android (APK)

في بيئة تطوير Android:

```bash
# تشغيل جميع اختبارات الوحدة وRobolectric
gradle :app:testDebugUnitTest

# تجميع حزمة التطبيق Debug APK
gradle :app:assembleDebug
```

سيتم إنشاء ملف APK في: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧪 الاختبارات والجودة (Testing)

تم تضمين اختبارات شاملة تغطي:
* **`WireGuardCryptoTest`**: التحقق من دقة خوارزمية Curve25519 RFC 7748، وضبط الـClamping، وتطابق المفاتيح العامة مع الخاصة.
* **`WireGuardConfigTest`**: التحقق من صحة تحليل وتوليد ملفات إعدادات WireGuard (`[Interface]` و `[Peer]`).
* **`VpnStatsAndLeakPolicyTest`**: التحقق من حسابات السرعة وحجم البيانات المستهلكة وسياسات منع التسريب.
* **`ExampleRobolectricTest`**: اختبار تكامل حفظ الإعدادات، واستدعاء الخوادم بدون الحاجة لمحاكي فيزيائي.

لتشغيل الاختبارات:
```bash
gradle :app:testDebugUnitTest
```

---

## 🔒 ترخيص الاستخدام (License)
هذا المشروع مرخص تحت رخصة **GPLv3 / Open Source** لدعم حرية البرمجيات والخصوصية الرقمية.
WireGuard® هي علامة تجارية مسجلة لـ Jason A. Donenfeld.
