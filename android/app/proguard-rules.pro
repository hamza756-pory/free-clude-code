# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hamza.claudecode.mobile.**$$serializer { *; }
-keepclassmembers class com.hamza.claudecode.mobile.** { *** Companion; }
-keepclasseswithmembers class com.hamza.claudecode.mobile.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink (used by androidx.security)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
