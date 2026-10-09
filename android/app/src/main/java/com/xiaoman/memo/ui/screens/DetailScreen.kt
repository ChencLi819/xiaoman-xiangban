package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.ui.components.Avatar
import com.xiaoman.memo.ui.components.Tick
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates

@Composable
fun DetailScreen(nav: NavHostController, vm: AppViewModel, memoId: Long) {
    val c = Xm
    val ui by vm.ui.collectAsState()
    var confirmDel by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showKindPicker by remember { mutableStateOf(false) }

    /* ⚠️ 直接订阅数据库流：任何变更（完成/置顶/改标签/开关提醒）都会即时刷新。
       旧实现是一次性读取 + 按「条数变化」刷新，导致点了完成页面毫无反应（bug 2 的根因）。 */
    val m by remember(memoId) { vm.memoFlow(memoId) }.collectAsState(initial = null)

    val tags = remember(ui.customTags) {
        com.xiaoman.memo.domain.allTags(ui.customTags.map { it.name })
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "一条备忘",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { confirmDel = true }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("···", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
        }

        if (m != null) {
            val memo = m!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm.nameOf(memo.author).take(1), if (memo.author == "her") c.her else c.him, size = 24)
                Text(
                    "  ${vm.nameOf(memo.author)} · ${Dates.relTime(memo.createdAt)} · ${if (memo.privateOnly) "仅自己可见" else "共同可见"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(18.dp),
            ) {
                Text(memo.text, fontSize = 15.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                if (memo.kind == "event" && memo.eventDate.isNotBlank()) {
                    Text("📅 ${Dates.cn(memo.eventDate)}", fontSize = 12.sp, color = c.goldInk, modifier = Modifier.padding(top = 8.dp))
                }
                /* 详细内容：所有标签通用（note 字段分层存储），事件与普通备忘都展示 */
                if (memo.note.isNotBlank()) {
                    Text(memo.note, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
            ) {
                // 标签：点击直接弹选择器改（三栏之一；事件也保留可改，便于转回普通备忘后调整）
                MetaRow("标签", memo.tag) { showTagPicker = true }
                // 提醒：点击直接开/关（三栏之一）
                MetaRow("提醒", if (memo.reminder) "已开启" else "未开启") {
                    vm.saveMemo(memo.copy(reminder = !memo.reminder))
                }
                if (memo.isTodo) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleMemoDone(memo) }.padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tick(memo.done) { vm.toggleMemoDone(memo) }
                        Text(
                            if (memo.done) "  已完成（点此撤销）" else "  待办（点此完成）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (memo.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // 状态：点击弹选择器，普通备忘 ↔ 事件记录 可互转
                    MetaRow("状态", if (memo.kind == "event") "事件记录" else "普通备忘") { showKindPicker = true }
                }
            }

            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionBtn("置顶", Modifier.weight(1f)) { vm.togglePin(memo) }
                ActionBtn("编辑", Modifier.weight(1f)) { nav.navigate("editor?memoId=${memo.id}") }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (memo.done) MaterialTheme.colorScheme.surfaceVariant else c.accent)
                        .clickable { vm.toggleMemoDone(memo) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (memo.done) "撤销完成" else "完成 ✓",
                        color = if (memo.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showTagPicker && m != null) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("换个标签") },
            text = {
                com.xiaoman.memo.ui.components.ChipRow {
                    tags.forEach { t ->
                        XmChip(t, on = t == m!!.tag) {
                            vm.saveMemo(m!!.copy(tag = t))
                            showTagPicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagPicker = false }) { Text("取消") }
            },
        )
    }

    if (showKindPicker && m != null) {
        AlertDialog(
            onDismissRequest = { showKindPicker = false },
            title = { Text("改成哪种状态") },
            text = {
                com.xiaoman.memo.ui.components.ChipRow {
                    listOf("memo" to "普通备忘", "event" to "事件记录").forEach { (k, label) ->
                        XmChip(label, on = m!!.kind == k) {
                            // 转事件且没日期时自动补今天，转回普通备忘时清掉事件日期
                            val nm = if (k == "event") {
                                if (m!!.eventDate.isBlank()) m!!.copy(kind = "event", eventDate = Dates.fmt(Dates.today()))
                                else m!!.copy(kind = "event")
                            } else m!!.copy(kind = "memo", eventDate = "")
                            vm.saveMemo(nm)
                            showKindPicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKindPicker = false }) { Text("取消") }
            },
        )
    }

    if (confirmDel && m != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除这条备忘？") },
            text = { Text("对方那边也会同步删除。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMemo(m!!.id); nav.popBackStack() }) {
                    Text("删除", color = c.accentDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDel = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MetaRow(k: String, v: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            if (onClick != null) "$v ›" else v,
            fontSize = 13.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
