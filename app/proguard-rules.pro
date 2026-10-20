# Navigation routes are @Serializable classes looked up by name.
-keep @kotlinx.serialization.Serializable class com.bentusk.app.navigation.** { *; }
-keepclassmembers class com.bentusk.app.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
