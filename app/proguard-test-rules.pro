# Only for the webhook test APK (-PminifyTest): shrink the libraries, keep the app's own classes whole.
# Gson reads fields by name (stored settings, Home Assistant jobs), Room and WorkManager create classes by name, and
# Hilt generates classes in the app package: none of those may be renamed or removed.
-keep class org.avmedia.gshockGoogleSync.** { *; }
-keep class org.avmedia.gshockapi.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class timber.log.Timber { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
