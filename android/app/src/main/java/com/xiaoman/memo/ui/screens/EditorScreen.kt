package com.xiaoman.memo.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.xiaoman.memo.data.MemoEntity
import com.xiaoman.memo.ui.components.Seg
import com.xiaoman.memo.ui.components.SwitchRow
import com.xiaoman.memo.ui.components.Tick
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.flow.map

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(nav: NavHostController, vm: AppViewModel, memoId: Long) {
    val c = Xm
    val me = vm.ui.collectAsState().value.profile.identity

    var text by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("生活") }
    var isTodo by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(false) }
    var privateOnly by remember { mutableStateOf(false) }
    var owner by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(memoId == 0L) }
    var original by remember { mutableStateOf<MemoEntity?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    /* 详细内容：所有标签通用，控件可自由开关（收起=一行入口，展开=多行编辑） */
    var note by remember { mutableStateOf("") }
    var noteOpen by remember { mutableStateOf(false) }
    /* 保存被挡下时的就地提示（以前是静默 no-op，看着像「保存没生效」） */
    var saveHint by remember { mutableStateOf("") }

    LaunchedEffect(memoId) {
        if (memoId != 0L) {
            vm.daoMemo(memoId)?.let { m ->
                original = m
                text = m.text; tag = m.tag; isTodo = m.isTodo; reminder = m.reminder
                privateOnly = m.privateOnly
                note = m.note; noteOpen = m.note.isNotBlank()
                owner = when (m.owner) { "mine" -> 1; "partner" -> 2; else -> 0 }
                loaded = true
            }
        }
    }

    // 事件的 kind/eventDate 通过 copy 自动保留；note 在下方「详细内容」控件编辑
    val dirty = original?.let {
        it.text != text || it.tag != tag || it.isTodo != isTodo || it.reminder != reminder ||
            it.privateOnly != privateOnly || it.note != note.trim()
    } ?: (text.isNotBlank())

    BackHandler(enabled = dirty && loaded) { confirmExit = true }

    val uiTags by vm.ui.collectAsState()
    val tags = remember(uiTags.customTags) { com.xiaoman.memo.domain.allTags(uiTags.customTags.map { it.name }) }
    var showNewTag by remember { mutableStateOf(false) }
    /* 标签管理：自定义标签以前只能加不能删（vm.deleteCustomTag 全项目零调用）。 */
    var showTagManage by remember { mutableStateOf(false) }
    var tagToDel by remember { mutableStateOf("") }

    fun save(onDone: () -> Unit) {
        // 名称留空时用「详细内容」第一行兜底：内容已经写了就不该保存不上
        val title = text.trim().ifBlank {
            note.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
        if (title.isBlank()) { saveHint = "写点什么再保存"; return }
        val ownerStr = if (privateOnly) "mine" else listOf("shared", "mine", "partner")[owner]
        val author = if (owner == 2 && original == null) (if (me == "her") "him" else "her") else (original?.author ?: me)
        val entity = (original ?: MemoEntity(text = "", tag = tag, author = me, createdAt = 0, updatedAt = 0)).copy(
            text = title, tag = tag, isTodo = isTodo, reminder = reminder, privateOnly = privateOnly,
            owner = ownerStr, author = author, note = note.trim(),
        )
        vm.saveMemo(entity)
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (dirty) confirmExit = true else nav.popBackStack()
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                if (memoId == 0L) "写一条" else "编辑",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "保存",
                color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .clickable { save { nav.popBackStack() } }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        XmCard {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it; saveHint = "" },
                modifier = Modifier.fillMaxWidth().height(132.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("写下要记住的事…", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
            )
        }

        if (saveHint.isNotBlank()) {
            Text(saveHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        XmCard {
            com.xiaoman.memo.ui.components.NoteToggleField(open = noteOpen, value = note, onOpen = { noteOpen = true }, onClose = { noteOpen = false }, onChange = { note = it; saveHint = "" })
        }

        XmCard {
            Row(Modifier.padding(vertical = 13.dp)) { Text("标签", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
            com.xiaoman.memo.ui.components.ChipRow {
                tags.forEach { t -> XmChip(t, on = tag == t) { tag = t } }
                // 动作形态：与上面的可选中标签明确区分
                com.xiaoman.memo.ui.components.XmActionChip("＋ 新标签") { showNewTag = true }
                if (uiTags.customTags.isNotEmpty()) {
                    com.xiaoman.memo.ui.components.XmActionChip("管理") { showTagManage = true }
                }
            }
            SwitchRow("设为待办", "会出现在首页「要一起做的」", isTodo) { isTodo = it }
            SwitchRow("提醒我", null, reminder) { reminder = it }
            SwitchRow("仅我看", "只留在本机，不会同步给对方", privateOnly) { privateOnly = it }
        }

        XmCard {
            Row(Modifier.padding(bottom = 10.dp)) { Text("归属", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
            Seg(listOf("共同", "我的", "TA 的"), owner) { owner = it }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.accent)
                .clickable { save { nav.popBackStack() } },
            contentAlignment = Alignment.Center,
        ) {
            Text("保存到小满", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("还没保存") },
            text = { Text("要保存这条备忘，还是放弃编辑？") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; save { nav.popBackStack() } }) { Text("保存", color = c.accentDeep) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text("放弃") }
            },
        )
    }

    if (showNewTag) {
        var tagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTag = false },
            title = { Text("新标签") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    placeholder = { Text("例如：健身、装修（8 字以内）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addCustomTag(tagName) { ok ->
                        if (ok) tag = tagName.trim()
                    }
                    showNewTag = false
                }) { Text("添加", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showNewTag = false }) { Text("取消") } },
        )
    }

    if (showTagManage) {
        AlertDialog(
            onDismissRequest = { showTagManage = false },
            title = { Text("管理标签") },
            text = {
                Column {
                    uiTags.customTags.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
                            Text(
                                "删除", fontSize = 12.sp, color = c.accentDeep,
                                modifier = Modifier.clickable { tagToDel = t.name },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagManage = false }) { Text("完成", color = c.accentDeep) } },
        )
    }

    if (tagToDel.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { tagToDel = "" },
            title = { Text("删掉标签「$tagToDel」？") },
            /* 说明清楚：删的是「标签」本身，已经用了这个标签的备忘不会跟着消失 */
            text = { Text("已经用了这个标签的备忘不会被删掉，只是以后不能再用它筛选了。") },
            confirmButton = {
                TextButton(onClick = {
                    val n = tagToDel
                    tagToDel = ""
                    if (tag == n) tag = "生活"     // 当前选中的标签被删了，回落到默认标签
                    vm.deleteCustomTag(n)
                }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { tagToDel = "" }) { Text("取消") } },
        )
    }
}
