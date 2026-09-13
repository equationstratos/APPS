# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# JSch (SSH) : chargement dynamique des algorithmes par nom de classe
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.**
