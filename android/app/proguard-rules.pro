# Room / Kotlin
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# WorkManager 保留沉睡的 Worker
-keep class com.xiaoman.memo.remind.DailyReminderWorker { *; }
