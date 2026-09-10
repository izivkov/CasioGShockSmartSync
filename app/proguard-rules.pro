# Gson rules to preserve model field names used in JSON serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName { *; }

# Keep data models that are serialized/deserialized
-keep class org.avmedia.gshockapi.model.** { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.Action** { *; }
-keep class org.avmedia.gshockGoogleSync.voice.VoiceCommand** { *; }

# Timber rules (optional: remove logs in release)
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Hilt/Dagger usually provide their own rules, but common ones:
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
