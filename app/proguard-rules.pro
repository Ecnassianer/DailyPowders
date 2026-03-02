# Add project specific ProGuard rules here.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.dailypowders.data.model.** {
    *** Companion;
    *** serializer(...);
}

-if @kotlinx.serialization.Serializable class com.dailypowders.data.model.**
-keepclassmembers class com.dailypowders.data.model.<1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
