# Add project specific ProGuard rules here.
# Preserve line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Keep Room Entities & Data Models for Room and Firestore serialization
-keep class com.example.data.model.** { *; }
-keepnames class com.example.data.model.** { *; }

# Keep Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.dao.** { *; }

# Keep BroadcastReceivers and WorkManager Worker
-keep class com.example.util.DueAlarmReceiver { *; }
-keep class com.example.util.BootCompletedReceiver { *; }
-keep class com.example.data.firebase.CloudCatchUpSyncWorker { *; }

# Keep Firebase Firestore & Auth model reflection
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
