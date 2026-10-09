package com.xiaoman.memo.domain

import com.xiaoman.memo.data.CycleEntity
import com.xiaoman.memo.util.Dates
import java.time.LocalDate

/* 周期预测（严格按照生理学叙述）：
   - 周期间隔 = 相邻两次开始日的天数差
   - 异常过滤：间隔 < 15 天（疑似把经期内出血记成了新周期）或 > 60 天（疑似长期漏记）不参与平均
   - 参与计算：取过滤后最近 3–6 次间隔；≥3 次取中位数（对不规律周期比平均值更稳），
     2 次取平均，1 次直接按该间隔，0 次回退标准 28 天
   - 平均经期 = (结束-开始+1) 的平均；未填结束按 5 天估算
   - 下次经期 = 末次开始 + 预测周期；排卵通常发生在下次月经前 14 天左右（±2 天）
   —— 本模块只做记录与生理阶段描述，不提供易孕区间 / 安全期等生育相关内容 */
data class CycleStats(
    val avgCycle: Int,
    val avgPeriod: Int,
    val last: CycleEntity,
    val nextStart: String,
    val ovulation: String,
    val records: Int,
    /* 以下为「预测依据」展示用：参与计算的间隔、估算方式、异常间隔与不规律标记 */
    val usedGaps: List<Int> = emptyList(),   // 最近参与计算的间隔（天）
    val method: String = "default",          // median / mean / single / default
    val outliers: List<Int> = emptyList(),   // 被过滤的异常间隔（天）
    val irregular: Boolean = false,          // 周期波动较大
)

object CycleMath {

    const val DEFAULT_CYCLE = 28
    const val MIN_GAP = 15
    const val MAX_GAP = 60
    /* 最长与最短间隔之差超过该天数时提示「周期波动较大」 */
    const val IRREGULAR_SPREAD = 9

    fun stats(records: List<CycleEntity>): CycleStats? {
        // 只认「经期区间记录」：每日状态记录（daily）与墓碑行不参与周期计算，避免拉低均值
        val r = records.filter { !it.daily && !it.deleted }.sortedBy { it.start }
        if (r.isEmpty()) return null
        val lens = r.map { if (it.end.isNotBlank()) Dates.diff(it.start, it.end).toInt() + 1 else 5 }
        val avgP = maxOf(1, lens.sum() / lens.size)

        val gaps = (1 until r.size).map { Dates.diff(r[it - 1].start, r[it].start).toInt() }
        val ok = gaps.filter { it in MIN_GAP..MAX_GAP }
        val outliers = gaps.filter { it < MIN_GAP || it > MAX_GAP }
        val recent = ok.takeLast(6)

        val (avgC, method) = when {
            recent.size >= 3 -> median(recent) to "median"
            recent.size == 2 -> (recent[0] + recent[1] + 1) / 2 to "mean"
            recent.size == 1 -> recent[0] to "single"
            else -> DEFAULT_CYCLE to "default"
        }
        val irregular = recent.size >= 3 && recent.max() - recent.min() > IRREGULAR_SPREAD
        val last = r.last()
        val nextStart = Dates.addDays(last.start, avgC)
        val ovu = Dates.addDays(nextStart, -14)
        return CycleStats(
            avgCycle = avgC, avgPeriod = avgP, last = last,
            nextStart = nextStart, ovulation = ovu, records = r.size,
            usedGaps = recent, method = method, outliers = outliers, irregular = irregular,
        )
    }

    /* 中位数：偶数个取中间两数的平均 */
    private fun median(xs: List<Int>): Int {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /* 生理阶段：1 月经期 / 2 卵泡期 / 3 排卵期 / 4 黄体期 */
    data class Phase(val k: Int, val n: Int, val name: String)

    fun phaseOf(date: String, st: CycleStats?): Phase? {
        if (st == null) return null
        val di = Dates.diff(st.last.start, date).toInt()
        if (di < 0) return Phase(2, 1, "卵泡期")
        val curEnd = if (st.last.end.isNotBlank()) Dates.diff(st.last.start, st.last.end).toInt() else st.avgPeriod - 1
        if (di <= curEnd) return Phase(1, di + 1, "经期")
        val ovuDay = st.avgCycle - 14
        if (kotlin.math.abs(di - ovuDay) <= 2) return Phase(3, di + 1, "排卵期")
        if (di < ovuDay - 2) return Phase(2, di + 1, "卵泡期")
        return Phase(4, di + 1, "黄体期")
    }

    /* ── 完整周期的四个阶段（估算区间，严格生理学叙述）──
       经期 = 周期第 1 天起，持续平均经期天数
       卵泡期 = 经期结束次日 → 排卵期前一日
       排卵期 = 排卵日（下次经期前 14 天）± 2 天，共约 5 天
       黄体期 = 排卵期结束后 → 下次经期前一日 */
    data class PhaseSpan(val k: Int, val start: String, val end: String, val days: Int)

    fun phaseSpans(st: CycleStats): List<PhaseSpan> {
        val p0 = st.last.start
        val periodEnd = Dates.addDays(p0, st.avgPeriod - 1)
        val ovuDay = st.avgCycle - 14
        val oStart = Dates.addDays(p0, maxOf(st.avgPeriod, ovuDay - 2))
        val oEnd = Dates.addDays(p0, ovuDay + 2)
        val folStart = Dates.addDays(periodEnd, 1)
        val folEnd = Dates.addDays(oStart, -1)
        val lStart = Dates.addDays(oEnd, 1)
        val lEnd = Dates.addDays(st.nextStart, -1)
        fun span(k: Int, a: String, b: String): PhaseSpan {
            val e = maxOf(a, b)
            return PhaseSpan(k, a, e, maxOf(1, Dates.diff(a, e).toInt() + 1))
        }
        return listOf(
            span(1, p0, periodEnd),
            span(2, folStart, folEnd),
            span(3, oStart, oEnd),
            span(4, lStart, lEnd),
        )
    }

    /* 今日落在哪个阶段的区间里（按估算区间，与 phaseOf 的当日口径一致） */
    fun spanIndexOf(date: String, spans: List<PhaseSpan>): Int =
        spans.indexOfFirst { date >= it.start && date <= it.end }.let { if (it >= 0) it else -1 }

    val PH_NAMES = mapOf(1 to "经期", 2 to "卵泡期", 3 to "排卵期", 4 to "黄体期")

    /* 各阶段的生理学描述（激素与常见身体表现，不含生育建议） */
    val PH_TIPS = mapOf(
        1 to "子宫内膜脱落并随经血排出，多数人持续 3–7 天；下腹坠胀、腰酸与疲惫是常见表现，注意保暖和休息",
        2 to "雌激素水平逐步回升，子宫内膜重新增厚，精力与状态一般会一天天恢复",
        3 to "成熟卵子排出，通常在下次月经前 14 天左右；部分人会有一侧下腹轻微不适，分泌物变得清亮透明，都属正常现象",
        4 to "孕激素升高，部分人会出现乳房胀痛、情绪波动、嗜睡或食欲变化等经前表现（PMS），需要多些耐心",
    )

    /* 日历标记：p 经期 / o 排卵期 / n 预测经期 / f 卵泡期 / l 黄体期
       优先级 p > n > o > f/l（每日记录不画区间） */
    fun monthMarks(year: Int, month: Int, records: List<CycleEntity>, st: CycleStats?): Map<LocalDate, String> {
        val marks = mutableMapOf<LocalDate, String>()
        records.filter { !it.daily && !it.deleted }.forEach { r ->
            val e = r.end.ifBlank { Dates.addDays(r.start, 4) }
            var d = r.start
            while (d <= e) {
                marks[Dates.parse(d)] = "p"
                d = Dates.addDays(d, 1)
            }
        }
        if (st != null) {
            var d = st.nextStart
            val end = Dates.addDays(st.nextStart, st.avgPeriod - 1)
            while (d <= end) {
                marks.putIfAbsent(Dates.parse(d), "n")
                d = Dates.addDays(d, 1)
            }
            for (i in -2..2) marks.putIfAbsent(Dates.parse(Dates.addDays(st.ovulation, i)), "o")
            /* 卵泡期 / 黄体期：本周期估算区间内的未标记日期全部铺色 */
            phaseSpans(st).filter { it.k == 2 || it.k == 4 }.forEach { s ->
                var day = s.start
                while (day <= s.end) {
                    marks.putIfAbsent(Dates.parse(day), if (s.k == 2) "f" else "l")
                    day = Dates.addDays(day, 1)
                }
            }
        }
        return marks
    }
}
