# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class app.cheesino.**$$serializer { *; }
-keepclassmembers class app.cheesino.** {
    *** Companion;
}

# libVLC — native (JNI) katman yansımayla erişir; küçültme onu bozmasın.
# (Media3/ExoPlayer, Firebase, ML Kit, Play Billing kendi consumer kurallarını getirir.)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
