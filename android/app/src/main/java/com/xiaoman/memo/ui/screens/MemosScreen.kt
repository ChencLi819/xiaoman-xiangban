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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.DraftKey
import com.xiaoman.memo.domain.tagList
import com.xiaoman.memo.ui.components.AutoDraft
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.MemoCard
import com.xiaoman.memo.ui.components.NoteToggleField
import com.xiaoman.memo.ui.components.StrListSaver
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.components.rememberDraft
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MemosScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }

    /* ── 新建对话框（备忘 / 事件通用，详细内容控件可自由开关）──
       表单状态一律 rememberSaveable：改字号 / 分屏重建后不丢，也不必靠草稿兜。 */
    var showNew by rememberSaveable { mutableStateOf(false) }
    var newKind by rememberSaveable { mutableStateOf("memo") }   // memo | event
    var nTitle by rememberSaveable { mutableStateOf("") }
    /* v6：一条备忘可以挂多个标签；第一个是主标签（写进 tag 列，旧版本也能读） */
    var nTags by rememberSaveable(stateSaver = StrListSaver) { mutableStateOf(listOf("生活")) }
    var showNewTag by rememberSaveable { mutableStateOf(false) }
    var newTagName by rememberSaveable { mutableStateOf("") }
    var newTagHint by rememberSaveable { mutableStateOf("") }
    var nDate by rememberSaveable { mutableStateOf("") }
    var nNoteOpen by rememberSaveable { mutableStateOf(false) }
    var nNote by rememberSaveable { mutableStateOf("") }
    /* 「仅我看」：只留在本机、不进同步快照（Backup.exportSyncSnapshot 会剔除） */
    var nPrivate by rememberSaveable { mutableStateOf(false) }
    /* 名称为空 + 详细内容也空时的就地提示，代替以前「静默关闭」的假保存 */
    var nHint by rememberSaveable { mutableStateOf("") }
    var showEvDate by rememberSaveable { mutableStateOf(false) }

    /* ── 详情查看层：点击任意条目弹出，名称/详细内容都可改 ── */
    var viewing by remember { mutableStateOf<com.xiaoman.memo.data.MemoEntity?>(null) }
    var viewText by remember { mutableStateOf("") }
    var viewNote by remember { mutableStateOf("") }
    var viewNoteOpen by remember { mutableStateOf(false) }
    var viewPrivate by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /* ── 写一半的「＋ 新建」自动留底 ──
       对话框是页面里的浮层，不是独立页面：点外面 / 点取消就关，没有「退出即存」的时机。
       有内容就留一份草稿，下次点「＋ 新建」自动接着写；正式保存后 finish() 清掉。 */
    val newDraft = rememberDraft(vm, DraftKey.MEMO, 0L)
    /* 每次打开只续一次：重建后表单里已经是用户最新的字，别拿 1.5 秒前的草稿盖回去 */
    var newResumed by rememberSaveable { mutableStateOf(false) }

    fun newDirty() = nTitle.isNotBlank() || nNote.isNotBlank() || nDate.isNotBlank() ||
        newKind != "memo" || nTags != listOf("生活") || nPrivate

    fun newSnapshot(): JSONObject? = if (!newDirty()) null else JSONObject().apply {
        put("title", nTitle); put("kind", newKind)
        put("tags", JSONArray().apply { nTags.forEach { put(it) } })
        put("date", nDate); put("note", nNote)
        put("noteOpen", nNoteOpen); put("private", nPrivate)
    }

    /* 打开时续上没写完的一条（关掉时把 newResumed 放回 false，下次打开还能接着续） */
    LaunchedEffect(showNew) {
        if (!showNew) { newResumed = false; return@LaunchedEffect }
        if (newResumed) return@LaunchedEffect
        newResumed = true
        newDraft.read()?.let { o ->
            nTitle = o.optString("title")
            newKind = if (o.optString("kind") == "event") "event" else "memo"
            o.optJSONArray("tags")?.let { a ->
                val t = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                if (t.isNotEmpty()) nTags = t
            }
            nDate = o.optString("date")
            nNote = o.optString("note")
            nNoteOpen = o.optBoolean("noteOpen", nNote.isNotBlank())
            nPrivate = o.optBoolean("private")
        }
    }

    /* 输入中防抖留底（进程被杀也不至于全丢）+ 离开页面兜底 */
    AutoDraft(newDraft, newDirty()) { newSnapshot() }

    /** 关掉「＋ 新建」但不丢内容：先显式落一次草稿。
     *  不能只靠 AutoDraft 的离开兜底 —— 这里随后要把表单清空，兜底会读到空值反把草稿抹掉。 */
    fun dismissNew() {
        newDraft.flush(newDirty(), newSnapshot())
        showNew = false
        nHint = ""; showNewTag = false; newTagName = ""; newTagHint = ""
    }

    /** 正式保存成功后的收尾：草稿清掉，表单复位。
     *  用 discard() 而不是 finish()：这个浮层会被反复打开，handle 得留着继续用。 */
    fun closeNew() {
        newDraft.discard()
        showNew = false
        nTitle = ""; nTags = listOf("生活"); nDate = ""; nNote = ""; nNoteOpen = false
        nPrivate = false; nHint = ""; showNewTag = false; newTagName = ""; newTagHint = ""
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
        val tags = m.tagList()
        (m.author == me || !m.privateOnly) &&
            (q.isBlank() || m.text.contains(q, true) || tags.any { it.contains(q, true) } || vm.nameOf(m.author).contains(q, true))
            && when {
                filter == "全部" -> true
                filter == "事件" -> m.kind == "event"
                filter == "仅我看" -> m.privateOnly && m.author == me
                /* 多标签：命中任意一个标签就算（一条备注可以同时出现在「生活」和「想买」下） */
                else -> m.kind == "memo" && tags.contains(filter)
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
            /* 点外面 / 返回键：留底走人，下次打开接着写 */
            onDismissRequest = { dismissNew() },
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
                        Column {
                            Text("标签（可多选）", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            com.xiaoman.memo.ui.components.ChipRow {
                                com.xiaoman.memo.domain.allTags(ui.customTags.map { it.name }).forEach { t ->
                                    XmChip(t, on = nTags.contains(t)) {
                                        nTags = if (nTags.contains(t)) {
                                            // 至少留一个标签，取消最后一个时不生效
                                            if (nTags.size > 1) nTags - t else nTags
                                        } else {
                                            (nTags + t).take(com.xiaoman.memo.domain.MAX_TAGS)
                                        }
                                    }
                                }
                                com.xiaoman.memo.ui.components.XmActionChip("＋ 自定义") { showNewTag = true }
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
                    else vm.addMemoWithTags(title, nTags, nNote, nPrivate)
                    closeNew()
                    afterSaved()
                }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { dismissNew() }) { Text("取消") } },
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

    /* ── 新建时随手加一个自定义标签 ── */
    if (showNewTag) {
        AlertDialog(
            onDismissRequest = { showNewTag = false; newTagName = ""; newTagHint = "" },
            title = { Text("新建标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it; newTagHint = "" },
                        label = { Text("标签名") },
                        singleLine = true,
                        isError = newTagHint.isNotBlank(),
                        supportingText = if (newTagHint.isNotBlank()) {
                            { Text(newTagHint, fontSize = 11.sp) }
                        } else null,
                    )
                    Text("8 字以内，不能含逗号", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newTagName.trim()
                    if (!com.xiaoman.memo.domain.isValidTagName(n)) {
                        newTagHint = "1–8 个字，不能含逗号"
                        return@TextButton
                    }
                    scope.launch {
                        vm.addCustomTag(n) { ok ->
                            if (ok) {
                                nTags = (nTags + n).distinct().take(com.xiaoman.memo.domain.MAX_TAGS)
                                showNewTag = false; newTagName = ""; newTagHint = ""
                            } else {
                                newTagHint = "这个名字已经有了，或格式不对"
                            }
                        }
                    }
                }) { Text("创建", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showNewTag = false; newTagName = ""; newTagHint = "" }) { Text("取消") } },
        )
    }

    /* ── 详情查看层：点击任意条目弹出；名称可改、详细内容可自由开关编辑 ── */
    /* 改一半关掉也不丢：按条目 id 存草稿（target 对不上就不恢复，不会把 A 的改动盖到 B 上） */
    val viewDraft = rememberDraft(vm, DraftKey.MEMO_EDIT, viewing?.id ?: 0L)
    LaunchedEffect(viewing) {
        viewing?.let { m ->
            viewText = m.text; viewNote = m.note
            viewNoteOpen = m.note.isNotBlank(); viewPrivate = m.privateOnly
            viewDraft.read()?.let { o ->
                viewText = o.optString("text", viewText)
                viewNote = o.optString("note", viewNote)
                viewNoteOpen = o.optBoolean("noteOpen", viewNote.isNotBlank())
                viewPrivate = o.optBoolean("private", viewPrivate)
            }
        }
    }
    viewing?.let { m ->
        /* 改没改过：与「保存」按钮的判断逐字一致（都比 trim 后的值），
           否则只加个尾随空格就会留下一条永远清不掉的草稿。打开不动就不留冗余草稿。 */
        val viewDirty = viewText.trim() != m.text || viewNote.trim() != m.note ||
            viewPrivate != m.privateOnly
        AutoDraft(viewDraft, viewDirty) {
            if (!viewDirty) null else JSONObject().apply {
                put("text", viewText); put("note", viewNote)
                put("noteOpen", viewNoteOpen); put("private", viewPrivate)
            }
        }
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
                        Text("标签：" + m.tagList().joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
                        /* 先清草稿再关：否则浮层离开组合时的兜底写入会把刚保存的改动又存回草稿。
                           这里用 finish()：viewing 一清空 handle 就随 targetId 重建，不存在被锁死的问题。 */
                        viewDraft.finish()
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
