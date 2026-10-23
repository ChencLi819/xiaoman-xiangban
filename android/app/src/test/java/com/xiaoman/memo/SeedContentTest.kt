package com.xiaoman.memo

import com.xiaoman.memo.data.MemoEntity
import com.xiaoman.memo.data.Seed
import com.xiaoman.memo.domain.Pools
import com.xiaoman.memo.domain.Quarrel
import com.xiaoman.memo.domain.joinTags
import com.xiaoman.memo.domain.normalizeTagList
import com.xiaoman.memo.domain.primaryTag
import com.xiaoman.memo.domain.splitTags
import com.xiaoman.memo.domain.tagList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/* v1.5 内容与多标签的数据层验收：
   - 互动四项各 520 条、情话保持原样；
   - 翻篇方式五级各 520 条、全库 2600 条且文案唯一；
   - tags 列与旧 tag 字段的兼容读写。 */
class SeedContentTest {

    @Test
    fun actPoolsAreFilledTo520ExceptLoveWords() {
        val byCat = Pools.ACT_BASE.groupBy { it.c }
        listOf("真心话", "大冒险", "情侣任务", "默契问答").forEach { cat ->
            assertEquals("$cat 应为 520 条", 520, byCat[cat]?.size ?: 0)
        }
        // 情话不扩充：与 SeedData 里的原始条数一致
        val loveOrigin = Seed.ACT_GROUPS.first { it.c == "情话" }.list.size
        assertEquals(loveOrigin, byCat["情话"]?.size ?: 0)
    }

    @Test
    fun actTextsAreUniqueWithinCategory() {
        Pools.ACT_BASE.groupBy { it.c }.forEach { (cat, list) ->
            assertEquals("$cat 有重复条目", list.size, list.map { it.t }.distinct().size)
        }
    }

    @Test
    fun poolHas520PerLevel() {
        val byLv = Quarrel.POOL.groupBy { it.lv }
        (1..5).forEach { lv ->
            assertEquals("第 $lv 级应为 520 条", 520, byLv[lv]?.size ?: 0)
        }
        assertEquals(2600, Quarrel.POOL.size)
        assertEquals("翻篇文案应全局唯一", 2600, Quarrel.POOL.map { it.t }.distinct().size)
        // 各级都要有「现在就能做」和「下次见面」两种兑现时机，否则筛选会空
        (1..5).forEach { lv ->
            val list = byLv[lv].orEmpty()
            assertTrue("第 $lv 级缺少「现在就能做」", list.any { !it.m })
            assertTrue("第 $lv 级缺少「下次见面」", list.any { it.m })
        }
    }

    @Test
    fun tagsRoundTripAndFallbackToLegacyTag() {
        assertEquals(listOf("生活", "想买"), splitTags("生活,想买"))
        assertEquals(emptyList<String>(), splitTags(""))
        assertEquals("生活,想买", joinTags(listOf("生活", "想买", "生活")))
        assertEquals("生活", primaryTag(emptyList()))
        assertEquals(listOf("生活", "想买"), normalizeTagList(listOf(" 生活 ", "想买", "")))
        // v5 及更早的数据只有 tag：tagList() 必须能读出来
        val legacy = MemoEntity(text = "旧数据", tag = "想法", author = "her", createdAt = 0, updatedAt = 0)
        assertEquals(listOf("想法"), legacy.tagList())
        // 新数据以 tags 为准，tag 只保留主标签
        val multi = legacy.copy(tag = "生活", tags = "生活,想买")
        assertEquals(listOf("生活", "想买"), multi.tagList())
    }
}
