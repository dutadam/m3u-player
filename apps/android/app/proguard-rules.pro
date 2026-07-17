# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class app.cheesino.**$$serializer { *; }
-keepclassmembers class app.cheesino.** {
    *** Companion;
}
