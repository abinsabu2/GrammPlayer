# TDLib JNI requires the full binding surface at runtime.
-keep class org.drinkless.tdlib.** { *; }

# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.aes.grammplayer.network.tmdb.** { *; }
-keepclassmembers class com.aes.grammplayer.network.tmdb.** { <fields>; }
-keep class com.aes.grammplayer.db.model.** { *; }
-keepclassmembers class com.aes.grammplayer.db.model.** { <fields>; }
-keep class com.aes.grammplayer.provider.JsonSeedStore$** { *; }
-keepclassmembers class com.aes.grammplayer.provider.JsonSeedStore$** { <fields>; }
-keep class com.aes.grammplayer.history.HistoryEntry { *; }
-keep class com.aes.grammplayer.history.HistoryFile { *; }
-keepclassmembers class com.aes.grammplayer.history.HistoryEntry { <fields>; }
-keepclassmembers class com.aes.grammplayer.history.HistoryFile { <fields>; }

# DataStore / coroutine continuations: do not merge bookmark readers
-keep class com.aes.grammplayer.ui.features.settings.SettingsDataStore { *; }
-keep class com.aes.grammplayer.ui.features.settings.SettingsDataStore$** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Google Play Services (TLS ProviderInstaller)
-keep class com.google.android.gms.** { *; }