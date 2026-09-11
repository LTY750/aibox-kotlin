# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aibox.kotlin.**$$serializer { *; }
-keepclassmembers class com.aibox.kotlin.** { *** Companion; }
-keepclasseswithmembers class com.aibox.kotlin.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Ktor
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class io.ktor.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Room
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
