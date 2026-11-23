# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable crash diagnostics
-keepattributes SourceFile,LineNumberTable

# Keep Android entry points & BroadcastReceivers
-keep public class com.example.MainActivity { *; }
-keep public class com.example.receiver.BankSmsBroadcastReceiver { *; }

# Keep Room Database, DAOs, Entities, and KSP-generated *_Impl classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# Keep Kotlin Metadata & Coroutines
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

