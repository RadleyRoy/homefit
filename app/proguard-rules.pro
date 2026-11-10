# kotlinx.serialization: keep generated serializers for the content DTOs, backup file and nav routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.radley.homefit.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.radley.homefit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
