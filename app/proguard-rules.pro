# AirGap proguard rules (release builds are not minified in v2).
# Keep Nearby Connections classes if minification is enabled later.
-keep class com.google.android.gms.nearby.** { *; }
