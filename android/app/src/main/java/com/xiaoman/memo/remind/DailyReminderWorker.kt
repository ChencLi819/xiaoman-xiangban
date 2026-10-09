package com.xiaoman.memo.remind

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xiaoman.memo.MainActivity
import com.xiaoman.memo.R
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.util.Dates

object Notifications {
    const val CH_REMINDER = "reminders"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "提醒与通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "今日待办、约定与周期提醒"
            },
        )
    }

    fun notify(ctx: Context, id: Int, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

/* 每日检查：今日约定 / 未完成待办 / 经期可能开始（分 channel 可关） */
class DailyReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = XiaomanDb.get(ctx)
        val dao = db.dao()
        val today = Dates.fmt(Dates.today())

        val plans = dao.plansAllOnce().filter { !it.deleted }
        val todayPlans = plans.filter { it.date == today && it.remind }
        if (todayPlans.isNotEmpty()) {
            Notifications.notify(ctx, 1001, "今天有 ${todayPlans.size} 件事", todayPlans.joinToString("、") { it.title }.take(80))
        }

        val todos = dao.memosOnce().filter { it.isTodo && !it.done }
        if (todos.isNotEmpty()) {
            Notifications.notify(ctx, 1002, "还有 ${todos.size} 件待办", todos.take(2).joinToString("、") { it.text }.take(80))
        }

        val cycles = dao.cyclesAllOnce().filter { !it.deleted && !it.daily }
        val st = CycleMath.stats(cycles)
        if (st != null) {
            val left = Dates.diff(today, st.nextStart).toInt()
            if (left == 0) {
                Notifications.notify(ctx, 1003, "周期提醒", "今天可能是经期开始日，多关心一下她")
            }
        }
        return Result.success()
    }
}
