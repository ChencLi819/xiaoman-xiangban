package com.xiaoman.memo.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun today(): LocalDate = LocalDate.now()
    fun fmt(d: LocalDate): String = d.format(ISO)
    fun parse(s: String): LocalDate = LocalDate.parse(s, ISO)
    fun addDays(s: String, n: Int): String = parse(s).plusDays(n.toLong()).format(ISO)
    fun diff(a: String, b: String): Long = ChronoUnit.DAYS.between(parse(a), parse(b))
    fun cn(s: String): String = "${parse(s).monthValue}月${parse(s).dayOfMonth}日"
    fun cnFull(s: String): String = parse(s).let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" }

    /* ── 年份感知格式化 ──
       同年只写「M月d日」，跨年必须带年「yyyy年M月d日」。
       历史记录、预测区间跨年时靠它区分，避免第二年出现两个「11月6日」。 */
    fun yearOf(s: String): Int = parse(s).year

    /** 与 refYear 同年 →「11月6日」；否则 →「2025年11月6日」 */
    fun cnY(s: String, refYear: Int = today().year): String {
        if (s.isBlank()) return ""
        val d = parse(s)
        return if (d.year == refYear) "${d.monthValue}月${d.dayOfMonth}日"
        else "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }

    /** 永远带年（用于列表年份分组之外的强区分场景） */
    fun cnAbs(s: String): String = if (s.isBlank()) "" else cnFull(s)

    /** 区间文案：同年简写月日，跨年两侧都带年，如「2026年12月28日 – 2027年1月4日」 */
    fun cnRange(a: String, b: String, refYear: Int = today().year): String {
        if (a.isBlank()) return ""
        if (b.isBlank()) return cnY(a, refYear)
        val ea = yearOf(a); val eb = yearOf(b)
        return if (ea == eb && ea == refYear) "${cn(a)} – ${cn(b)}"
        else if (ea == eb) "${ea}年${cn(a)} – ${cn(b)}"
        else "${ea}年${cn(a)} – ${eb}年${cn(b)}"
    }

    /** 年份标签：「2026年」 */
    fun yearLabel(y: Int): String = "${y}年"

    /* ── 时间戳 → 日期 / 时刻（云笺等「带时分」场景） ── */
    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun dateOf(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(ISO)

    fun timeOf(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(HM)

    /** 「2026年10月24日 21:07」；同年可只写月日 */
    fun stampCn(epochMs: Long, withYear: Boolean = true): String {
        if (epochMs <= 0) return ""
        val s = dateOf(epochMs)
        return (if (withYear) cnFull(s) else cn(s)) + " " + timeOf(epochMs)
    }

    private val WD = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    fun weekdayCn(d: LocalDate): String = WD[d.dayOfWeek.value - 1]

    /* 「10月2日 星期五」首页副标题 */
    fun headerToday(): String {
        val d = today()
        return "${d.monthValue}月${d.dayOfMonth}日 ${weekdayCn(d)}"
    }

    /* 相对时间：刚刚 / N 分钟前 / N 小时前 / 昨天 / 周几 / M月d日 */
    fun relTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = nowMs - epochMs
        val min = diff / 60000
        if (min < 1) return "刚刚"
        if (min < 60) return "$min 分钟前"
        val h = min / 60
        if (h < 24) return "$h 小时前"
        val d = LocalDate.now().minusDays(1)
        val date = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        if (date == d) return "昨天"
        val d7 = LocalDate.now().minusDays(7)
        if (date.isAfter(d7)) return weekdayCn(date)
        return "${date.monthValue}月${date.dayOfMonth}日"
    }

    fun dayLabel(date: LocalDate): String = when {
        date == today() -> "今天"
        date == today().plusDays(1) -> "明天"
        else -> "${date.monthValue}/${date.dayOfMonth} ${WD[date.dayOfWeek.value - 1].removePrefix("星期")}"
    }
}
