# ════════════════════════════════════════════════════════════════
# Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
# Newsick es software propietario. Queda prohibida su copia, modificación,
# distribución o ingeniería inversa sin autorización expresa del autor.
# ════════════════════════════════════════════════════════════════

# ── Ofuscación agresiva ───────────────────────────────────────────
-optimizationpasses 7
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses 'n'
-flattenpackagehierarchy 'n'
-overloadaggressively
-renamesourcefileattribute ''
-dontpreverify

# Eliminar logs en release (no dejar trazas)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# ── Retrofit + Gson — necesario para deserialización ─────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Modelos de datos — mantener clase Y campos para Gson (necesario para deserialización)
-keep class com.makro17.newsick.**Request    { *; }
-keep class com.makro17.newsick.**Response   { *; }
-keep class com.makro17.newsick.FeedGroup    { *; }
-keep class com.makro17.newsick.FeedPhotoItem { *; }
-keep class com.makro17.newsick.NearbyUserResponse { *; }
-keep class com.makro17.newsick.NowPlayingInfo { *; }
-keep class com.makro17.newsick.PendingUploadTrack { *; }

# Clases anidadas que Gson necesita instanciar (sin estas -> ClassCastException)
-keep class com.makro17.newsick.RecommenderInfo      { *; }
-keep class com.makro17.newsick.FriendSongContributor { *; }
-keep class com.makro17.newsick.FriendSongEntry       { *; }
-keep class com.makro17.newsick.ItunesTrack            { *; }
-keep class com.makro17.newsick.ItunesSearchResponse  { *; }

# ── OkHttp ───────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Room ─────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ── Coil ─────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Google Maps / Play Services ──────────────────────────────────
-keep class com.google.android.gms.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.**

# ── Compose ──────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Servicios Android (no ofuscar nombres declarados en Manifest) ─
-keep class com.makro17.newsick.MainActivity { *; }
-keep class com.makro17.newsick.MediaListenerService { *; }

# ── Kotlin ───────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
