# Add project specific ProGuard rules here.
# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin / coroutines / reflection metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Room entities/DAOs/database (keep fields for reflection/Room)
-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class com.example.bike_.data.db.** { *; }
-keepclassmembers class com.example.bike_.data.db.** { *; }

# Domain/data models (serialization, Room, export/import, analytics)
-keep class com.example.bike_.domain.model.** { *; }
-keepclassmembers class com.example.bike_.domain.model.** { *; }
-keep class com.example.bike_.data.route.** { *; }
-keepclassmembers class com.example.bike_.data.route.** { *; }
-keep class com.example.bike_.data.workout.** { *; }
-keepclassmembers class com.example.bike_.data.workout.** { *; }
-keep class com.example.bike_.domain.analytics.** { *; }

# Enums (used by Room converters / parsing / UI)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# BLE / Nordic BLE / GATT data classes
-keep class no.nordicsemi.android.ble.** { *; }
-dontwarn no.nordicsemi.android.ble.**
-keep class com.example.bike_.data.ble.** { *; }
-keepclassmembers class com.example.bike_.data.ble.** { *; }

# osmdroid maps
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# Google Play Services location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# XML pull parser / GPX-ZWO parsing
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.**
-keep class org.kxml2.** { *; }
-dontwarn org.kxml2.**

# Keep service/receivers referenced from manifest
-keep class com.example.bike_.service.** { *; }
-keep class com.example.bike_.MainActivity { *; }
-keep class com.example.bike_.BikeApplication { *; }

# Compose/serialization-safe: keep default constructors for model classes
-keepclassmembers class com.example.bike_.** {
    <init>(...);
}

# Remove debug logging in release (optional safety)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
