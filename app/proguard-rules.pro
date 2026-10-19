-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.twovi.assistant.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
