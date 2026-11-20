# Tag - release shrinker rules.

# Room generates implementations reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Entity/DAO members are accessed through generated code and reflection.
-keepclassmembers class com.tag.routine.data.** { *; }

# Receivers and services are instantiated by the framework from the manifest.
-keep class com.tag.routine.notifications.NotificationReceiver { *; }
-keep class com.tag.routine.notifications.BootReceiver { *; }
-keep class com.tag.routine.notifications.FocusTimerReceiver { *; }
-keep class com.tag.routine.notifications.FocusTimerService { *; }
-keep class com.tag.routine.widget.TodayWidgetProvider { *; }
-keep class com.tag.routine.widget.WidgetUpdateReceiver { *; }
-keep class com.tag.routine.TagApp { *; }
-keep class com.tag.routine.MainActivity { *; }

# Kotlin metadata used by reflection-based libraries.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Compose ships its own consumer rules; silence optional references.
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.annotations.**

# DataStore / coroutines optional diagnostics.
-dontwarn kotlinx.coroutines.**
