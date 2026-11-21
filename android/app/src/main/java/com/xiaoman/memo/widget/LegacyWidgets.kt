package com.xiaoman.memo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.xiaoman.memo.R
import com.xiaoman.memo.data.MemoEntity
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.domain.tagList
import com.xiaoman.memo.util.Dates
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

/* ── 旧六件套（v1.5 起 6 个）──
   2×1 今天      ：TA 写了没 + 我今天写了几条
   4×2 要一起做的：共同待办 + 周期状态
   4×2 说好的    ：清单进度 + 前几件没勾的
   2×2 还有几天  ：最近的约定倒计时
   4×2 点滴      ：最近三条备忘
   2×1 今日一句  ：按日期取一句情话（一天一句，稳定不跳）

   ⚠️ 这 6 个类名必须与 v1.5 逐字一致。AppWidget 实例是按 ComponentName
   （包名 + 类名）认领的，类名一变系统就当新部件，桌面上已有的实例会被判为
   「provider 已不存在」而一并移除 —— 这正是 v1.6.3 用户看到的「卡片凭空消失」。
   v1.6.3 把 6 个 <receiver> 从清单里删掉，系统重扫清单后就按平台规则清掉了实例；
   App 侧没有任何 API 能拦住。v1.6.7 原样注册回来，老实例即自动认回。

   与新一代（XmWidgets.kt）的关系：两代共存、各自刷新，互不干扰。
   本文件只承载旧六件套，WidgetUpdater / renderV2 仍在新一代那边。 */

class WidgetSmall : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

class WidgetLarge : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

class WidgetLists : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

class WidgetCountdown : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

class WidgetMemos : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

class WidgetQuote : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        runBlocking { renderLegacy(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderLegacy(ctx) }
}

/* 2×1「今天」在 40dp（声明下限）时只放得下主行，次行收起；
   4×2 的「要一起做的 / 点滴」在 110dp（声明下限）时收起底部按钮，三行内容才留得下。
   注意 4×2 卡的短边就是高度（110dp），所以按钮的阈值必须比 110 大一点点才生效，
   又必须小于常见高度（148dp 档）—— 取 120dp 恰好把「声明下限」与「正常档」分开。
   阈值与 tools/render_widget_designs.py 的 HIDE_BELOW 一一对应，改一处要同步另一处。 */
private const val HIDE_SUB_BELOW = 48f
private const val HIDE_ACTION_BELOW = 120f

/* 空行收成 GONE：GONE 的 View 不占任何空间（含 layout_marginTop），
   比留一个空 TextView 少 17dp 空档。旧版三行恒占位，两条记录时卡面下半截是空的。 */
private fun RemoteViews.setTextOrHide(id: Int, text: String) {
    setTextViewText(id, text)
    setViewVisibility(id, if (text.isBlank()) View.GONE else View.VISIBLE)
}

private fun clip(s: String, n: Int = 26): String = if (s.length <= n) s else s.take(n - 1) + "…"

/* 一次查询，六个部件一起刷新（各自开线程查库会打六遍全表）。
   逐实例构建：RemoteViews 不可复制，按实例尺寸收起元素的判断得按 id 单独做。 */
internal suspend fun renderLegacy(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val cns = listOf(
        ComponentName(ctx, WidgetSmall::class.java),
        ComponentName(ctx, WidgetLarge::class.java),
        ComponentName(ctx, WidgetLists::class.java),
        ComponentName(ctx, WidgetCountdown::class.java),
        ComponentName(ctx, WidgetMemos::class.java),
        ComponentName(ctx, WidgetQuote::class.java),
    )
    val idsByCn = cns.map { it to mgr.getAppWidgetIds(it) }
    /* 一个旧卡都没放（v1.6.3 之后的新用户多半如此）：省掉整轮查库 */
    if (idsByCn.all { it.second.isEmpty() }) return

    val dao = XiaomanDb.get(ctx).dao()
    val profile = SettingsStore(ctx).profileOnce()
    val me = profile.identity
    val partner = if (me == "her") "him" else "her"
    val today = LocalDate.now()
    val todayStr = Dates.fmt(today)
    val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val memos = dao.memosOnce()
    val myToday = memos.count { it.author == me && it.createdAt >= todayStart }
    val partnerToday = memos.count { it.author == partner && it.createdAt >= todayStart }
    val todos = memos.filter { it.isTodo }
    val pending = todos.filter { !it.done }.take(3)
    val doneCount = todos.count { it.done }

    val cycles = dao.cyclesAllOnce().filter { !it.deleted && !it.daily }
    val st = CycleMath.stats(cycles)
    val phase = CycleMath.phaseOf(todayStr, st)

    val visibleMemos = memos.filter { it.author == me || !it.privateOnly }
    val visibleTotal = visibleMemos.size
    val recent = visibleMemos.take(3)

    val plans = dao.plansAllOnce()
        .filter { !it.deleted && it.date.isNotBlank() }
        .sortedBy { it.date + it.time }
    val nextPlan = plans.firstOrNull { it.date >= todayStr } ?: plans.lastOrNull()

    val quotes = WidgetData.quotesOf(dao.poolOverridesOnce())
    val quote = quotes.getOrNull(WidgetData.quoteIndexOf(ctx, quotes.size, today)).orEmpty()

    /* 清单卡要的两张表也在这里一次查完：下面 Spec 的 build 是普通 lambda，
       不是 suspend，lambda 里不能再调 DAO 的挂起方法。 */
    val aliveLists = dao.checklistsAllOnce().filter { !it.deleted }
    val aliveListIds = aliveLists.map { it.id }.toSet()
    val listItems = dao.checklistItemsAllOnce().filter { !it.deleted && it.listId in aliveListIds }
    val leftItems = listItems.filter { !it.checked }

    /* 每个部件一份构建函数，入参 = 实例短边 min(宽, 高)（dp），拿不到时给 null */
    class Spec(val cn: ComponentName, val build: (Float?) -> RemoteViews)
    val specs = listOf(
        Spec(cns[0]) { side ->
            val k = side ?: 74f
            val rv = RemoteViews(ctx.packageName, R.layout.widget_small)
            rv.setTextViewText(
                R.id.w_body,
                if (partnerToday > 0) "TA 已经写了 $partnerToday 条" else "TA 还没写",
            )
            rv.setTextViewText(R.id.w_sub, "你已经写了 $myToday 条")
            /* 40dp 高只放得下主行；整卡仍是两行状态，不塞按钮 */
            if (k < HIDE_SUB_BELOW) rv.setViewVisibility(R.id.w_sub, View.GONE)
            rv.setOnClickPendingIntent(R.id.w_body, route(ctx, "xiaoman://editor", 201))
            rv.setOnClickPendingIntent(R.id.w_sub, route(ctx, "xiaoman://tabs?tab=0", 202))
            rv
        },
        Spec(cns[1]) { side ->
            val k = side ?: 148f
            val rv = RemoteViews(ctx.packageName, R.layout.widget_large)
            /* 一件待办都没有时「0/0」看着像坏了，收起进度 */
            rv.setTextOrHide(R.id.w_progress, if (todos.isEmpty()) "" else "$doneCount/${todos.size}")
            rv.setTextViewText(
                R.id.w_phase,
                "周期 · " + (phase?.name?.take(2) ?: "未记") +
                    if (st != null) " · 下次还有 ${Dates.diff(todayStr, st.nextStart)} 天" else "",
            )
            val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
            rows.forEachIndexed { i, id ->
                rv.setTextOrHide(
                    id,
                    when {
                        i < pending.size -> "○ ${clip(pending[i].text)}"
                        i == 0 && todos.isEmpty() -> "○ 还没有要一起做的事"
                        i == 0 -> "○ 都勾掉啦，记一件新的？"
                        else -> ""
                    },
                )
            }
            /* 110dp 高时收起按钮，三行待办才留得下 */
            if (k < HIDE_ACTION_BELOW) rv.setViewVisibility(R.id.w_action, View.GONE)
            rv.setOnClickPendingIntent(R.id.w_action, route(ctx, "xiaoman://tabs?tab=0", 203))
            rv
        },
        Spec(cns[2]) { _ ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_lists)
            /* 空清单时「0/0」看着像坏了，收起进度，只留引导 */
            rv.setTextOrHide(R.id.w_progress, if (listItems.isEmpty()) "" else "${listItems.size - leftItems.size}/${listItems.size}")
            rv.setTextViewText(
                R.id.w_hint,
                if (aliveLists.isEmpty()) "还没有清单" else "${aliveLists.size} 个清单 · 还剩 ${leftItems.size} 件",
            )
            val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
            rows.forEachIndexed { i, id ->
                rv.setTextOrHide(
                    id,
                    when {
                        i < leftItems.size -> "○ ${clip(leftItems[i].text)}"
                        i == 0 && aliveLists.isEmpty() -> "○ 去「说好的」建一个清单"
                        i == 0 -> "○ 清单都勾完啦"
                        else -> ""
                    },
                )
            }
            rv.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://tabs?tab=1", 204))
            rv
        },
        Spec(cns[3]) { _ ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_countdown)
            val anni = profile.anniversary
            val hasAnni = anni.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            if (nextPlan != null) {
                val days = Dates.diff(todayStr, nextPlan.date)
                rv.setTextViewText(
                    R.id.w_days,
                    when {
                        days > 0 -> "$days 天"
                        days == 0L -> "今天"
                        else -> "已过"
                    },
                )
                rv.setTextViewTextSize(R.id.w_days, TypedValue.COMPLEX_UNIT_SP, 30f)
                rv.setTextViewText(
                    R.id.w_title,
                    clip(nextPlan.title, 20) + if (nextPlan.time.isNotBlank()) " · ${nextPlan.time}" else "",
                )
                rv.setTextViewText(R.id.w_cd_label, "最近的约定")
            } else {
                /* 空态不给 30sp 的「—」：那在卡上就是一根红杠。缩到 18sp 并用「暂无」 */
                rv.setTextViewText(R.id.w_days, "暂无")
                rv.setTextViewTextSize(R.id.w_days, TypedValue.COMPLEX_UNIT_SP, 18f)
                rv.setTextViewText(R.id.w_title, "去「往后」记一个约定")
                /* 没有约定时把标签位让给「在一起 N 天」，卡面不至于只剩一句引导 */
                rv.setTextViewText(
                    R.id.w_cd_label,
                    if (hasAnni) "在一起 ${Dates.diff(anni, todayStr) + 1} 天" else "往后",
                )
            }
            rv.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://tabs?tab=3", 205))
            rv
        },
        Spec(cns[4]) { side ->
            val k = side ?: 148f
            val rv = RemoteViews(ctx.packageName, R.layout.widget_memos)
            rv.setTextViewText(R.id.w_count, "$visibleTotal 条")
            val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
            rows.forEachIndexed { i, id ->
                val m: MemoEntity? = recent.getOrNull(i)
                rv.setTextOrHide(
                    id,
                    if (m != null) {
                        val tags = m.tagList()
                        (if (m.kind == "event") "📍 " else "· ") + clip(m.text) +
                            if (tags.isNotEmpty()) "  #${tags.first()}" else ""
                    } else if (i == 0) "还没有记录，记一条吧" else "",
                )
            }
            if (k < HIDE_ACTION_BELOW) rv.setViewVisibility(R.id.w_action, View.GONE)
            rv.setOnClickPendingIntent(R.id.w_action, route(ctx, "xiaoman://editor", 206))
            rv.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://tabs?tab=2", 207))
            rv
        },
        Spec(cns[5]) { side ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_quote)
            rv.setTextViewText(R.id.w_quote, quote.ifBlank { "去互动库加一句情话" })
            /* 40dp 高只放得下一行，与渲染脚本的 w_quote 特判一致 */
            rv.setInt(R.id.w_quote, "setMaxLines", if ((side ?: 74f) < 50f) 1 else 2)
            rv.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://library", 208))
            rv
        },
    )

    for ((i, sp) in specs.withIndex()) {
        for (id in idsByCn[i].second) {
            mgr.updateAppWidget(id, sp.build(squareSide(mgr, id)))
        }
    }
}
