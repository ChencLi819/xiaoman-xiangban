package com.xiaoman.memo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.RuleEntity
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.SwitchRow
import com.xiaoman.memo.ui.components.TagChip
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm

private val RCAT_NAME = mapOf("like" to "喜欢", "hate" to "讨厌", "never" to "不能做", "rule" to "我们的规矩")
private val RWHO_NAME = mapOf("her" to "她", "him" to "他", "both" to "共同")

/* ── 分寸 ── */
@Composable
fun RulesScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var cat by remember { mutableStateOf("all") }
    var who by remember { mutableStateOf("all") }
    /* 删除要二次确认：以前 ✕ 一点就删、没有任何确认（雨过/相册/情书都有） */
    var confirmDel by remember { mutableStateOf<RuleEntity?>(null) }

    val list = ui.rules
        .filter { cat == "all" || it.cat == cat }
        .filter { who == "all" || it.who == who }

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
                Text("分寸", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (ui.rules.isEmpty()) "还没有记录" else "${ui.rules.size} 条 · 其中底线 ${ui.rules.count { it.line }} 条",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 记一条") { nav.navigate("ruleEdit?ruleId=0") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CatCell("全部", ui.rules.size, cat == "all", c.ink) { cat = "all" }
                CatCell("喜欢", ui.rules.count { it.cat == "like" }, cat == "like", Color(0xFF9FC4AE)) { cat = "like" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CatCell("讨厌", ui.rules.count { it.cat == "hate" }, cat == "hate", Color(0xFFD18B4E)) { cat = "hate" }
                CatCell("不能做", ui.rules.count { it.cat == "never" }, cat == "never", c.accentDeep) { cat = "never" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CatCell("我们的规矩", ui.rules.count { it.cat == "rule" }, cat == "rule", c.q) { cat = "rule" }
            }
        }

        com.xiaoman.memo.ui.components.ChipRow {
            listOf("all" to "全部", "her" to "她的", "him" to "他的", "both" to "共同的").forEach { (k, label) ->
                XmChip(label, on = who == k) { who = k }
            }
        }

        if (list.isEmpty()) {
            EmptyView("还没有记录")
        }
        list.forEach { r ->
            RuleCard(
                r,
                onEdit = { nav.navigate("ruleEdit?ruleId=${r.id}") },
                onDelete = { confirmDel = r },
            )
        }
    }

    confirmDel?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDel = null },
            title = { Text("删掉这条？") },
            text = { Text("「${r.text}」\n删掉后对方那边也会同步删除。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRule(r.id); confirmDel = null }) {
                    Text("删除", color = c.accentDeep)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RowScope.CatCell(name: String, count: Int, on: Boolean, bg: Color, onClick: () -> Unit) {
    val c = Xm
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) bg else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(13.dp, 12.dp),
    ) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) com.xiaoman.memo.ui.components.inkOn(bg) else MaterialTheme.colorScheme.onSurface)
        Text("$count 条", fontSize = 11.sp, color = if (on) com.xiaoman.memo.ui.components.inkOn(bg).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(r: RuleEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = Xm
    val barColor = when (r.cat) {
        "like" -> Color(0xFF9FC4AE)
        "hate" -> Color(0xFFE3A76B)
        "never" -> Color(0xFFD1624F)
        else -> c.q
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).height(38.dp).width(3.dp).clip(RoundedCornerShape(3.dp)).background(barColor))
        /* 正文区可点 = 进「改一条」。以前这里只有删除没有编辑，条目存进去就再也改不了。 */
        Column(Modifier.padding(start = 10.dp).weight(1f).clickable { onEdit() }) {
            Text(r.text, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
            /* 徽章用 FlowRow 自动折行（与雨过卡片同一套做法）：
               窄屏/大字号下宁可折到第二行，也不要被裁掉半截（可横滑的 ChipRow 虽然能滑到，
               但视觉上仍是断的）。 */
            FlowRow(
                Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagChip(RCAT_NAME[r.cat] ?: "", MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
                TagChip(RWHO_NAME[r.who] ?: "", MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
                if (r.line) TagChip("底线", Color(0xFFB03A50), Color(0xFFFBE0E4))
                if (r.agreed) TagChip("已双方确认", c.ok, c.okSoft)
            }
        }
        /* 两个固定宽度图标按钮：带 weight 的正文列会被压缩（正常换行），图标永远压不没 */
        com.xiaoman.memo.ui.components.IconAction(
            "✎", c.tagBg, MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        ) { onEdit() }
        com.xiaoman.memo.ui.components.IconAction(
            "✕", c.tagBg, MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 6.dp),
        ) { onDelete() }
    }
}

/* ── 记一条 / 改一条 分寸 ── */
@Composable
fun RuleEditScreen(nav: NavHostController, vm: AppViewModel, ruleId: Long = 0L) {
    val c = Xm
    val ui by vm.ui.collectAsState()

    /* 全部 rememberSaveable：系统重建 Activity（改字号/显示大小）时不能把填了一半的内容丢掉 */
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("rule") }
    var who by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("both") }
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var line by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var agreed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    /* 编辑时必须保留原行的 uuid / createdAt：它们是同步合并的身份键 */
    var uuid by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var createdTs by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0L) }
    /* 载入时的内容指纹：只用来判断「有没有改过」，避免打开老记录按返回就弹「还没保存」 */
    var originFp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var loaded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ruleId == 0L) }
    /* 保存被挡下时就地提示（以前是静默 return，看着像「保存没反应」） */
    var saveHint by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var confirmExit by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmDel by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    fun fingerprint() = listOf(cat, who, text.trim(), line.toString(), agreed.toString()).joinToString("|")

    /* 分寸没有单条查询接口，直接从已加载的列表里取；列表还没到就等它 size 变化再试 */
    LaunchedEffect(ruleId, ui.rules.size, loaded) {
        if (ruleId != 0L && !loaded) {
            ui.rules.firstOrNull { it.id == ruleId }?.let { r ->
                cat = r.cat; who = r.who; text = r.text; line = r.line; agreed = r.agreed
                uuid = r.uuid; createdTs = r.createdAt
                originFp = fingerprint(); loaded = true
            }
        }
    }

    fun saveNow(onDone: () -> Unit) {
        if (text.isBlank()) { saveHint = "写点什么再保存"; return }
        vm.saveRule(
            RuleEntity(
                id = ruleId, cat = cat, who = who, text = text.trim(), line = line, agreed = agreed,
                createdAt = if (createdTs > 0) createdTs else System.currentTimeMillis(),
                uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
            ),
        )
        onDone()
    }

    /* 新建：有输入就算脏；编辑：与载入时比对，改类别/归属/内容/开关都能识别出来 */
    val dirty = if (ruleId == 0L) text.isNotBlank() else (originFp.isNotBlank() && fingerprint() != originFp)
    BackHandler(enabled = dirty) { confirmExit = true }

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
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { if (dirty) confirmExit = true else nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(if (ruleId == 0L) "记一条" else "改一条", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                "保存", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { saveNow { nav.popBackStack() } }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        XmCard {
            Text("属于哪一类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 7.dp))
            com.xiaoman.memo.ui.components.ChipRow {
                listOf("like" to "喜欢", "hate" to "讨厌", "never" to "不能做", "rule" to "我们的规矩").forEach { (k, label) ->
                    XmChip(label, on = cat == k, accent = c.accent) { cat = k; saveHint = "" }
                }
            }
            Text("关于谁", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp))
            com.xiaoman.memo.ui.components.ChipRow {
                listOf("her" to "她", "him" to "他", "both" to "共同").forEach { (k, label) ->
                    XmChip(label, on = who == k, accent = c.accent) { who = k; saveHint = "" }
                }
            }
        }

        XmCard {
            Text("具体内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 7.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text, onValueChange = { text = it; saveHint = "" },
                modifier = Modifier.fillMaxWidth().height(84.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (text.isEmpty()) Text("例如：吵架时不许说分手", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
        }

        XmCard {
            SwitchRow("这是底线", "触犯会很严重，会自动置顶", line) { line = it; saveHint = "" }
            SwitchRow("需要对方确认", "区分单方期待与双方约定", agreed) { agreed = it; saveHint = "" }
        }

        if (saveHint.isNotBlank()) {
            Text(saveHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                .clickable { saveNow { nav.popBackStack() } },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ruleId == 0L) "保存这一条" else "保存改动", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        /* 删除入口：只在编辑已有记录时出现（软删 + 墓碑，会同步到对方） */
        if (ruleId != 0L) {
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable { confirmDel = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("删掉这一条", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmDel) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删掉这条？") },
            text = { Text("删掉后对方那边也会同步删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDel = false; vm.deleteRule(ruleId); nav.popBackStack() }) {
                    Text("删除", color = c.accentDeep)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("还没保存") },
            text = { Text("要保存这条，还是放弃？") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续编辑") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text("放弃") }
            },
        )
    }
}
