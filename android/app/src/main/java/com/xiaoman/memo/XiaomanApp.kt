package com.xiaoman.memo

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xiaoman.memo.data.Seeder
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.remind.DailyReminderWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XiaomanApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        val db = XiaomanDb.get(this)
        val settings = SettingsStore(this)
        appScope.launch {
            Seeder.seedIfEmpty(db, settings)
        }
        com.xiaoman.memo.remind.Notifications.ensureChannels(this)
        scheduleDaily()
        maybeAutoSync()
    }

    /* 全局崩溃日志：写到应用外部可见目录，闪退后可在文件管理器里取到，
       路径：Android/data/<包名>/files/crash_log.txt（sync 包为 com.xiaoman.memo.sync） */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = java.io.File(dir, "crash_log.txt")
                f.appendText(
                    "\n==== " + java.time.LocalDateTime.now() + " thread=" + t.name + " ====\n" +
                        android.util.Log.getStackTraceString(e),
                )
                if (f.length() > 200_000) f.writeText(f.readText().takeLast(100_000))
            }
            prev?.uncaughtException(t, e)
        }
    }

    /* 同步端：打开 App 自动同步一次（已配对才有动作） */
    private fun maybeAutoSync() {
        if (!BuildConfig.SYNC_ENABLED) return
        appScope.launch {
            runCatching {
                val db = XiaomanDb.get(this@XiaomanApp)
                com.xiaoman.memo.data.SyncEngine.syncNow(this@XiaomanApp, db, SettingsStore(this@XiaomanApp))
            }
        }
    }

    private fun scheduleDaily() {
        val req = PeriodicWorkRequestBuilder<DailyReminderWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(initialDelayToNextCheck(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "xiaoman_daily",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    /* 对齐到下一个 08:30 检查点 */
    private fun initialDelayToNextCheck(): Long {
        val now = java.time.LocalDateTime.now()
        var next = now.toLocalDate().atTime(8, 30)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return java.time.Duration.between(now, next).toMinutes()
    }
}
