# Proguard / R8 Optimization Rules for Habit Bell
# =============================================
# These rules ensure critical Android framework service classes, Car App Library
# components, and Cast Framework components survive R8 minification and
# tree-shaking in release builds.

# --------------------------------------------------------------------------
# Android Auto Car App Library Services
# --------------------------------------------------------------------------
# CarAppService and its subclasses are instantiated by the Android Auto host
# via reflection from AndroidManifest.xml service declarations.
-keep class com.habitbell.app.auto.HabitBellCarAppService { <init>(); }
-keep class com.habitbell.app.auto.HabitBellCarSession { <init>(); }
-keep class com.habitbell.app.auto.HabitBellCarScreen { <init>(...); }
-keep class com.habitbell.app.auto.HabitBellMediaService { <init>(); }

# Car App Library internal classes required for host-to-app Binder IPC
-keep class androidx.car.app.** { *; }

# --------------------------------------------------------------------------
# MediaBrowserServiceCompat (Android Auto Media Integration)
# --------------------------------------------------------------------------
# MediaBrowserServiceCompat subclasses are discovered via intent-filter resolution.
-keep class * extends android.support.v4.media.MediaBrowserServiceCompat { *; }
-keep class * extends androidx.media.MediaBrowserServiceCompat { *; }

# --------------------------------------------------------------------------
# Google Cast Framework
# --------------------------------------------------------------------------
# CastOptionsProvider is instantiated by the Cast Framework via reflection
# from the meta-data declaration in AndroidManifest.xml.
-keep class com.habitbell.app.cast.CastOptionsProvider { <init>(); }
-keep class com.google.android.gms.cast.framework.** { *; }

# --------------------------------------------------------------------------
# Kotlin Coroutines
# --------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
