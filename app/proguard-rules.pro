# Ulysses ProGuard Rules
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class com.ulysses.app.data.db.entities.** { *; }
-dontwarn com.google.zxing.**
