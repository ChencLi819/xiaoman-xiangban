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
