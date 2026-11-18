package com.xiaoman.memo.domain

/* 「心晴」五档心情（v1.6）。
   key 落库（tian / xiang / lei / sang / qi），name 用于显示，sub 是卡片里的一句话。
   App 与桌面小工具共用这一份定义，避免两边文案各写一份、日后漂移。
   插画按 key 取：res/drawable-nodpi/mood_<her|him>_<key>.png */
object Moods {

    data class Item(val key: String, val name: String, val sub: String)

    val ALL = listOf(
        Item("tian", "甜", "甜度超标，想贴贴"),
        Item("xiang", "想", "满脑子都是你"),
        Item("lei", "累", "电量不足，求充电"),
        Item("sang", "丧", "有点提不起劲"),
        Item("qi", "气", "快来哄，速来"),
    )

    fun byKey(key: String): Item? = ALL.find { it.key == key }

    /** 键 → 中文名（未知键返回空串，便于 UI 判断「没记」） */
    fun nameOf(key: String): String = byKey(key)?.name ?: ""

    fun subOf(key: String): String = byKey(key)?.sub ?: ""

    fun indexOf(key: String): Int = ALL.indexOfFirst { it.key == key }

    /** 把任意写法（key / 中文名）收敛成合法 key。
        桌面小工具的历史点击传的是中文名，这里做一次兼容。 */
    fun normalize(raw: String): String =
        when {
            ALL.any { it.key == raw } -> raw
            else -> ALL.find { it.name == raw }?.key ?: "tian"
        }
}
