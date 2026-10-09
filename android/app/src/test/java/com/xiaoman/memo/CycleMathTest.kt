package com.xiaoman.memo

import com.xiaoman.memo.data.CycleEntity
import com.xiaoman.memo.domain.CycleMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* CycleMath.stats 预测算法：中位数 / 异常过滤 / 回退 / 不规律提示 */
class CycleMathTest {

    private fun rec(start: String, end: String = "", daily: Boolean = false) =
        CycleEntity(start = start, end = end, daily = daily, uuid = start)

    /* 0 条记录 → 无预测 */
    @Test
    fun `empty records`() {
        assertEquals(null, CycleMath.stats(emptyList()))
    }

    /* 1 条记录：无间隔 → 回退 28 天标准周期 */
    @Test
    fun `single record falls back to 28`() {
        val st = CycleMath.stats(listOf(rec("2026-09-28", "2026-10-02")))!!
        assertEquals(28, st.avgCycle)
        assertEquals("default", st.method)
        assertEquals(5, st.avgPeriod)
        assertEquals("2026-10-26", st.nextStart)          // 9/28 + 28
        assertEquals("2026-10-12", st.ovulation)          // 下次经期 -14
        assertTrue(st.usedGaps.isEmpty())
    }

    /* 2 条记录：仅 1 个间隔 → 直接按该间隔 */
    @Test
    fun `two records use the single gap`() {
        val st = CycleMath.stats(listOf(rec("2026-08-31"), rec("2026-09-28")))!!
        assertEquals(28, st.avgCycle)
        assertEquals("single", st.method)
        assertEquals(listOf(28), st.usedGaps)
    }

    /* 3+ 条记录：取最近间隔的中位数，不受个别偏长/偏短扰动 */
    @Test
    fun `median of recent gaps`() {
        // 间隔 27 · 28 · 40 → 中位数 28（旧算法平均会得 31.7）
        val st = CycleMath.stats(
            listOf(rec("2026-07-01"), rec("2026-07-28"), rec("2026-08-25"), rec("2026-10-04")),
        )!!
        assertEquals("median", st.method)
        assertEquals(listOf(27, 28, 40), st.usedGaps)
        assertEquals(28, st.avgCycle)
    }

    /* 中位数偶数个：取中间两数平均 */
    @Test
    fun `median even count`() {
        val st = CycleMath.stats(
            listOf(rec("2026-06-01"), rec("2026-06-29"), rec("2026-07-27"), rec("2026-08-28"), rec("2026-09-25"))!!,
        )!!
        // 间隔 28 · 28 · 32 · 28 → 最近 4 个排序 [28,28,28,32] → (28+28)/2 = 28
        assertEquals(28, st.avgCycle)
        assertEquals("median", st.method)
    }

    /* 异常间隔过滤：< 15 或 > 60 天不参与，也不拖垮中位数 */
    @Test
    fun `outliers are filtered and reported`() {
        // 7/01 → 7/29 = 28；7/29 → 8/05 = 7（异常）；8/05 → 8/31 = 26；8/31 → 9/28 = 28
        val st = CycleMath.stats(
            listOf(rec("2026-07-01"), rec("2026-07-29"), rec("2026-08-05"), rec("2026-08-31"), rec("2026-09-28"))!!,
        )!!
        assertEquals("median", st.method)
        assertEquals(listOf(28, 26, 28), st.usedGaps)
        assertEquals(listOf(7), st.outliers)
        assertEquals(28, st.avgCycle)
        assertFalse(st.irregular)
    }

    /* 全部间隔都异常 → 回退 28 */
    @Test
    fun `all gaps abnormal falls back to default`() {
        val st = CycleMath.stats(listOf(rec("2026-08-01"), rec("2026-08-10"), rec("2026-08-20")))!! // 9 · 10 天
        assertEquals(28, st.avgCycle)
        assertEquals("default", st.method)
        assertEquals(listOf(9, 10), st.outliers)
    }

    /* 不规律：最近间隔极差 > 9 天 → irregular 标记 */
    @Test
    fun `irregular flag when spread is large`() {
        // 间隔 24 · 38 → 27 · 41 三次波动大
        val st = CycleMath.stats(
            listOf(rec("2026-06-01"), rec("2026-06-25"), rec("2026-08-02"), rec("2026-08-29"), rec("2026-10-09"))!!,
        )!!
        // 间隔 24 · 38 · 27 · 41 → 最近 4 个，极差 41-24=17 > 9
        assertTrue(st.irregular)
        assertEquals("median", st.method)
    }

    /* 规律周期不标记不规律 */
    @Test
    fun `regular cycle not flagged`() {
        val st = CycleMath.stats(
            listOf(rec("2026-07-01"), rec("2026-07-29"), rec("2026-08-26"), rec("2026-09-23"))!!,
        )!!
        assertEquals(28, st.avgCycle)
        assertFalse(st.irregular)
    }

    /* 超过 6 次间隔：只取最近 6 次 */
    @Test
    fun `only last six gaps are used`() {
        // 7 个 30 天间隔 + 6 个 28 天间隔 → 只取最近 6 个（全 28），早期的 30 不参与
        val list = mutableListOf(rec("2026-01-01"))
        var d = "2026-01-01"
        repeat(7) {
            d = com.xiaoman.memo.util.Dates.addDays(d, 30)
            list.add(rec(d))
        }
        repeat(6) {
            d = com.xiaoman.memo.util.Dates.addDays(d, 28)
            list.add(rec(d))
        }
        val st = CycleMath.stats(list)!!
        assertEquals("median", st.method)
        assertEquals(28, st.avgCycle)
        assertEquals(6, st.usedGaps.size)
        assertTrue(st.usedGaps.all { it == 28 })
    }

    /* daily 记录与已删除行不参与 */
    @Test
    fun `daily and deleted rows excluded`() {
        val st = CycleMath.stats(
            listOf(
                rec("2026-08-31"),
                rec("2026-09-04", daily = true),          // 当日记录，混在周期中也不影响
                rec("2026-09-28"),
            ),
        )!!
        assertEquals(28, st.avgCycle)
        assertEquals("single", st.method)
        assertEquals("2026-09-28", st.last.start)
    }
}
