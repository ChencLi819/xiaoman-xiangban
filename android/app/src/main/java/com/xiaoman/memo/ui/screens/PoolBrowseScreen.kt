package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.domain.Quarrel
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.TagChip
import com.xiaoman.memo.ui.components.XmActionChip
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.Xm

/* 翻篇点子库：雨过「翻篇方式」的独立浏览页（v1.5.1）
   - 与互动那边「翻翻看」同一模式：抽一张页只留入口卡，全量浏览收进这里；
   - 搜索 / 火气等级 / 兑现时机 / 分类四重筛选，LazyColumn 承载 2600 条；
   - 增删改也归这一页（原先是抽一张页内嵌列表上的 ✎ ✕）。 */
@Composable
fun PoolBrowseScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var q by remember { mutableStateOf("") }
    var lv by remember { mutableIntStateOf(0) }      // 0 = 全部等级
    var mo by remember { mutableStateOf("all") }     // all | now | meet
    var cat by remember { mutableStateOf("全部") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Quarrel.PoolItem?>(null) }
    var deleting by remember { mutableStateOf<Quarrel.PoolItem?>(null) }

    val cats = listOf("全部") + Quarrel.CATS
    val pool = ui.qPool

    val view = pool.filter { p ->
        (lv == 0 || p.lv == lv) &&
            (cat == "全部" || p.c == cat) &&
            (q.isBlank() || p.t.contains(q, true)) &&
            (mo == "all" || (mo == "now" && !p.m) || (mo == "meet" && p.m))
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("翻篇点子库", style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "共 ${pool.size} 条 · ${pool.count { it.m }} 条等下次见面",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp),
                )
            }
            XmActionChip("＋ 自定义") { showAdd = true }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp, 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 9.dp))
            BasicTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) Text("搜一种翻篇的方式…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
                singleLine = true,
            )
        }

        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${view.size} 条",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        com.xiaoman.memo.ui.components.ChipRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            XmChip("全部等级", on = lv == 0) { lv = 0 }
            (1..5).forEach { l -> XmChip(Quarrel.LEVEL_NAMES[l], on = lv == l) { lv = l } }
        }
        com.xiaoman.memo.ui.components.ChipRow(Modifier.padding(horizontal = 20.dp)) {
            listOf("all" to "全部时机", "now" to "现在就能做", "meet" to "下次见面").forEach { (k, label) ->
                XmChip(label, on = mo == k) { mo = k }
            }
        }
        com.xiaoman.memo.ui.components.ChipRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            cats.forEach { k -> XmChip(k, on = cat == k) { cat = k } }
        }

        if (view.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) { EmptyView("这个组合下没有条目") }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(view.size) { i ->
                    val p = view[i]
                    PoolRow(
                        p = p,
                        onEdit = { editing = p },
                        onDel = { deleting = p },
                    )
                }
            }
        }
    }

    if (showAdd) {
        PoolEditDialog(
            editing = null,
            cats = Quarrel.CATS,
            onSave = { text, catSel, meet, lvSel -> vm.addPoolItem("pool", text, catSel, meet, lvSel) },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { p ->
        PoolEditDialog(
            editing = p,
            cats = Quarrel.CATS,
            onSave = { text, catSel, meet, lvSel -> vm.editPoolItem("pool", p.t, text, catSel, meet, lvSel) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条翻篇方式？") },
            text = { Text("删掉后不再被抽到；想恢复，重新添加相同内容即可。") },
            confirmButton = {
                TextButton(onClick = { vm.deletePoolItem("pool", p.t); deleting = null }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/* 单条：等级色标 + 分类 + 内容 + 时机 + 编辑/删除 */
@Composable
private fun PoolRow(p: Quarrel.PoolItem, onEdit: () -> Unit, onDel: () -> Unit) {
    val c = Xm
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.heat[p.lv - 1]).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(Quarrel.LEVEL_NAMES[p.lv], fontSize = 9.sp, color = Color.White)
        }
        TagChip(p.c, MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg, Modifier.padding(start = 8.dp))
        Text(
            p.t,
            fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(
            if (p.m) "见面时" else "现在",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(" ✎", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).clickable { onEdit() })
        Text(" ✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 6.dp).clickable { onDel() })
    }
}

/* 新增/编辑翻篇方式：文本 + 分类 + 兑现时机 + 火气等级；editing=null 表示新增。
   从抽一张页的内嵌列表迁来这里，样式不变。 */
@Composable
private fun PoolEditDialog(
    editing: Quarrel.PoolItem?,
    cats: List<String>,
    onSave: (text: String, cat: String, meet: Boolean, lv: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(editing?.t ?: "") }
    var cat by remember { mutableStateOf(editing?.c ?: cats.first()) }
    var meet by remember { mutableStateOf(editing?.m ?: false) }
    var lv by remember { mutableIntStateOf(editing?.lv ?: 2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "自定义翻篇方式" else "修改这一条") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("写一种翻篇的方式…") })
                Text("分类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.xiaoman.memo.ui.components.ChipRow {
                    cats.forEach { k -> XmChip(k, on = cat == k) { cat = k } }
                }
                Text("兑现时机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XmChip("现在就能做", on = !meet) { meet = false }
                    XmChip("下次见面", on = meet) { meet = true }
                }
                Text("火气等级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { level -> XmChip(Quarrel.LEVEL_NAMES[level], on = lv == level) { lv = level } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) { onSave(text.trim(), cat, meet, lv); onDismiss() } }) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
