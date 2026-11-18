package com.xiaoman.memo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.xiaoman.memo.data.AppViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject

/* 编辑页的草稿助手：写一半退出不丢内容，下次进同一个页面接着写。

   规则：
   - 草稿按「场景 + 记录」存，payload 里带 target（0 = 新建）；读的时候 target 对不上就不恢复，
     免得把「写一半的新信」盖到「正在改的老信」上；
   - 退出（返回键 / 左上角）时落一次；编辑过程中也会防抖落一次，App 被系统杀掉也不至于全丢；
   - 点「保存 / 寄出」正式落地后调 finish()：草稿清掉，退出时不再写回。 */

class DraftHandle internal constructor(
    private val vm: AppViewModel,
    private val key: String,
    private val targetId: Long,
) {
    private var storedTarget: Long? = null
    private var finished = false

    /** 进入页面时读一次；返回 null = 没有草稿，或草稿属于别的记录 */
    suspend fun read(): JSONObject? {
        val raw = vm.readDraft(key) ?: return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        storedTarget = o.optLong("target", Long.MIN_VALUE)
        return if (storedTarget == targetId) o else null
    }

    /** 正式保存成功：清掉草稿，退出时不再写回 */
    fun finish() {
        finished = true
        vm.clearDraft(key)
    }

    /** 落草稿；返回 true = 确实存下了内容（页面据此提示「已存草稿」） */
    fun flush(dirty: Boolean, json: JSONObject?): Boolean {
        if (finished) return false
        if (dirty && json != null) {
            vm.saveDraft(key, targetId, json)
            /* 记下这份草稿归谁：否则「输入 → 落盘 → 全删 → 退出」时清不掉，下次进来又把删掉的字还回来 */
            storedTarget = targetId
            return true
        }
        /* 没内容 / 没改动：把属于这条记录的旧草稿清掉，免得下次进来又冒出过期内容 */
        if (storedTarget == targetId) vm.clearDraft(key)
        return false
    }
}

@Composable
fun rememberDraft(vm: AppViewModel, key: String, targetId: Long): DraftHandle =
    remember(key, targetId) { DraftHandle(vm, key, targetId) }

/** 字符串列表的 Saver：标签、配图路径要过 Activity 重建（改字号 / 分屏）时用它 */
val StrListSaver: Saver<List<String>, Any> = listSaver(
    save = { ArrayList(it) },
    restore = { it.toList() },
)

/** 内容变化后防抖落一次 + 离开组合时兜底落一次；dirty 为 false 时只做清理 */
@Composable
fun AutoDraft(handle: DraftHandle, dirty: Boolean, snapshot: () -> JSONObject?) {
    val latestDirty by rememberUpdatedState(dirty)
    val latestSnapshot by rememberUpdatedState(snapshot)
    val stamp = snapshot()?.toString().orEmpty()
    LaunchedEffect(stamp) {
        delay(1500)
        handle.flush(latestDirty, latestSnapshot())
    }
    DisposableEffect(handle) {
        onDispose { handle.flush(latestDirty, latestSnapshot()) }
    }
}
