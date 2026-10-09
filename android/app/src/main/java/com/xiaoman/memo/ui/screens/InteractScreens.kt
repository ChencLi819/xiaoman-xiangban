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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.domain.ActItem
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.FlipCard
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.TagChip
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm

private val ACT_CATS = listOf("真心话", "大冒险", "情侣任务", "情话", "默契问答")

@Composable
private fun ActRow(
    a: ActItem,
    fav: Boolean,
    played: Boolean,
    onFav: () -> Unit,
    onPlay: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDel: (() -> Unit)? = null,
) {
    val c = Xm
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagChip(a.c, MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
        Text(a.t, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onEdit != null) ManageIcon("✎") { onEdit() }
            if (onDel != null) ManageIcon("✕") { onDel() }
            IconBtn("♥", fav) { onFav() }
            IconBtn("✓", played) { onPlay() }
        }
    }
}

@Composable
private fun ManageIcon(sym: String, onClick: () -> Unit) {
    val c = Xm
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.tagBg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(sym, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun IconBtn(sym: String, on: Boolean, onClick: () -> Unit) {
    val c = Xm
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on && sym == "♥") Color(0xFFFBE0E7) else if (on) c.okSoft else c.tagBg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(sym, fontSize = 13.sp, color = if (on && sym == "♥") Color(0xFFB85F73) else if (on) c.ok else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* 新增/编辑互动条目对话框：editing=null 表示新增 */
@Composable
private fun ActEditDialog(
    editing: ActItem?,
    onSave: (text: String, cat: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(editing?.t ?: "") }
    var cat by remember { mutableStateOf(editing?.c ?: ACT_CATS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "自定义一条" else "修改这一条") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("写内容…") })
                Text("分类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.xiaoman.memo.ui.components.ChipRow {
                    ACT_CATS.forEach { k -> XmChip(k, on = cat == k) { cat = k } }
                }
                if (editing != null && !editing.custom) {
                    Text(
                        "修改内置条目不会影响其他人，随时可以删掉这条修改恢复原文。",
                        fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) { onSave(text.trim(), cat); onDismiss() } }) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ActDeleteDialog(origin: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (origin.isBlank()) "删除这一条？" else "删除这条内容？") },
        text = { Text("删掉后不再出现；以后想恢复，可以在「互动库」重新添加相同内容。") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("删除", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/* ── 互动 ── */
@Composable
fun InteractScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var cat by remember { mutableStateOf("全部") }
    var cur by remember { mutableStateOf<ActItem?>(null) }
    var flipTick by remember { mutableIntStateOf(0) }

    val marks = ui.actMarks.associateBy { it.text }
    val cats = listOf("全部") + ACT_CATS
    val actPool = ui.actPool

    fun draw() {
        val v = actPool.filter { cat == "全部" || it.c == cat }
        if (v.isEmpty()) return
        val un = v.filter { marks[it.t]?.played != true }
        val pool = if (un.isNotEmpty()) un else v
        var p = pool.random()
        if (pool.size > 1 && cur != null && p.t == cur!!.t) p = pool[(pool.indexOf(p) + 1) % pool.size]
        cur = p
        flipTick++
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("互动", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${actPool.size} 条 · 已玩 ${marks.count { it.value.played }} · 收藏 ${marks.count { it.value.fav }}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }

        com.xiaoman.memo.ui.components.ChipRow {
            cats.forEach { k -> XmChip(k, on = cat == k) { cat = k; draw() } }
        }

        FlipCard(trigger = flipTick) {
            Column(
                Modifier.fillMaxWidth().height(190.dp).shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.actGrad[0], c.actGrad[1])))
                    .clickable { draw() }.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("互 动", fontSize = 11.sp, letterSpacing = 1.4.sp, color = c.actCap)
                Text(cur?.t ?: "点一下，抽一张", fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = c.actTxt, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                Text(
                    if (cur == null) "全部 · ${actPool.size} 条里随机" else "${cur!!.c} · ${if (marks[cur!!.t]?.played == true) "已玩过" else "还没玩"}",
                    fontSize = 11.sp, color = c.actCap, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val fav = cur != null && marks[cur!!.t]?.fav == true
            val played = cur != null && marks[cur!!.t]?.played == true
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { cur?.let { vm.toggleActFav(it.t) } }.padding(0.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (fav) "已收藏 ♥" else "收藏", fontSize = 13.sp, color = if (fav) c.actTxt else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (fav) FontWeight.SemiBold else FontWeight.Normal) }
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(13.dp)).background(if (played) c.accentSoft else MaterialTheme.colorScheme.surface)
                    .clickable { cur?.let { vm.toggleActPlayed(it.t) } },
                contentAlignment = Alignment.Center,
            ) { Text(if (played) "已玩过 ✓" else "已玩过", fontSize = 13.sp, color = if (played) c.actTxt else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (played) FontWeight.SemiBold else FontWeight.Normal) }
        }

        SectionHead("我的收藏", "${marks.count { it.value.fav }} 条")
        val favs = actPool.filter { marks[it.t]?.fav == true }
        if (favs.isEmpty()) EmptyView("还没有收藏")
        favs.take(10).forEach { a ->
            ActRow(a, marks[a.t]?.fav == true, marks[a.t]?.played == true, { vm.toggleActFav(a.t) }, { vm.toggleActPlayed(a.t) })
        }

        SectionHead("翻翻看", "$cat · 共 ${actPool.count { cat == "全部" || it.c == cat }} 条")
        actPool.filter { cat == "全部" || it.c == cat }.take(6).forEach { a ->
            ActRow(a, marks[a.t]?.fav == true, marks[a.t]?.played == true, { vm.toggleActFav(a.t) }, { vm.toggleActPlayed(a.t) })
        }

        Text(
            "想管理内容？去「互动库」新增、修改或删除",
            fontSize = 12.sp, color = c.accent, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { nav.navigate(R.LIBRARY) }.padding(vertical = 8.dp),
        )
    }
}

/* ── 互动库：完整管理入口（增删改） ── */
@Composable
fun LibraryScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var filter by remember { mutableStateOf("all") }
    var cat by remember { mutableStateOf("全部") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActItem?>(null) }
    var deleting by remember { mutableStateOf<ActItem?>(null) }

    val marks = ui.actMarks.associateBy { it.text }
    val cats = ACT_CATS
    val actPool = ui.actPool

    val view = actPool
        .filter { cat == "全部" || it.c == cat }
        .filter {
            when (filter) {
                "fav" -> marks[it.t]?.fav == true
                "done" -> marks[it.t]?.played == true
                "new" -> marks[it.t]?.played != true
                else -> true
            }
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
            Column(Modifier.weight(1f)) {
                Text("互动库", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("${view.size} 条 · 已玩 ${marks.count { it.value.played }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 自定义") { showAdd = true }
        }

        com.xiaoman.memo.ui.components.ChipRow {
            listOf("all" to "全部", "fav" to "收藏", "done" to "已玩过", "new" to "还没玩").forEach { (k, label) ->
                XmChip(label, on = filter == k) { filter = k }
            }
        }
        com.xiaoman.memo.ui.components.ChipRow {
            listOf("全部", *cats.toTypedArray()).forEach { k -> XmChip(k, on = cat == k) { cat = k } }
        }

        if (view.isEmpty()) EmptyView("这里还没有内容")
        view.take(60).forEach { a ->
            ActRow(
                a, marks[a.t]?.fav == true, marks[a.t]?.played == true,
                { vm.toggleActFav(a.t) }, { vm.toggleActPlayed(a.t) },
                onEdit = { editing = a },
                onDel = { deleting = a },
            )
        }
        if (view.size > 60) {
            Text("仅显示前 60 条，用分类缩小范围", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

    if (showAdd) {
        ActEditDialog(
            editing = null,
            onSave = { text, catSel -> vm.addPoolItem("act", text, catSel) },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { a ->
        ActEditDialog(
            editing = a,
            onSave = { text, catSel -> vm.editPoolItem("act", a.t, text, catSel) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { a ->
        ActDeleteDialog(
            origin = if (a.custom) "" else a.t,
            onConfirm = { vm.deletePoolItem("act", a.t) },
            onDismiss = { deleting = null },
        )
    }
}
