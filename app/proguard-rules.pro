# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Kotlin / Coroutines
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------------------------------------------------------------------------
# WorkManager：Worker 由类名反射实例化，必须保留
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * implements androidx.work.WorkerFactory { *; }

# ---------------------------------------------------------------------------
# Glance / AppWidget
# ---------------------------------------------------------------------------
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# ---------------------------------------------------------------------------
# 数据模型：Room 通过生成的适配器读写字段，保留实体成员
# ---------------------------------------------------------------------------
-keep class com.example.countdown.data.CountdownEvent { *; }
