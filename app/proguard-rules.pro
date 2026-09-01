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