package com.xiaoman.memo.domain

/* 同步合并的纯函数决策层（不依赖 Android / Room，可 JVM 单元测试）。
   规则（A2）：
   - 键 = uuid（跨设备稳定）
   - 修改 = updatedAt LWW（新者胜）
   - 平手（updatedAt 相同且内容不同）= 发起方 host 胜出；guest 端被覆盖的修改计入 conflicts（日志可查，不静默）
   - 删除 = 墓碑行也是一种「内容」，同样走 LWW：较新的墓碑让删除传播，较新的修改可以把误删复活 */
object Merge {

    data class Result<T>(val toInsert: List<T>, val toUpdate: List<T>, val conflicts: Int) {
        val applied: Int get() = toInsert.size + toUpdate.size
    }

    /**
     * @param local   本地全量行（含墓碑）
     * @param remote  对端全量行（含墓碑）
     * @param key     跨设备稳定键（uuid）
     * @param stamp   updatedAt
     * @param idOf    本地主键读取
     * @param withId  把对端行内容套上本地主键（用于 UPDATE 与内容比较）
     * @param amHost  本机是否为发起方（平手时 host 胜）
     *
     * 内容比较 = 把对端行套上本地主键后与本地行做数据类相等比较（忽略两端不同的自增 id）。
     */
    fun <T> mergeLists(
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
        stamp: (T) -> Long,
        idOf: (T) -> Long,
        withId: (T, Long) -> T,
        amHost: Boolean,
    ): Result<T> {
        val byKey = local.associateBy(key)
        val ins = mutableListOf<T>()
        val upd = mutableListOf<T>()
        var conflicts = 0
        remote.forEach { r ->
            val l = byKey[key(r)]
            when {
                l == null -> ins += r
                else -> {
                    val rs = stamp(r)
                    val ls = stamp(l)
                    when {
                        rs > ls -> upd += withId(r, idOf(l))
                        rs == ls -> {
                            val normalized = withId(r, idOf(l))
                            if (normalized != l) {
                                if (amHost) conflicts++ else upd += normalized
                            }
                        }
                        // rs < ls：保留本地
                    }
                }
            }
        }
        return Result(ins, upd, conflicts)
    }
}
