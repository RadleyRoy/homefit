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

# Anthropic SDK (AI coach): its models are (de)serialised by Jackson through reflection, using
# jackson-module-kotlin, which reads kotlin.Metadata. Renaming or stripping any of it breaks
# parsing at runtime with no compile-time signal, so the SDK and Jackson are kept whole.
-keepattributes Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn java.beans.**

# The SDK bundles OpenTelemetry's API, which references AutoValue's compile-only annotation.
-dontwarn com.google.auto.value.AutoValue

# Java 8 reflection types used only by the SDK's class-based structured-output schema generator
# (victools). The coach builds its schema by hand, so that code path never runs on Android.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
