package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.ChecklistItemEntity
import com.xiaoman.memo.ui.components.Tick
import com.xiaoman.memo.ui.components.WhoDot
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm

@Composable
fun ListsScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var showNew by remember { mutableStateOf(false) }
    var addFor by remember { mutableStateOf<Long?>(null) }
    var addItemText by remember { mutableStateOf("") }
    var deletingList by remember { mutableStateOf<Long?>(null) }
    var renamingList by remember { mutableStateOf<Long?>(null) }
    var renameListText by remember { mutableStateOf("") }
    var renamingItem by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var renameItemText by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 14.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("说好的", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text("一起勾掉的事，都留着", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.weight(1f))
                com.xiaoman.memo.ui.components.XmActionChip("＋ 新建") { showNew = true }
            }
        }

        val lists = ui.checklists
        items(lists.size) { li ->
            val list = lists[li]
            val rows = ui.checklistItems.filter { it.listId == list.id }
            val done = rows.count { it.checked }
            val all = rows.size
            val full = all > 0 && done == all

            Column(
                Modifier
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                    .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        list.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { renamingList = list.id; renameListText = list.name },
                    )
                    Text(
                        "$done/$all" + if (full) " · 已完成" else "",
                        fontSize = 12.sp,
                        color = if (full) c.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " ✕",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clickable { deletingList = list.id },
                    )
                }
                /* 进度条（宽度变化带动画） */
                val progress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (all > 0) done / all.toFloat() else 0f,
                    animationSpec = androidx.compose.animation.core.tween(300),
                    label = "listProgress",
                )
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(RoundedCornerShape(6.dp)).background(c.tagBg),
                ) {
                    if (all > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (full) c.ok else c.accent),
                        )
                    }
                }
                rows.forEachIndexed { ii, it0 ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tick(it0.checked) { vm.toggleChecklistItem(it0) }
                        Text(
                            " ${it0.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it0.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (it0.checked) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        WhoDot(it0.author)
                        Text(
                            " ✎",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp).clickable { renamingItem = it0; renameItemText = it0.text },
                        )
                        Text(
                            " ✕",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp).clickable { vm.deleteChecklistItem(it0.id) },
                        )
                    }
                }
                if (addFor == list.id) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = addItemText,
                            onValueChange = { addItemText = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            decorationBox = { inner ->
                                Box {
                                    if (addItemText.isEmpty()) Text("要一起做什么？", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                                    inner()
                                }
                            },
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                vm.addChecklistItem(list.id, addItemText); addItemText = ""; addFor = null
                            }),
                            singleLine = true,
                        )
                    }
                } else {
                    Text(
                        "＋ 添加一项",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp).clickable { addFor = list.id },
                    )
                }
            }
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新建清单") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("例如：周末采购") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.addChecklist(name); showNew = false }) { Text("创建", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("取消") } },
        )
    }

    deletingList?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingList = null },
            title = { Text("删除这个清单？") },
            text = { Text("清单会整张移除，已勾掉的事不会保留。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteChecklist(id); deletingList = null }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { deletingList = null }) { Text("取消") } },
        )
    }

    renamingList?.let { id ->
        AlertDialog(
            onDismissRequest = { renamingList = null },
            title = { Text("重命名清单") },
            text = {
                OutlinedTextField(value = renameListText, onValueChange = { renameListText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.renameChecklist(id, renameListText); renamingList = null }) { Text("保存", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { renamingList = null }) { Text("取消") } },
        )
    }

    renamingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text("改一改") },
            text = {
                OutlinedTextField(value = renameItemText, onValueChange = { renameItemText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.renameChecklistItem(item.id, renameItemText); renamingItem = null }) { Text("保存", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { renamingItem = null }) { Text("取消") } },
        )
    }
}
