package com.xiaoman.memo.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaoman.memo.data.PoolOverrideEntity
import com.xiaoman.memo.data.SettingsStore
import com.xiaoman.memo.data.XiaomanDb
import com.xiaoman.memo.domain.Pools
import com.xiaoman.memo.util.Dates
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Context.widgetStore by preferencesDataStore(name = "xiaoman_widget")

/* 新一代桌面小工具统一出数（docs/widgets-integration.md §4）。
   全部只读查询；心情与「换一句」索引存本地 DataStore，不进同步快照。 */
object WidgetData {
    /* 五档与 App 共用一份定义（domain/Moods），文案不会两边漂移 */
    val MOODS = com.xiaoman.memo.domain.Moods.ALL.map { it.name }

    data class Snapshot(
        val daysTogether: Int?,          // null = 未设纪念日
        val anniversaryShort: String,    // 2023.03.14（未设为空）
        val meetDays: Long?,             // null = 没有未来计划
        val meetDateShort: String,       // 11.15
        val meetPlace: String,           // 成都
        val todayMood: String,           // MOODS 之一或空
        val quote: String,
        val quoteCount: Int,
        val meIsHer: Boolean,            // 本机身份：决定心情页用哪套插画
    )

    suspend fun snapshot(ctx: Context): Snapshot {
        val dao = XiaomanDb.get(ctx).dao()
        val profile = SettingsStore(ctx).profileOnce()
        val today = LocalDate.now()
        val todayStr = Dates.fmt(today)

        val anni = profile.anniversary
        val days: Int? = if (anni.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            runCatching { Dates.diff(anni, todayStr).toInt() + 1 }.getOrNull()
        } else null

        val plans = dao.plansAllOnce()
            .filter { !it.deleted && it.date.isNotBlank() }
            .sortedBy { it.date + it.time }
        val next = plans.firstOrNull { it.date >= todayStr }
        val meetDays: Long? = next?.let { p ->
            runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(p.date)) }.getOrNull()
        }

        val quotes = quotesOf(dao.poolOverridesOnce())
        val idx = quoteIndexOf(ctx, quotes.size, today)

        return Snapshot(
            daysTogether = days,
            anniversaryShort = if (days != null) anni.replace("-", ".") else "",
            meetDays = meetDays,
            meetDateShort = next?.date?.takeIf { it.length >= 10 }?.substring(5)?.replace('-', '.') ?: "",
            meetPlace = next?.title?.take(8) ?: "",
            todayMood = moodOnce(ctx, todayStr),
            quote = quotes.getOrNull(idx) ?: "",
            quoteCount = quotes.size,
            meIsHer = profile.identity != "him",
        )
    }

    /* 情话池：内置 109 条 + pool_overrides 生效后的「情话」分类 */
    fun quotesOf(overrides: List<PoolOverrideEntity>): List<String> =
        Pools.effectiveAct(overrides).filter { it.c == "情话" }.map { it.t }

    /* 每日稳定轮换；「换一句」换过的当日内固定 */
    suspend fun quoteIndexOf(ctx: Context, size: Int, today: LocalDate): Int {
        if (size <= 0) return 0
        val p = ctx.widgetStore.data.first()
        val day = p[KEY_QUOTE_DAY] ?: 0L
        val idx = p[KEY_QUOTE_IDX] ?: -1
        return if (day == today.toEpochDay() && idx in 0 until size) idx
        else (today.toEpochDay() % size).toInt().let { if (it < 0) it + size else it }
    }

    suspend fun shuffleQuote(ctx: Context, size: Int, current: Int): Int {
        if (size <= 0) return 0
        var n = (0 until size).random()
        if (size > 1 && n == current) n = (n + 1) % size
        ctx.widgetStore.edit {
            it[KEY_QUOTE_IDX] = n
            it[KEY_QUOTE_DAY] = LocalDate.now().toEpochDay()
        }
        return n
    }

    suspend fun moodOnce(ctx: Context, date: String): String {
        val dao = XiaomanDb.get(ctx).dao()
        val me = SettingsStore(ctx).profileOnce().identity
        val m = dao.moodOf(date, me) ?: return ""
        return com.xiaoman.memo.domain.Moods.nameOf(m.mood)
    }

    /* v1.6：心情改存 Room（进同步、进备份），不再用组件私有的 DataStore。
       mood 传中文名或 key 都行 —— 老的小组件点击传的是中文。 */
    suspend fun setMood(ctx: Context, date: String, mood: String) {
        val dao = XiaomanDb.get(ctx).dao()
        val me = SettingsStore(ctx).profileOnce().identity
        val key = com.xiaoman.memo.domain.Moods.normalize(mood)
        val now = System.currentTimeMillis()
        val cur = dao.moodOf(date, me)
        if (cur == null) {
            dao.upsertMood(
                com.xiaoman.memo.data.MoodEntity(
                    date = date, author = me, mood = key, createdAt = now, updatedAt = now,
                ),
            )
        } else {
            dao.updateMood(cur.copy(mood = key, updatedAt = now))
        }
    }

    private val KEY_QUOTE_IDX = intPreferencesKey("widget_quote_idx")
    private val KEY_QUOTE_DAY = longPreferencesKey("widget_quote_day")

    /* 一次性搬运：v1.5.x 的心情只存在组件私有的 DataStore（mood_yyyy-MM-dd），
       v1.6 起统一落 Room。升级后跑一次，把老打卡搬进 moods 表，不让它凭空消失。
       ⚠️ 老数据没记是谁记的，只能归到「当前身份」；搬运后旧键保留不动（不再读取）。 */
    suspend fun migrateLegacyMoods(ctx: Context, done: () -> Unit) {
        val dao = XiaomanDb.get(ctx).dao()
        val me = SettingsStore(ctx).profileOnce().identity
        val prefs = ctx.widgetStore.data.first().asMap()
        val now = System.currentTimeMillis()
        var n = 0
        prefs.forEach { (k, v) ->
            val key = k.name
            if (!key.startsWith("mood_")) return@forEach
            val date = key.removePrefix("mood_")
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return@forEach
            val moodName = v as? String ?: return@forEach
            if (moodName.isBlank()) return@forEach
            if (dao.moodOf(date, me) == null) {
                dao.upsertMood(
                    com.xiaoman.memo.data.MoodEntity(
                        date = date, author = me,
                        mood = com.xiaoman.memo.domain.Moods.normalize(moodName),
                        createdAt = now, updatedAt = now,
                    ),
                )
                n++
            }
        }
        if (n > 0) done()
    }
}
