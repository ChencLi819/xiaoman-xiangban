package com.xiaoman.memo.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* 桌面小工具统一刷新入口。
   两代并存，一次刷新一起走：
   · 新一代八件套（XmWidgets.kt）：恋爱计时 / 见面倒计时 / 心情打卡 / 情话小卡 /
     每日情话 / 快速记一笔 / 雨过翻一篇 / 相伴大卡；
   · 旧六件套（LegacyWidgets.kt）：今天 / 要一起做的 / 说好的 / 还有几天 / 点滴 / 今日一句。
   旧六件套 v1.6.3 曾整组删除（provider、布局、元数据），升级后系统重扫清单，
   判定 provider 不存在，把桌面上已放置的实例一并清掉；v1.6.7 原样注册回来。
   类名与 v1.5 逐字一致，老实例会被系统自动认回，不需要用户重新添加。 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        scope.launch {
            renderV2(context)
            renderLegacy(context)
        }
    }
}
