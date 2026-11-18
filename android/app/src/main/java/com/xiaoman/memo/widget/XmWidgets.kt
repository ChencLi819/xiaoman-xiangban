package com.xiaoman.memo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xiaoman.memo.MainActivity
import com.xiaoman.memo.R
import com.xiaoman.memo.domain.Moods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/* ── 新一代桌面小工具（docs/prototype/widgets.html · 8 个）──
   ①恋爱计时2×2 ②见面倒计时2×2 ③心情打卡2×2 ④情话小卡2×2
   ⑤每日情话4×2 ⑥快速记一笔4×1 ⑦雨过翻一篇4×1 ⑧相伴大卡3×3
   一次查询出 Snapshot，八个部件一起刷新；心情与换一句走广播回调。
   卡片铺满格子（不再缩成正方形，否则缝里会露系统 widget 底色），
   字号按实例短边 min(宽,高) 等比微调（fitScale）。 */

class DayCounterWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class MeetCountdownWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class MoodCheckWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class LoveQuoteSmallWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class LoveQuoteWideWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class QuickMemoWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class RainPassWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

class ComboWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = runBlocking { renderV2(ctx) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) =
        runBlocking { renderV2(ctx) }
}

/* 心情打卡：无界面写库 → 刷新 → Toast（五档直接落当日心情） */
class MoodActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val mood = intent.getStringExtra("mood") ?: return
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetData.setMood(ctx, LocalDate.now().toString(), mood)
                renderV2(ctx)
                Toast.makeText(ctx, "已记下", Toast.LENGTH_SHORT).show()
            } finally {
                done.finish()
            }
        }
    }
}

/* 情话「换一句」：换索引 → 当日内固定 → 重渲染 */
class QuoteRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val quotes = WidgetData.quotesOf(com.xiaoman.memo.data.XiaomanDb.get(ctx).dao().poolOverridesOnce())
                val cur = WidgetData.quoteIndexOf(ctx, quotes.size, LocalDate.now())
                WidgetData.shuffleQuote(ctx, quotes.size, cur)
                renderV2(ctx)
            } finally {
                done.finish()
            }
        }
    }
}

/* 深链统一入口：MainActivity + Navigation deeplink */
private fun route(ctx: Context, uri: String, rc: Int): PendingIntent = PendingIntent.getActivity(
    ctx, rc,
    Intent(ctx, MainActivity::class.java).setData(Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun moodPending(ctx: Context, mood: String, rc: Int): PendingIntent = PendingIntent.getBroadcast(
    ctx, rc, Intent(ctx, MoodActionReceiver::class.java).putExtra("mood", mood),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun refreshPending(ctx: Context, rc: Int): PendingIntent = PendingIntent.getBroadcast(
    ctx, rc, Intent(ctx, QuoteRefreshReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/* 正方形实例的真实边长（dp）：取系统下发尺寸的 min(宽,高)；
   Android 12 以下拿不到实例尺寸，返回 null，卡片回退铺满格子。 */
internal fun squareSide(mgr: AppWidgetManager, appWidgetId: Int): Float? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val o: Bundle = mgr.getAppWidgetOptions(appWidgetId)
    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)   // 竖屏宽度 (dp)
    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)  // 竖屏高度 (dp)
    if (w <= 0 || h <= 0) return null
    return minOf(w, h).toFloat()
}

/* 卡片铺满格子：v1.6.2 曾把方形卡按 min(宽,高) 缩成正方形，卡片比格子小 4–8dp，
   四周缝里就露出系统给的 widget 底色（浅色主题是淡紫），四角最明显 —— 用户看到的「灰角」。
   现在不再覆写宽高（布局 XML 本身就是 match_parent + layout_gravity=center），
   缝隙消失；圆角交给系统的 widget 圆角裁切，我们只烘一层更小的兜底圆角。
   注意 RemoteViews.setViewLayoutWidth 只接受具体尺寸，传 MATCH_PARENT 会被当成 -1dp 算错。 */

/* 字号随实例边长等比微调：以参考边长为 1，夹在 0.78~1.15 —— 小卡不挤、大卡不虚 */
internal fun fitScale(side: Float?, ref: Float): Float =
    if (side == null || side <= 0f) 1f else (side / ref).coerceIn(0.72f, 1.15f)

internal fun RemoteViews.sp(id: Int, baseSp: Float, k: Float) =
    setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, baseSp * k)

internal suspend fun renderV2(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val s = WidgetData.snapshot(ctx)

    /* 每个 widget 一份构建函数，逐实例构建：RemoteViews 不可复制，字号要按 id 的短边单独算。 */
    class Spec(
        val cn: ComponentName,
        val build: (Float?) -> RemoteViews,
    )
    val specs = listOf(
        Spec(ComponentName(ctx, DayCounterWidget::class.java)) { dcViews(ctx, s, it) },
        Spec(ComponentName(ctx, MeetCountdownWidget::class.java)) { mcViews(ctx, s, it) },
        Spec(ComponentName(ctx, MoodCheckWidget::class.java)) { moodViews(ctx, s, it) },
        Spec(ComponentName(ctx, LoveQuoteSmallWidget::class.java)) { qsViews(ctx, s, it) },
        Spec(ComponentName(ctx, LoveQuoteWideWidget::class.java)) { qwViews(ctx, s, it) },
        Spec(ComponentName(ctx, QuickMemoWidget::class.java)) { qmViews(ctx, it) },
        Spec(ComponentName(ctx, RainPassWidget::class.java)) { rainViews(ctx, it) },
        Spec(ComponentName(ctx, ComboWidget::class.java)) { cbViews(ctx, s, it) },
    )
    for (sp in specs) {
        for (id in mgr.getAppWidgetIds(sp.cn)) {
            val rv = sp.build(squareSide(mgr, id))
            mgr.updateAppWidget(id, rv)
        }
    }
}

/* ── ① 恋爱计时 ── */
private fun dcViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 148f)
    val dc = RemoteViews(ctx.packageName, R.layout.widget_day_counter)
    dc.setTextViewText(R.id.w_dc_days, s.daysTogether?.toString() ?: "")
    /* 110dp 最小边长下「第 1268 天」横向放不下：省掉「第」，语义仍清楚 */
    dc.setViewVisibility(R.id.w_dc_unit_l, if ((side ?: 148f) < 130f) View.GONE else View.VISIBLE)
    dc.setTextViewText(R.id.w_dc_cap, if (s.daysTogether != null) "自 ${s.anniversaryShort}" else "先去设置纪念日")
    dc.sp(R.id.w_dc_days, 32f, k)
    dc.sp(R.id.w_dc_unit_l, 12f, k)
    dc.sp(R.id.w_dc_unit_r, 12f, k)
    dc.sp(R.id.w_dc_cap, 11f, k)
    dc.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://tabs?tab=0", 101))
    return dc
}

/* ── ② 见面倒计时 ── */
private fun mcViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 148f)
    val mc = RemoteViews(ctx.packageName, R.layout.widget_meet_countdown)
    when {
        s.meetDays == null -> {
            /* 空态别用 36sp 的「—」：那会在卡上变成一根蓝色横杠；留白 + 胶囊给引导 */
            mc.setTextViewText(R.id.w_mc_days, "")
            mc.setTextViewText(R.id.w_mc_unit, "")
            mc.setTextViewText(R.id.w_mc_pill, "添加见面计划")
        }
        s.meetDays == 0L -> {
            mc.setTextViewText(R.id.w_mc_days, "今天")
            mc.setTextViewText(R.id.w_mc_pill, listOf(s.meetDateShort, s.meetPlace).filter { it.isNotBlank() }.joinToString(" · "))
        }
        else -> {
            mc.setTextViewText(R.id.w_mc_days, s.meetDays.toString())
            mc.setTextViewText(R.id.w_mc_pill, listOf(s.meetDateShort, s.meetPlace).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }
    mc.sp(R.id.w_mc_days, 32f, k)
    mc.sp(R.id.w_mc_unit, 12f, k)
    mc.sp(R.id.w_mc_pill, 11f, k)
    mc.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://tabs?tab=3", 102))
    return mc
}

/* ── ③ 今日心情（翻页版）──
   上半区是「心情页」：按当日心情取插画 + 名字 + 一句话；没记过就显示引导页。
   下面五枚是翻页按钮：点哪一档 → 写库（MoodActionReceiver）→ 重渲染，页面直接翻到那一档。 */
private fun moodViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 148f)
    val mood = RemoteViews(ctx.packageName, R.layout.widget_mood)
    val has = s.todayMood.isNotBlank()
    val key = if (has) Moods.normalize(s.todayMood) else Moods.ALL.first().key
    val idx = Moods.indexOf(key).coerceAtLeast(0)
    val item = Moods.ALL[idx]

    /* 插画：按本机身份取 her / him 那一套。
       还没记过心情时不能直接摆「甜」那一页 —— 会让空态看起来像已经选了甜：
       插画降到 35% 透明度当淡影，名字用次级墨色，文案改成引导。 */
    val who = if (s.meIsHer) "her" else "him"
    val artId = ctx.resources.getIdentifier("mood_${who}_$key", "drawable", ctx.packageName)
    if (artId != 0) mood.setImageViewResource(R.id.w_mood_art, artId)
    mood.setFloat(R.id.w_mood_art, "setAlpha", if (has) 1f else 0.35f)

    mood.setTextViewText(R.id.w_mood_name, if (has) item.name else "今天")
    mood.setTextViewText(R.id.w_mood_sub, if (has) item.sub else "点下面，记个心情")
    /* 名字恒用主墨：原来名字跟着心情变色，切一档就跳一次色，反而没有稳定的层级 */
    mood.setTextColor(R.id.w_mood_name, ContextCompat.getColor(ctx, R.color.w_ink))
    mood.sp(R.id.w_mood_name, 16f, k)
    mood.sp(R.id.w_mood_sub, 11f, k)
    /* 小卡（< 130dp）放不下「插画 + 名字 + 一句话 + 翻页条」：副文案收起，只留插画与名字 */
    val compact = (side ?: 148f) < 130f
    mood.setViewVisibility(R.id.w_mood_sub, if (compact) View.GONE else View.VISIBLE)
    mood.setInt(R.id.w_mood_sub, "setMaxLines", if (compact) 1 else 2)

    /* 插画边长跟着实例走：约为卡片边长的 1/3（148dp 卡 ≈ 50dp） */
    val artRatio = if (compact) 0.24f else 0.30f
    val artSide = ((side ?: 148f) * artRatio).coerceIn(24f, 92f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        mood.setViewLayoutWidth(R.id.w_mood_art, artSide, TypedValue.COMPLEX_UNIT_DIP)
        mood.setViewLayoutHeight(R.id.w_mood_art, artSide, TypedValue.COMPLEX_UNIT_DIP)
    }

    /* 五枚翻页按钮：未选是次级墨（统一），选中的那档才是 accent 胶囊 + 白字。
       原来五档各一种颜色（砖红/棕金/藏蓝/紫/绛红），「甜」和「气」两个红肉眼分不出，
       整排像打翻的调色盘 —— 颜色应该只标记「当前选中」，不该标记身份。 */
    val tabIds = listOf(R.id.w_mood0, R.id.w_mood1, R.id.w_mood2, R.id.w_mood3, R.id.w_mood4)
    val tabBoxes = listOf(
        R.id.w_mood_tab0, R.id.w_mood_tab1, R.id.w_mood_tab2, R.id.w_mood_tab3, R.id.w_mood_tab4,
    )
    Moods.ALL.forEachIndexed { i, m ->
        val sel = has && i == idx
        mood.setTextViewText(tabIds[i], m.name)
        mood.sp(tabIds[i], 12f, k)
        mood.setTextColor(tabIds[i], ContextCompat.getColor(ctx, if (sel) R.color.w_on_accent else R.color.w_ink2))
        /* 选中态给整格（识别点 + 字）打 accent 胶囊 */
        if (sel) mood.setInt(tabBoxes[i], "setBackgroundResource", R.drawable.w_moodpill_sel)
        else mood.setInt(tabBoxes[i], "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        mood.setOnClickPendingIntent(tabBoxes[i], moodPending(ctx, m.name, 110 + i))
    }
    mood.setOnClickPendingIntent(R.id.w_mood_page, route(ctx, "xiaoman://tabs?tab=0", 120))
    return mood
}

/* ── ④ 情话小卡 2×2 ── */
private fun qsViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 148f)
    val qs = RemoteViews(ctx.packageName, R.layout.widget_quote_small)
    qs.setTextViewText(R.id.w_qs_text, s.quote.ifBlank { "去互动库加一句情话" })
    qs.sp(R.id.w_qs_mark, 20f, k)
    /* 最小边长下引号会让正文只剩一行，收起 */
    qs.setViewVisibility(R.id.w_qs_mark, if ((side ?: 148f) < 120f) View.GONE else View.VISIBLE)
    qs.sp(R.id.w_qs_text, 12f, k)
    /* 小卡只放得下两行：边长 < 140dp 时收成 2 行，避免第三行被裁成半行 */
    qs.setInt(R.id.w_qs_text, "setMaxLines", if ((side ?: 148f) >= 140f) 3 else 2)
    /* 平衡断行：116dp 宽的窄栏里，普通断行会把「就是你。」挤成孤字行 */
    qs.setInt(R.id.w_qs_text, "setBreakStrategy", android.text.Layout.BREAK_STRATEGY_BALANCED)
    qs.setOnClickPendingIntent(R.id.w_qs_text, route(ctx, "xiaoman://library", 103))
    qs.setOnClickPendingIntent(R.id.w_qs_refresh, refreshPending(ctx, 104))
    return qs
}

/* ── ⑤ 每日情话 4×2 ── */
private fun qwViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 148f)
    val qw = RemoteViews(ctx.packageName, R.layout.widget_quote_wide)
    qw.setTextViewText(R.id.w_qw_text, s.quote.ifBlank { "去互动库加一句情话" })
    qw.setTextViewText(R.id.w_qw_count, "每日情话 · ${s.quoteCount} 条精选")
    qw.sp(R.id.w_qw_text, 14f, k)
    qw.sp(R.id.w_qw_count, 11f, k)
    /* 拉高时多给一行：4×2 → 4×3 之后 2 行会显得空 */
    qw.setInt(R.id.w_qw_text, "setMaxLines", if ((side ?: 148f) >= 190f) 3 else 2)
    qw.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://library", 105))
    qw.setOnClickPendingIntent(R.id.w_qw_refresh, refreshPending(ctx, 106))
    return qw
}

/* ── ⑥ 快速记一笔 ── */
private fun qmViews(ctx: Context, side: Float?): RemoteViews {
    val qm = RemoteViews(ctx.packageName, R.layout.widget_quick_memo)
    qm.sp(R.id.w_qm_pill, 12f, fitScale(side, 74f))
    qm.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://editor", 107))
    return qm
}

/* ── ⑦ 雨过翻一篇 ── */
private fun rainViews(ctx: Context, side: Float?): RemoteViews {
    val k = fitScale(side, 74f)
    val rain = RemoteViews(ctx.packageName, R.layout.widget_rain)
    rain.sp(R.id.w_rain_title, 12f, k)
    rain.sp(R.id.w_rain_sub, 11f, k)
    rain.setOnClickPendingIntent(R.id.w_root, route(ctx, "xiaoman://pool", 108))
    return rain
}

/* ── ⑧ 相伴大卡 ── */
private fun cbViews(ctx: Context, s: WidgetData.Snapshot, side: Float?): RemoteViews {
    val k = fitScale(side, 222f)
    val cb = RemoteViews(ctx.packageName, R.layout.widget_combo)
    cb.setTextViewText(R.id.w_cb_days, s.daysTogether?.toString() ?: "—")   /* 13sp 的短横不会成杠，空态可读 */
    cb.setTextViewText(
        R.id.w_cb_meet_text,
        when {
            s.meetDays == null -> "去记个见面计划"
            s.meetDays == 0L -> "见面 · 就是今天"
            else -> "距见面 ${s.meetDays} 天"
        },
    )
    cb.setTextViewText(R.id.w_cb_quote, s.quote.ifBlank { "去互动库加一句情话" })
    cb.sp(R.id.w_cb_days, 14f, k)
    cb.sp(R.id.w_cb_quote, 12f, k)
    cb.setInt(
        R.id.w_cb_quote,
        "setMaxLines",
        when {
            (side ?: 222f) >= 200f -> 3
            (side ?: 222f) >= 170f -> 2
            else -> 1
        },
    )
    /* 入口条高度 = 边长 × 0.19（222dp 卡 ≈ 42dp），夹在 34~52dp */
    val tileH = ((side ?: 222f) * 0.19f).coerceIn(34f, 52f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cb.setViewLayoutHeight(R.id.w_cb_tiles, tileH, TypedValue.COMPLEX_UNIT_DIP)
    }
    cb.setOnClickPendingIntent(R.id.w_cb_top, route(ctx, "xiaoman://tabs?tab=0", 109))
    cb.setOnClickPendingIntent(R.id.w_cb_meet_text, route(ctx, "xiaoman://tabs?tab=3", 111))
    cb.setOnClickPendingIntent(R.id.w_cb_quote, route(ctx, "xiaoman://library", 112))
    val narrow = (side ?: 222f) < 175f
    cb.setViewVisibility(R.id.w_cb_refresh, if (narrow) View.GONE else View.VISIBLE)
    if (!narrow) cb.setOnClickPendingIntent(R.id.w_cb_refresh, refreshPending(ctx, 113))
    cb.setOnClickPendingIntent(R.id.w_cb_write, route(ctx, "xiaoman://editor", 114))
    cb.setOnClickPendingIntent(R.id.w_cb_mood, route(ctx, "xiaoman://tabs?tab=0", 115))
    cb.setOnClickPendingIntent(R.id.w_cb_draw, route(ctx, "xiaoman://library", 116))
    cb.setOnClickPendingIntent(R.id.w_cb_rain, route(ctx, "xiaoman://pool", 117))
    return cb
}

/* 每日 00:05 日更（天数翻日 / 情话轮换）；见 XiaomanApp.scheduleWidgetDaily */
class WidgetDailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork() = runCatching { renderV2(applicationContext) }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
