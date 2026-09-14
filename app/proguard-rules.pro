# Keep Room entities and DAOs
-keep class com.gamemedia.extractor.data.db.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *** serializer(...); }

# LZ4 / XZ native accessors
-keep class net.jpountz.** { *; }
-keep class org.tukaani.xz.** { *; }
