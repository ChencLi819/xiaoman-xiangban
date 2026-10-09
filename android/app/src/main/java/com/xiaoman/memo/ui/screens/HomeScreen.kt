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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.ui.components.PairAvatars
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.Tick
import com.xiaoman.memo.ui.components.WhoAdded
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates

@Composable
fun HomeScreen(nav: NavHostController, vm: AppViewModel, onTab: (Int) -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var quick by remember { mutableStateOf("") }

    /* 待办备忘（写一条里勾了「设为待办」的），完成与未完成都保留痕迹 */
    val todos = ui.memos.filter { it.isTodo }
    val pending = todos.count { !it.done }
    val phase = ui.phaseToday
    val st = ui.cycleStats

    /* 「最近」= 备忘 + 清单条目 + 约定 三个板块的最新动态，按时间排，只取 3 条 */
    data class RecentEntry(val time: Long, val kind: String, val text: String, val target: Int, val memoId: Long = 0L)
    val recent = buildList {
        ui.memos.forEach { m ->
            add(RecentEntry(m.createdAt, if (m.kind == "event") "事件" else "备忘", m.text, 2, m.id))
        }
        ui.checklistItems.forEach { item ->
            val name = ui.checklists.find { it.id == item.listId }?.name.orEmpty()
            add(RecentEntry(maxOf(item.updatedAt, item.checkedAt), "清单", listOf(name, item.text).filter { it.isNotBlank() }.joinToString(" · "), 1))
        }
        ui.plans.forEach { p ->
            add(RecentEntry(p.updatedAt, "约定", p.title, 3))
        }
    }.sortedByDescending { it.time }.take(3)

    var showAdd by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 14.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("此刻", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "${Dates.headerToday()} · $pending 件待办",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(Modifier.weight(1f))
                PairAvatars(ui.profile.herName, ui.profile.himName)
            }
        }

        /* 速记条：三秒记下一件事 */
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = quick,
                    onValueChange = { quick = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box {
                            if (quick.isEmpty()) {
                                Text("随手记一件…（回车即存）", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            inner()
                        }
                    },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        vm.addQuickMemo(quick) { quick = "" }
                    }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                )
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .width(56.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(c.accent, c.accentDeep),
                            ),
                        )
                        .clickable { vm.addQuickMemo(quick) { quick = "" } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("记下", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }

        /* B2：到期当天轻提醒（应用内，不强制通知栏） */
        val todayStr = Dates.fmt(Dates.today())
        val todayPlans = ui.plans.filter { it.date == todayStr }
        if (todayPlans.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.goldSoft)
                        .clickable { onTab(3) }
                        .padding(12.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⏰", fontSize = 15.sp)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(
                            "今天有 ${todayPlans.size} 件事",
                            fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = c.goldInk,
                        )
                        Text(
                            todayPlans.joinToString("、") { it.title },
                            fontSize = 11.sp, color = c.goldInk, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Text("›", color = c.goldInk, fontSize = 14.sp)
                }
            }
        }

        /* 周期卡（有记录才显示） */
        if (phase != null && st != null) {
            item {
                val left = Dates.diff(Dates.fmt(Dates.today()), st.nextStart).toInt()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable { nav.navigate(R.CYCLE) }
                        .padding(12.dp, 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(38.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(listOf(c.calP, c.okSoft, c.calO, c.goldSoft)[phase.k - 1]),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            phase.name.take(2),
                            fontSize = 11.sp,
                            color = listOf(c.calPInk, c.ok, c.calOInk, c.goldInk)[phase.k - 1],
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("今天 · ${phase.name} 第 ${phase.n} 天", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (left > 0) "下次经期还有 $left 天" else "今天可能是经期开始",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                }
            }
        }

        /* 要一起做的：待办备忘（在点滴里勾选「设为待办」的那部分）。
           增：＋ 添加一项 → 新建一条待办备忘；
           删：✕ 软删该备忘；改：✎ 进编辑页（可改文字/标签/待办/提醒）；
           查（勾选）：Tick 完成/撤销，完成后保留划线痕迹。 */
        item {
            SectionHead("要一起做的", "全部 ${todos.size} 件")
        }

        item {
            XmCard {
                if (todos.isEmpty()) {
                    Text(
                        "还没有待办",
                        fontSize = 12.sp, lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                todos.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tick(m.done) { vm.toggleMemoDone(m) }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (m.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (m.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            )
                            WhoAdded(m.author, vm.nameOf(m.author), m.done)
                        }
                        Text(
                            "✎",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { nav.navigate("editor?memoId=${m.id}") },
                        )
                        Text(
                            "✕",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable { vm.deleteMemo(m.id) },
                        )
                    }
                }
                Text(
                    "＋ 添加一项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp).clickable { showAdd = true },
                )
            }
        }

        item {
            SectionHead("最近", "备忘 · 清单 · 约定")
        }

        if (recent.isEmpty()) {
            item {
                    Text("暂无动态", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        items(recent.size) { i ->
            val e = recent[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (e.memoId != 0L) nav.navigate("detail/${e.memoId}") else onTab(e.target)
                    }
                    .padding(12.dp, 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (fg, bg) = when (e.kind) {
                    "清单" -> c.himInk to c.himSoft
                    "约定" -> c.goldInk to c.goldSoft
                    "事件" -> c.accentDeep to c.accentSoft
                    else -> c.ok to c.okSoft
                }
                Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(e.kind, fontSize = 10.sp, color = fg)
                }
                Text(
                    e.text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                )
                Text("›", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    /* 添加：新建一条待办备忘（默认标签「待办」、归属当前身份、共同可见） */
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("要一起做什么？") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("写一件事…") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        vm.addTodoMemo(text)
                        showAdd = false
                    }
                }) { Text("添加", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
