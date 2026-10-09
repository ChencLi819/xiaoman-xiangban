package com.xiaoman.memo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xiaoman.memo.MainActivity
import com.xiaoman.memo.R
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/* 2×1：「TA 还没写 / 你已经写了 N 条」＋ 一键记一条（对应原型桌面小部件页） */
class WidgetSmall : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx)
    }
}

/* 4×2：要一起做的（前 3 件待办 + 进度）＋ 周期状态 */
class WidgetLarge : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx)
    }
}

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        scope.launch { render(context) }
    }
}

private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 0, Intent(ctx, MainActivity::class.java).setAction("android.intent.action.MAIN"),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun openEditor(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 1, Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse("xiaoman://editor")),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun render(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val db = XiaomanDb.get(ctx)
    val settings = SettingsStore(ctx)
    val dao = db.dao()
    kotlinx.coroutines.runBlocking {
        val profile = settings.profileOnce()
        val me = profile.identity
        val partner = if (me == "her") "him" else "her"
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val memos = dao.memosOnce()
        val myToday = memos.count { it.author == me && it.createdAt >= todayStart }
        val partnerToday = memos.count { it.author == partner && it.createdAt >= todayStart }
        val todos = memos.filter { it.isTodo }
        val pending = todos.filter { !it.done }.take(3)
        val doneCount = todos.count { it.done }
        val cycles = dao.cyclesAllOnce().filter { !it.deleted && !it.daily }
        val st = CycleMath.stats(cycles)
        val phase = CycleMath.phaseOf(Dates.fmt(LocalDate.now()), st)

        val small = RemoteViews(ctx.packageName, R.layout.widget_small)
        small.setTextViewText(R.id.w_body, if (partnerToday > 0) {
            "TA 已经写了 $partnerToday 条\n你已经写了 $myToday 条"
        } else {
            "TA 还没写\n你已经写了 $myToday 条"
        })
        small.setOnClickPendingIntent(R.id.w_action, openEditor(ctx))
        small.setOnClickPendingIntent(R.id.w_body, openApp(ctx))

        val large = RemoteViews(ctx.packageName, R.layout.widget_large)
        large.setTextViewText(R.id.w_progress, "$doneCount/${todos.size}")
        large.setTextViewText(
            R.id.w_phase,
            "周期 · " + (phase?.name?.take(2) ?: "—") + (if (st != null) " · 下次还有 ${Dates.diff(Dates.fmt(LocalDate.now()), st.nextStart)} 天" else ""),
        )
        val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
        rows.forEachIndexed { i, id ->
            large.setTextViewText(id, if (i < pending.size) "○ ${pending[i].text}" else if (i == 0 && pending.isEmpty()) "○ 都勾掉啦，记一件新的？" else "")
        }
        large.setOnClickPendingIntent(R.id.w_action, openApp(ctx))

        mgr.updateAppWidget(ComponentName(ctx, WidgetSmall::class.java), small)
        mgr.updateAppWidget(ComponentName(ctx, WidgetLarge::class.java), large)
    }
}
