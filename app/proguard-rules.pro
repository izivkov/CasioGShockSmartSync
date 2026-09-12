# Preserve names for reflection-based logic in Scratchpad and Actions
-keep class * implements org.avmedia.gshockGoogleSync.scratchpad.ScratchpadClient { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel$Action { *; }
-keep class * extends org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel$Action { *; }

# Preserve classes and method names for Utils.AppHashCode() (stack trace analysis)
# and for event subscription IDs (canonicalName / simpleName).
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class org.avmedia.gshockGoogleSync.MainEventHandler { *; }
-keep class org.avmedia.gshockGoogleSync.pairing.CompanionDevicePresenceMonitor { *; }
-keep class org.avmedia.gshockGoogleSync.services.DeviceManager { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel$* { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.ActionRunner { *; }
-keep class org.avmedia.gshockGoogleSync.ui.others.PreConnectionViewModel { *; }
-keep class org.avmedia.gshockGoogleSync.ui.common.WatchFeatureManager { *; }
-keep class org.avmedia.gshockGoogleSync.ui.actions.PhoneFinder { *; }
-keep class org.avmedia.gshockGoogleSync.data.repository.GShockRepository { *; }
-keep class org.avmedia.gshockGoogleSync.utils.Utils { *; }
-keep class org.avmedia.gshockGoogleSync.utils.Utils$* { *; }

-keep class org.avmedia.gshockGoogleSync.ui.others.ActionNameHandler { *; }

# Keep all Enums as they are often used in 'when' expressions and for serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembernames class * {
    *** onCreate(...);
    *** onResume(...);
    *** onPause(...);
    *** onCleared(...);
    *** setupEventSubscription(...);
    *** listenForUpdateRequest(...);
    *** createSubscription(...);
    *** initializeEventListener(...);
    *** subscribeToEvents(...);
    *** setupActionSubscriptions(...);
    *** setupDisconnectListener(...);
    *** PopupMessageReceiver(...);
}

# Preserve the GShockAPI library itself to avoid internal reflection issues
-keep class org.avmedia.gshockapi.** { *; }

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

# Timber rules (Optional: keep logs but allow obfuscation if needed, or strip)
# -assumenosideeffects class timber.log.Timber {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# Standard Hilt/Dagger rules (usually provided by library, but for safety):
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
