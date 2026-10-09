package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.theme.T

/* 内置使用说明：全 App 的操作指引统一收在此页（2026-10-04 发布清理，
   各页面内的引导文案已移除，仅保留功能性提示）。 */
@Composable
fun UsageScreen(nav: NavHostController) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.height(34.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "使用说明",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.foundation.layout.Box(Modifier.height(34.dp))
        }

        Section("开始使用") {
            Item("在「我们俩 → 我的身份」里选择这台手机是谁在用，并填写双方的昵称与在一起的日子；未填写时界面会显示通用的「她 / 他」。")
            Item("外观在「我们俩 → 外观」里切换：浅色 / 深色 / 跟随系统。")
        }

        Section("此刻") {
            Item("顶部速记条输入后回车即存。")
            Item("「设为待办」的备忘会出现在首页「要一起做的」，勾选完成后保留划线痕迹。")
            Item("点任意条目进详情页，可直接改标签、开关提醒、完成/撤销、置顶或删除。")
            Item("自定义标签在编辑页的「＋ 新标签」添加、旁边的「管理」里删除；删标签不会删掉用过它的备忘。")
        }

        Section("说好的（共享清单）") {
            Item("新建清单后添加条目，条目归属会显示是谁加的；勾选状态双端同步。")
            Item("清单与条目都支持重命名和删除，删除会同步给对方。")
        }

        Section("点滴") {
            Item("全部备忘的流水，支持按标签、事件、仅我看筛选与搜索。")
            Item("「＋ 新建事件」记录去过的地方、看过的剧、一起做的事，可填发生日期（能补录过去）与备注。")
            Item("「仅我看」的条目只留在本机：不进同步快照、对方永远看不到，也不会被对方的同步覆盖。")
        }

        Section("往后（约定）") {
            Item("新建约定时日期可不选（归入「随时想做」）；有日期的按今天 / 明天 / 之后排序。")
            Item("点约定条目可直接编辑；到期当天的约定会在首页顶部提醒。")
        }

        Section("雨过 · 分寸") {
            Item("雨过记录每次吵架：一句话标题说清因为什么，想写多细就点「＋ 添加详细内容」（和点滴一样，可不写）。")
            Item("再选错误方、抽一张翻篇方式；标记翻篇后会沉淀「下次我们约好」。")
            Item("点任意一条雨过卡片可进「改一改」：改写标题与详细内容、换翻篇方式、补约定，或删除这条（删除会同步给对方）。")
            Item("分寸记录彼此的喜欢 / 讨厌 / 不能做 / 我们的规矩，可标记底线并区分归属。")
            Item("点任意一条分寸的正文可进「改一条」：改类别、改归属、改内容或删除（删除会同步给对方）。")
        }

        Section("照顾她 · 周期") {
            Item("「＋ 记一次」记录经期区间（开始、结束可事后补填），记满两次后自动给出平均周期与预测。")
            Item("「记今天状态」每天单独记一条当日心情与身体感受，不参与周期计算。")
            Item("已记录的内容点击即可修改；列表按记录当日排序。")
        }

        Section("心意") {
            Item("互动：真心话 / 大冒险 / 情侣任务 / 情话 / 默契问答，可收藏与标记玩过。")
            Item("情书：按月归档，支持配图；互动与翻篇玩法都支持自定义增删改。")
            Item("相册：从本地相册导入，标注归属。")
        }

        Section("同步（WebDAV 网盘）") {
            Item("两台设备都安装「小满·相伴」，在「我们俩 → 连接网盘」里配置同一个网盘。")
            Item("共用一个网盘账号：一台选「发起方」、一台选「加入方」，配对码相同即可，文件夹无需共享。")
            Item("各自用各自账号：把同步文件夹共享给对方（坚果云）或使用双方都可见的目录。")
            Item("坚果云需使用网页版生成的「应用密码」，不是登录密码。")
            Item("同步内容经配对码端到端加密，网盘服务商看不到明文；保存配置后会自动试连一次并给出结果。")
            Item("标了「仅我看」的备忘只留在本机，不会上传到网盘、也不会从对方同步过来。")
        }

        Section("数据") {
            Item("「数据导出（JSON）」生成完整备份文件；「数据导入」可恢复，同步快照与旧版备份都兼容。")
            Item("本机导出的备份文件包含「仅我看」条目（那是对你自己设备的完整备份），把文件发给对方前请留意。")
            Item("「清空全部数据」会删除本机所有内容，操作前有二次确认；清空不影响对方设备。")
            Item("若曾设置过同步，清空后重新配对同步可能把对方仍保留的数据合并回来。")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    XmCard {
        Text(title, style = T.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Column(
            Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content,
        )
    }
}

@Composable
private fun Item(text: String) {
    Row {
        Text("·", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 7.dp))
        Text(text, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
