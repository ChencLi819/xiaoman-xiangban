# Room / Kotlin
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# WorkManager：Worker 不在 Manifest 里注册，类名会被写进 WorkManager 自己的数据库，
# 之后靠反射实例化。R8 改名或删掉就是「任务排上了但永远不跑」，所以按基类整体保留。
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class com.xiaoman.memo.remind.DailyReminderWorker { *; }
-keep class com.xiaoman.memo.widget.WidgetDailyWorker { *; }
