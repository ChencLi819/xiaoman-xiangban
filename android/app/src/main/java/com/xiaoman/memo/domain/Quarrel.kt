package com.xiaoman.memo.domain

/* 雨过 · 翻篇方式分级（等级挂钩投入成本，不挂钩狠度） */
object Quarrel {
    /* 错误方按「人」记，不按「我 / TA」：直接存 her / him。
       好处是谁写的、谁在看都不影响解读——「这次怪她」「这次怪他」在两台手机上显示的完全一致，
       也不需要知道记录的作者（quarrels 表里本来就没有 author 字段）。
       旧数据（me / ta，相对写记录的人而言）只能按本机身份解释，见 faultName。 */
    fun cnOf(identity: String): String = if (identity == "him") "他" else "她"
    fun otherCn(identity: String): String = if (identity == "him") "她" else "他"
    fun faultKeyOf(identity: String): String = if (identity == "him") "him" else "her"

    fun faultName(fault: String, meIdentity: String): String = when (fault) {
        "her" -> "这次怪她"
        "him" -> "这次怪他"
        "me" -> "这次怪" + cnOf(meIdentity)        // 旧数据：相对「我」写的，按本机身份解释
        "ta" -> "这次怪" + otherCn(meIdentity)     // 旧数据
        "both" -> "各退一步"
        "none" -> "说不清"
        else -> ""
    }

    /* 记一次时的四个选项：本机的人 = 「我」，另一个人 = 「TA」；落库存的是人（her/him） */
    fun faultOptions(me: String): List<Triple<String, String, String>> {
        val meKey = faultKeyOf(me)
        val otherKey = if (meKey == "her") "him" else "her"
        return listOf(
            Triple(meKey, "这次怪" + cnOf(me), "是我没道理"),
            Triple(otherKey, "这次怪" + otherCn(me), "是对方的问题"),
            Triple("both", "各退一步", "一人一半，各退一步"),
            Triple("none", "说不清", "其实没谁对谁错"),
        )
    }

    val EMOTIONS = listOf("委屈", "生气", "不被理解", "累了", "其实还好")

    val HEAT_NAMES = listOf("", "小别扭", "有点气", "真的生气", "很受伤", "很严重")
    val HEAT_DESC = listOf("", "撒个娇就能过去", "需要一点表示", "要认真哄一哄", "得拿出诚意", "需要一次郑重的补偿")

    val LEVEL_NAMES = listOf("", "小别扭", "有点气", "真的生气", "很受伤", "很严重")

    /* 「最常因为」统计：关键词族匹配（无需分词库，十几条量级足够稳）。
       每条记录按「简述 + 情绪」命中原因族计数；打平取最近一次；无命中算「其他」。 */
    val REASON_FAMILIES: List<Pair<String, List<String>>> = listOf(
        "家务" to listOf("家务", "洗碗", "倒垃圾", "拖地", "晾衣", "打扫", "做饭", "收拾"),
        "沟通" to listOf("沟通", "语气", "说话", "解释", "沉默", "冷淡", "报备", "提前说", "提醒"),
        "时间" to listOf("迟到", "晚归", "准时", "等", "加班", "时间", "见面", "约好"),
        "钱" to listOf("钱", "房租", "消费", "预算", "记账", "价格"),
        "家人" to listOf("妈妈", "爸", "婆", "丈母", "家人", "亲戚", "催"),
        "宠物" to listOf("猫", "狗", "宠物", "猫粮"),
        "游戏" to listOf("游戏", "比赛", "排位"),
    )

    /** items = (简述, 情绪, createdAt)；返回最常原因；无记录返回 "—"，全部无命中返回 "其他" */
    fun topReason(items: List<Triple<String, String, Long>>): String {
        if (items.isEmpty()) return "—"
        data class Fam(val name: String, var count: Int = 0, var latest: Long = 0)
        val fams = REASON_FAMILIES.map { Fam(it.first) }
        items.forEach { (text, emotions, createdAt) ->
            val t = "$text $emotions"
            fams.forEach { f ->
                val keywords = REASON_FAMILIES.first { it.first == f.name }.second
                if (keywords.any { t.contains(it) }) {
                    f.count++
                    if (createdAt > f.latest) f.latest = createdAt
                }
            }
        }
        val top = fams.filter { it.count > 0 }.maxByOrNull { it.count * 1_000_000_000_000L + it.latest / 1_000_000L }
        return top?.name ?: "其他"
    }

    /* 520 条池：等级 = 分类 base + 固定偏移（与原型一致，可复现） */
    val LV_BASE = mapOf(
        "表达" to 1, "作息" to 1, "趣味" to 1,
        "云陪" to 2, "跑腿" to 2, "投喂" to 2,
        "手作" to 3, "承诺" to 3, "寄送" to 4, "见面时" to 5,
    )
    private val LV_OFF = listOf(0, 1, 0, -1, 0, 1, -1, 0)

    data class PoolItem(val c: String, val t: String, val m: Boolean, val lv: Int)

    val POOL: List<PoolItem> = com.xiaoman.memo.data.Seed.POOL_GROUPS.flatMap { g ->
        g.list.mapIndexed { i, t ->
            var lv = (LV_BASE[g.c] ?: 2) + LV_OFF[i % 8]
            if (g.m && lv < 3) lv = 3
            PoolItem(g.c, t, g.m, lv.coerceIn(1, 5))
        }
    }

    const val NOTE = "分级是为了匹配这次的情绪，不是比谁更狠。抽到什么就做到什么，做完这件事就算翻篇——记录会留着，情绪可以放下了。"
}
