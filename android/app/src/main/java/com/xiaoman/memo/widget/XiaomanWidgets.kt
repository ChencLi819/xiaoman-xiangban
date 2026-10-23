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
import com.xiaoman.memo.data.MemoEntity
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.domain.tagList
import com.xiaoman.memo.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/* ── 桌面小工具（v1.5 起共 6 个）──
   2×1 今天      ：TA 写了没 + 一键记一条
   4×2 要一起做的：共同待办 + 周期状态
   4×2 说好的    ：清单进度 + 前几件没勾的
   2×2 还有几天  ：最近的约定倒计时 + 在一起天数
   4×2 点滴      ：最近三条备忘
   2×1 今日一句  ：按日期取一句情话（一天一句，稳定不跳）
   全部复用同一套 render：一次查询，六个部件一起刷新，避免各自开线程查库。 */

class WidgetSmall : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
}

class WidgetLarge : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
}

class WidgetLists : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
}

class WidgetCountdown : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
}

class WidgetMemos : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
}

class WidgetQuote : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx)
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

private fun clip(s: String, n: Int = 26): String = if (s.length <= n) s else s.take(n - 1) + "…"

private fun render(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val db = XiaomanDb.get(ctx)
    val settings = SettingsStore(ctx)
    val dao = db.dao()
    kotlinx.coroutines.runBlocking {
        val profile = settings.profileOnce()
        val me = profile.identity
        val partner = if (me == "her") "him" else "her"
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val memos = dao.memosOnce()
        val myToday = memos.count { it.author == me && it.createdAt >= todayStart }
        val partnerToday = memos.count { it.author == partner && it.createdAt >= todayStart }
        val todos = memos.filter { it.isTodo }
        val pending = todos.filter { !it.done }.take(3)
        val doneCount = todos.count { it.done }
        val cycles = dao.cyclesAllOnce().filter { !it.deleted && !it.daily }
        val st = CycleMath.stats(cycles)
        val phase = CycleMath.phaseOf(Dates.fmt(today), st)

        /* ── 2×1 今天 ── */
        val small = RemoteViews(ctx.packageName, R.layout.widget_small)
        small.setTextViewText(R.id.w_body, if (partnerToday > 0) {
            "TA 已经写了 $partnerToday 条\n你已经写了 $myToday 条"
        } else {
            "TA 还没写\n你已经写了 $myToday 条"
        })
        small.setOnClickPendingIntent(R.id.w_action, openEditor(ctx))
        small.setOnClickPendingIntent(R.id.w_body, openApp(ctx))

        /* ── 4×2 要一起做的 ── */
        val large = RemoteViews(ctx.packageName, R.layout.widget_large)
        large.setTextViewText(R.id.w_progress, "$doneCount/${todos.size}")
        large.setTextViewText(
            R.id.w_phase,
            "周期 · " + (phase?.name?.take(2) ?: "—") + (if (st != null) " · 下次还有 ${Dates.diff(Dates.fmt(today), st.nextStart)} 天" else ""),
        )
        val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
        rows.forEachIndexed { i, id ->
            large.setTextViewText(id, if (i < pending.size) "○ ${clip(pending[i].text)}" else if (i == 0 && pending.isEmpty()) "○ 都勾掉啦，记一件新的？" else "")
        }
        large.setOnClickPendingIntent(R.id.w_action, openApp(ctx))

        /* ── 4×2 说好的：清单进度 + 前几件没勾的 ── */
        val lists = RemoteViews(ctx.packageName, R.layout.widget_lists)
        val aliveLists = dao.checklistsAllOnce().filter { !it.deleted }
        val aliveIds = aliveLists.map { it.id }.toSet()
        val items = dao.checklistItemsAllOnce().filter { !it.deleted && it.listId in aliveIds }
        val left = items.filter { !it.checked }
        lists.setTextViewText(R.id.w_progress, "${items.size - left.size}/${items.size}")
        lists.setTextViewText(
            R.id.w_hint,
            if (aliveLists.isEmpty()) "还没有清单" else "${aliveLists.size} 个清单 · 还剩 ${left.size} 件",
        )
        val lrows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
        lrows.forEachIndexed { i, id ->
            lists.setTextViewText(
                id,
                when {
                    i < left.size -> "○ ${clip(left[i].text)}"
                    i == 0 && items.isNotEmpty() -> "○ 清单都勾完啦"
                    i == 0 -> "○ 去「说好的」建一个清单"
                    else -> ""
                },
            )
        }
        lists.setOnClickPendingIntent(R.id.w_root, openApp(ctx))

        /* ── 2×2 还有几天：最近的约定 ── */
        val cd = RemoteViews(ctx.packageName, R.layout.widget_countdown)
        val plans = dao.plansAllOnce()
            .filter { !it.deleted && it.date.isNotBlank() }
            .sortedBy { it.date + it.time }
        val next = plans.firstOrNull { it.date >= Dates.fmt(today) } ?: plans.lastOrNull()
        if (next != null) {
            val days = Dates.diff(Dates.fmt(today), next.date)
            cd.setTextViewText(
                R.id.w_days,
                when {
                    days > 0 -> "$days 天"
                    days == 0L -> "今天"
                    else -> "已过"
                },
            )
            cd.setTextViewText(R.id.w_title, clip(next.title, 20) + if (next.time.isNotBlank()) " · ${next.time}" else "")
        } else {
            cd.setTextViewText(R.id.w_days, "—")
            cd.setTextViewText(R.id.w_title, "去「往后」记一个约定")
        }
        val anniversary = profile.anniversary
        cd.setTextViewText(
            R.id.w_footer,
            if (anniversary.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                "在一起 ${Dates.diff(anniversary, Dates.fmt(today)) + 1} 天"
            else "打开小满 · 往后",
        )
        cd.setOnClickPendingIntent(R.id.w_root, openApp(ctx))

        /* ── 4×2 点滴：最近三条（别人的「仅我看」不显示，和 App 内一致） ── */
        val wm = RemoteViews(ctx.packageName, R.layout.widget_memos)
        val visible = memos.filter { it.author == me || !it.privateOnly }.take(3)
        wm.setTextViewText(R.id.w_count, "${memos.count { it.author == me || !it.privateOnly }} 条")
        val mrows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
        mrows.forEachIndexed { i, id ->
            val m: MemoEntity? = visible.getOrNull(i)
            wm.setTextViewText(
                id,
                if (m != null) {
                    val tags = m.tagList()
                    (if (m.kind == "event") "📍 " else "· ") + clip(m.text) + if (tags.isNotEmpty()) "  #${tags.first()}" else ""
                } else if (i == 0) "还没有记录，记一条吧" else "",
            )
        }
        wm.setOnClickPendingIntent(R.id.w_action, openEditor(ctx))
        wm.setOnClickPendingIntent(R.id.w_root, openApp(ctx))

        /* ── 2×1 今日一句：按日期取，同一天始终同一句 ── */
        val wq = RemoteViews(ctx.packageName, R.layout.widget_quote)
        val quotes = com.xiaoman.memo.data.Seed.ACT_GROUPS.firstOrNull { it.c == "情话" }?.list.orEmpty()
        if (quotes.isNotEmpty()) {
            val idx = (today.toEpochDay() % quotes.size).toInt().let { if (it < 0) it + quotes.size else it }
            wq.setTextViewText(R.id.w_quote, quotes[idx])
            wq.setTextViewText(R.id.w_tail, "第 ${today.dayOfYear} 天 · 小满")
        }
        wq.setOnClickPendingIntent(R.id.w_root, openApp(ctx))

        mgr.updateAppWidget(ComponentName(ctx, WidgetSmall::class.java), small)
        mgr.updateAppWidget(ComponentName(ctx, WidgetLarge::class.java), large)
        mgr.updateAppWidget(ComponentName(ctx, WidgetLists::class.java), lists)
        mgr.updateAppWidget(ComponentName(ctx, WidgetCountdown::class.java), cd)
        mgr.updateAppWidget(ComponentName(ctx, WidgetMemos::class.java), wm)
        mgr.updateAppWidget(ComponentName(ctx, WidgetQuote::class.java), wq)
    }
}
