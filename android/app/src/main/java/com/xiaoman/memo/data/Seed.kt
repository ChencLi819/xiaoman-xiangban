package com.xiaoman.memo.data

/* 示例内容已按用户要求移除：不再灌入任何样例数据（2026-10-03）。
   保留对象与方法签名，避免牵连调用点；首次启动仅标记 seeded。 */
object Seeder {
    suspend fun seedIfEmpty(db: XiaomanDb, settings: SettingsStore) {
        settings.markSeeded()
    }
}
