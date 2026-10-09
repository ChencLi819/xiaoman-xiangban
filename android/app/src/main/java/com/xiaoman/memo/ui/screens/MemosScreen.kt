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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.MemoCard
import com.xiaoman.memo.ui.components.NoteToggleField
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MemosScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }

    /* ── 新建对话框（备忘 / 事件通用，详细内容控件可自由开关）── */
    var showNew by remember { mutableStateOf(false) }
    var newKind by remember { mutableStateOf("memo") }   // memo | event
    var nTitle by remember { mutableStateOf("") }
    var nTag by remember { mutableStateOf("生活") }
    var nDate by remember { mutableStateOf("") }
    var nNoteOpen by remember { mutableStateOf(false) }
    var nNote by remember { mutableStateOf("") }
    /* 「仅我看」：只留在本机、不进同步快照（Backup.exportSyncSnapshot 会剔除） */
    var nPrivate by remember { mutableStateOf(false) }
    /* 名称为空 + 详细内容也空时的就地提示，代替以前「静默关闭」的假保存 */
    var nHint by remember { mutableStateOf("") }
    var showEvDate by remember { mutableStateOf(false) }

    /* ── 详情查看层：点击任意条目弹出，名称/详细内容都可改 ── */
    var viewing by remember { mutableStateOf<com.xiaoman.memo.data.MemoEntity?>(null) }
    var viewText by remember { mutableStateOf("") }
    var viewNote by remember { mutableStateOf("") }
    var viewNoteOpen by remember { mutableStateOf(false) }
    var viewPrivate by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun closeNew() {
        showNew = false
        nTitle = ""; nTag = "生活"; nDate = ""; nNote = ""; nNoteOpen = false
        nPrivate = false; nHint = ""
    }

    /** 保存成功后统一收尾：清掉搜索与筛选（否则新条目可能被当前筛选/搜索挡在列表外，
     *  用户会以为「没保存上」），并滚回顶部让它出现在第一屏。 */
    fun afterSaved() {
        q = ""; filter = "全部"
        scope.launch { listState.animateScrollToItem(0) }
    }

    val filters = remember(ui.customTags) {
        listOf("全部") + com.xiaoman.memo.domain.BUILTIN_TAGS + listOf("事件") +
            ui.customTags.map { it.name } + listOf("仅我看")
    }
    val me = ui.profile.identity
    /* 别人的「仅我看」条目即使因为历史原因（旧版本同步）残留在本机，也不在这里展示 */
    val list = ui.memos.filter { m ->
        (m.author == me || !m.privateOnly) &&
            (q.isBlank() || m.text.contains(q, true) || m.tag.contains(q, true) || vm.nameOf(m.author).contains(q, true))
            && when {
                filter == "全部" -> true
                filter == "事件" -> m.kind == "event"
                filter == "仅我看" -> m.privateOnly && m.author == me
                else -> m.kind == "memo" && m.tag == filter
            }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 14.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("点滴", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text("都在这儿 · 两个人一起写的", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                com.xiaoman.memo.ui.components.XmActionChip("＋ 新建") { showNew = true }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(12.dp, 14.dp),
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
                            if (q.isEmpty()) Text("搜内容、标签、谁写的", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                            inner()
                        }
                    },
                    singleLine = true,
                )
            }
        }
        item {
            com.xiaoman.memo.ui.components.ChipRow {
                filters.forEach { f ->
                    XmChip(f, on = filter == f) { filter = f }
                }
            }
        }
        if (list.isEmpty()) {
            item { EmptyView("暂无内容") }
        }
        items(list.size) { i ->
            val m = list[i]
            MemoCard(m) { viewing = m }
        }
    }

    /* ── ＋ 新建：备忘（标签可选）或事件（带日期），详细内容可自由开关 ── */
    if (showNew) {
        AlertDialog(
            onDismissRequest = { closeNew() },
            title = { Text("记一笔") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.xiaoman.memo.ui.components.Seg(listOf("备忘", "事件"), if (newKind == "memo") 0 else 1) {
                        newKind = if (it == 0) "memo" else "event"
                    }
                    BasicTextField(
                        value = nTitle,
                        onValueChange = { nTitle = it; nHint = "" },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            Box {
                                if (nTitle.isEmpty()) Text(
                                    if (newKind == "event") "去了哪里 / 看了什么 / 一起做了什么…" else "写下这件事的名称…",
                                    fontSize = 14.sp, color = MaterialTheme.colorScheme.outline,
                                )
                                inner()
                            }
                        },
                    )
                    if (newKind == "event") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable { showEvDate = true }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    if (nDate.isBlank()) "📅 日期（可选）" else "📅 $nDate",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
                                )
                            }
                            if (nDate.isNotBlank()) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .clickable { nDate = "" }
                                        .padding(horizontal = 8.dp, vertical = 14.dp),
                                ) { Text("清除", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    } else {
                        com.xiaoman.memo.ui.components.ChipRow {
                            com.xiaoman.memo.domain.allTags(ui.customTags.map { it.name }).forEach { t ->
                                XmChip(t, on = nTag == t) { nTag = t }
                            }
                        }
                    }
                    NoteToggleField(open = nNoteOpen, value = nNote, onOpen = { nNoteOpen = true }, onClose = { nNoteOpen = false }, onChange = { nNote = it; nHint = "" })
                    com.xiaoman.memo.ui.components.SwitchRow(
                        "仅我看", "只留在本机，不会同步给对方", nPrivate,
                    ) { nPrivate = it }
                    if (nHint.isNotBlank()) {
                        Text(nHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    /* 名称留空时自动取「详细内容」的第一行当名称：用户常把内容整段写在详细内容里，
                       旧实现在这种情况下会静默关闭对话框、内容全丢，看起来就是「保存没生效」。 */
                    val title = nTitle.trim().ifBlank {
                        nNote.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    }
                    if (title.isBlank()) {
                        // 什么都不填：不关对话框、不丢输入，就地提示
                        nHint = "给这件事起个名字，或写一句详细内容"
                        return@TextButton
                    }
                    if (newKind == "event") vm.addEventMemo(title, nDate, nNote, nPrivate)
                    else vm.addTaggedMemo(title, nTag, nNote, nPrivate)
                    closeNew()
                    afterSaved()
                }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { closeNew() }) { Text("取消") } },
        )
    }

    if (showEvDate) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showEvDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        nDate = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .let { com.xiaoman.memo.util.Dates.fmt(it) }
                    }
                    showEvDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEvDate = false }) { Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }

    /* ── 详情查看层：点击任意条目弹出；名称可改、详细内容可自由开关编辑 ── */
    LaunchedEffect(viewing) {
        viewing?.let {
            viewText = it.text; viewNote = it.note
            viewNoteOpen = it.note.isNotBlank(); viewPrivate = it.privateOnly
        }
    }
    viewing?.let { m ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(if (m.kind == "event") "事件详情" else "改一改") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    BasicTextField(
                        value = viewText,
                        onValueChange = { viewText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        ),
                        decorationBox = { inner -> Box { inner() } },
                    )
                    if (m.kind == "event" && m.eventDate.isNotBlank()) {
                        Text("📅 " + com.xiaoman.memo.util.Dates.cn(m.eventDate), fontSize = 12.sp, color = c.goldInk)
                    } else if (m.kind != "event") {
                        Text("标签：${m.tag}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    NoteToggleField(open = viewNoteOpen, value = viewNote, onOpen = { viewNoteOpen = true }, onClose = { viewNoteOpen = false }, onChange = { viewNote = it })
                    com.xiaoman.memo.ui.components.SwitchRow(
                        "仅我看", "只留在本机，不会同步给对方", viewPrivate,
                    ) { viewPrivate = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val note2 = viewNote.trim()
                    val text2 = viewText.trim().ifBlank {
                        note2.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    }
                    if (text2.isNotBlank() &&
                        (text2 != m.text || note2 != m.note || viewPrivate != m.privateOnly)
                    ) {
                        vm.saveMemo(
                            m.copy(
                                text = text2, note = note2, privateOnly = viewPrivate,
                                // 打开「仅我看」= 归到自己名下；关掉时回到共同
                                owner = if (viewPrivate) "mine" else if (m.privateOnly) "shared" else m.owner,
                            ),
                        )
                        afterSaved()
                    }
                    viewing = null
                }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val id = m.id
                        viewing = null
                        nav.navigate("detail/$id")
                    }) { Text("详情 ›", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { viewing = null }) { Text("关闭") }
                }
            },
        )
    }
}
