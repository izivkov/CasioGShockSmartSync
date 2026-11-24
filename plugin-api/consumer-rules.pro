# Plugins are found by ServiceLoader and call the interface by name.
-keep class org.avmedia.smartsync.plugin.api.** { *; }
-keep class * implements org.avmedia.smartsync.plugin.api.WatchPlugin { <init>(); *; }
