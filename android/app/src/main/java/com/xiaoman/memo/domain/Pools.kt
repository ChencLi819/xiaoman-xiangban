package com.xiaoman.memo.domain

import com.xiaoman.memo.data.PoolOverrideEntity

/* 互动条目（内置 520 条 + 用户自定义） */
data class ActItem(val c: String, val t: String, val custom: Boolean = false)

/* 覆盖表生效逻辑：
   op=del  → 从内置池移除 origin；
   op=edit → 用 (text,cat,meet,lv) 替换 origin；
   op=add  → 追加用户自定义条目。
   对「自定义新增」条目的改/删直接作用于该 add 行本身。 */
object Pools {
    val ACT_BASE: List<ActItem> = com.xiaoman.memo.data.Seed.ACT_GROUPS.flatMap { g -> g.list.map { ActItem(g.c, it) } }

    fun effectiveAct(ov: List<PoolOverrideEntity>): List<ActItem> {
        val edits = ov.filter { it.kind == "act" && it.op == "edit" }.associateBy { it.origin }
        val dels = ov.filter { it.kind == "act" && it.op == "del" }.map { it.origin }.toSet()
        val adds = ov.filter { it.kind == "act" && it.op == "add" }
        val base = ACT_BASE.mapNotNull { item ->
            when {
                item.t in dels -> null
                edits.containsKey(item.t) -> ActItem(edits[item.t]!!.cat.ifBlank { item.c }, edits[item.t]!!.text, custom = false)
                else -> item
            }
        }
        return base + adds.map { ActItem(it.cat, it.text, custom = true) }
    }

    fun effectivePool(ov: List<PoolOverrideEntity>): List<Quarrel.PoolItem> {
        val edits = ov.filter { it.kind == "pool" && it.op == "edit" }.associateBy { it.origin }
        val dels = ov.filter { it.kind == "pool" && it.op == "del" }.map { it.origin }.toSet()
        val adds = ov.filter { it.kind == "pool" && it.op == "add" }
        val base = Quarrel.POOL.mapNotNull { item ->
            when {
                item.t in dels -> null
                edits.containsKey(item.t) -> {
                    val e = edits[item.t]!!
                    Quarrel.PoolItem(e.cat.ifBlank { item.c }, e.text, e.meet, if (e.lv in 1..5) e.lv else item.lv)
                }
                else -> item
            }
        }
        return base + adds.map { Quarrel.PoolItem(it.cat, it.text, it.meet, if (it.lv in 1..5) it.lv else 2) }
    }
}
