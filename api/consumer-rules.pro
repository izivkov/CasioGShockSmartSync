# Keep library models used for JSON serialization and event keys
-keep class org.avmedia.gshockapi.model.** { *; }
-keep class org.avmedia.gshockapi.io.TimeAdjustmentInfo { *; }
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Preserve Gson usage in the library
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
