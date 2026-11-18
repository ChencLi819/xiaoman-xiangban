package com.xiaoman.memo.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* 桌面小工具统一刷新入口。
   v1.6.3 起只剩新一代八件套（XmWidgets.kt）：恋爱计时 / 见面倒计时 / 心情打卡 /
   情话小卡 / 每日情话 / 快速记一笔 / 雨过翻一篇 / 相伴大卡。
   旧的 v1.5 六件套（今天 / 要一起做的 / 说好的 / 还有几天 / 点滴 / 今日一句）
   已连同布局、provider 元数据一起删除，避免两代表格在「添加小工具」里并存。 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        scope.launch { renderV2(context) }
    }
}
