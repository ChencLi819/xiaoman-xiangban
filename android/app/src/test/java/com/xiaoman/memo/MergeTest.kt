package com.xiaoman.memo

import com.xiaoman.memo.data.PlanEntity
import com.xiaoman.memo.domain.Merge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/* A1/A2 合并决策单元测试：两端各增 / 各改 / 一端删 / 同键并发改（host 平手胜）。
   用 PlanEntity 当载体（纯数据类，不依赖 Android 运行时）。 */
class MergeTest {

    private val key: (PlanEntity) -> String = { it.uuid }
    private val stamp: (PlanEntity) -> Long = { it.updatedAt }
    private val idOf: (PlanEntity) -> Long = { it.id }
    private val withId: (PlanEntity, Long) -> PlanEntity = { r, id -> r.copy(id = id) }

    private fun plan(id: Long, uuid: String, title: String, updatedAt: Long, deleted: Boolean = false) =
        PlanEntity(id = id, uuid = uuid, date = "2026-10-03", time = "", title = title, updatedAt = updatedAt, deleted = deleted)

    @Test
    fun `两端各新增 - 结果为并集`() {
        val local = listOf(plan(1L, "a", "本地新增", 100))
        val remote = listOf(plan(9L, "b", "对方新增", 100))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = true)
        assertEquals(listOf("b"), r.toInsert.map { it.uuid })
        assertTrue(r.toUpdate.isEmpty())
        assertEquals(0, r.conflicts)
    }

    @Test
    fun `对方较新修改 - 采用对方内容并保留本地主键`() {
        val local = listOf(plan(1L, "a", "旧标题", 100))
        val remote = listOf(plan(9L, "a", "新标题", 200))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = true)
        assertEquals(1, r.toUpdate.size)
        assertEquals(1L, r.toUpdate[0].id)      // 保留本地主键
        assertEquals("新标题", r.toUpdate[0].title)
    }

    @Test
    fun `本地较新修改 - 保留本地`() {
        val local = listOf(plan(1L, "a", "本地新标题", 300))
        val remote = listOf(plan(9L, "a", "旧标题", 200))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = false)
        assertTrue(r.toUpdate.isEmpty())
        assertEquals(0, r.conflicts)
    }

    @Test
    fun `同键并发改且时间戳相同 - host 胜出`() {
        // 本机是 host：平手保留本地，guest 的修改被覆盖并计数（日志可查，不静默）
        val rHost = Merge.mergeLists(
            listOf(plan(1L, "a", "host 的版本", 100)),
            listOf(plan(9L, "a", "guest 的版本", 100)),
            key, stamp, idOf, withId, amHost = true,
        )
        assertTrue(rHost.toUpdate.isEmpty())
        assertEquals(1, rHost.conflicts)

        // 本机是 guest：平手采用 host（对端）内容
        val rGuest = Merge.mergeLists(
            listOf(plan(1L, "a", "guest 的版本", 100)),
            listOf(plan(9L, "a", "host 的版本", 100)),
            key, stamp, idOf, withId, amHost = false,
        )
        assertEquals(1, rGuest.toUpdate.size)
        assertEquals("host 的版本", rGuest.toUpdate[0].title)
    }

    @Test
    fun `一端删除且墓碑较新 - 删除传播`() {
        val local = listOf(plan(1L, "a", "还活着", 100))
        val remote = listOf(plan(9L, "a", "还活着", 200, deleted = true))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = false)
        assertEquals(1, r.toUpdate.size)
        assertTrue(r.toUpdate[0].deleted)
    }

    @Test
    fun `较新的修改可以把误删复活`() {
        val local = listOf(plan(1L, "a", "复活后", 300))
        val remote = listOf(plan(9L, "a", "复活前", 200, deleted = true))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = true)
        assertTrue(r.toUpdate.isEmpty()) // 本地修改较新，不被旧墓碑删掉
    }

    @Test
    fun `本地墓碑较新 - 不被对端旧内容复活`() {
        val local = listOf(plan(1L, "a", "任意", 300, deleted = true))
        val remote = listOf(plan(9L, "a", "旧内容", 200))
        val r = Merge.mergeLists(local, remote, key, stamp, idOf, withId, amHost = false)
        assertTrue(r.toUpdate.isEmpty())
    }

    @Test
    fun `内容相同的时间戳平手 - 不算冲突`() {
        val r = Merge.mergeLists(
            listOf(plan(1L, "a", "一样的内容", 100)),
            listOf(plan(9L, "a", "一样的内容", 100)),
            key, stamp, idOf, withId, amHost = true,
        )
        assertTrue(r.toUpdate.isEmpty())
        assertEquals(0, r.conflicts)
    }
}
