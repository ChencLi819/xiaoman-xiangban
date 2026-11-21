package com.xiaoman.memo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* 未写完的草稿：写一半退出时留在本机，下次进同一个编辑页自动接着写。
   存 DataStore 而不是数据库 —— 草稿是临时状态，不参与同步（LWW / 墓碑 / 快照），
   也不进备份；一个场景一个字符串键，值是各编辑页自己拼的 JSON（字段随页面而定）。 */
private val Context.draftStore by preferencesDataStore(name = "xiaoman_drafts")

/* 各编辑场景的草稿键 */
object DraftKey {
    const val LETTER = "letter"
    const val MAIL = "mail"
    const val QUARREL = "quarrel"
    const val RULE = "rule"
    const val CYCLE = "cycle"               // 记一次（经期区间）
    const val CYCLE_DAILY = "cycle_daily"   // 记今天状态
    const val MEMO = "memo"                 // 点滴 · ＋ 新建（备忘 / 事件）
    const val MEMO_EDIT = "memo_edit"       // 点滴 · 点开某条改一改
}

class DraftStore(private val context: Context) {
    /* 独立作用域：编辑页离开组合（onDispose 兜底写入）时 viewModelScope 可能已被取消，
       草稿得落在自己的 scope 上才不会丢。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun read(key: String): String? = context.draftStore.data.first()[stringPreferencesKey(key)]

    /** 空串 = 清掉这条草稿（页面上没有内容时不该留下空草稿） */
    fun write(key: String, json: String) {
        scope.launch {
            context.draftStore.edit { p ->
                if (json.isBlank()) p.remove(stringPreferencesKey(key)) else p[stringPreferencesKey(key)] = json
            }
        }
    }

    fun clear(key: String) {
        scope.launch { context.draftStore.edit { it.remove(stringPreferencesKey(key)) } }
    }

    fun clearAll() {
        scope.launch { context.draftStore.edit { it.clear() } }
    }
}
