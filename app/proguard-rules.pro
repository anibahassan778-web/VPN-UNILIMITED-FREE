# Project specific ProGuard rules for WireShield VPN

# Keep WireGuard Tunnel native bindings, reflection and config classes
-keep class com.wireguard.** { *; }
-keepclassmembers class com.wireguard.** { *; }

# Keep app data models, crypto, and VPN service
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }

# Jetpack Compose UI state preservation
-keep class androidx.compose.** { *; }

# OkHttp optional JSSE / Conscrypt / BouncyCastle platforms
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
