package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.domain.Quarrel
import com.xiaoman.memo.ui.components.FlipCard
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.launch

@Composable
fun DrawScreen(nav: NavHostController, vm: AppViewModel, quarrelId: Long?, heatArg: Int) {
    val c = Xm
    val ui by vm.ui.collectAsState()
    var curLv by remember { mutableIntStateOf(heatArg.coerceIn(1, 5)) }
    var qMo by remember { mutableStateOf("all") }      // all | now | meet
    var qCat by remember { mutableStateOf("全部") }
    var current by remember { mutableStateOf<Quarrel.PoolItem?>(null) }
    var flipTick by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Quarrel.PoolItem?>(null) }
    var deleting by remember { mutableStateOf<Quarrel.PoolItem?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val cats = listOf("全部") + com.xiaoman.memo.data.Seed.POOL_GROUPS.map { it.c }

    fun poolView(): List<Quarrel.PoolItem> = ui.qPool
        .filter { it.lv == curLv }
        .filter { qMo == "all" || (qMo == "now" && !it.m) || (qMo == "meet" && it.m) }
        .filter { qCat == "全部" || it.c == qCat }

    fun draw(force: Boolean) {
        val v = poolView()
        if (v.isEmpty()) { current = null; return }
        var n = (0 until v.size).random()
        if (!force && v.size > 1 && current != null) {
            var g = 0
            while (v[n].t == current!!.t && g++ < 20) n = (0 until v.size).random()
        }
        current = v[n]
        flipTick++
    }

    LaunchedEffect(Unit) { draw(true) }

    val view = poolView()
    val lvTotal = ui.qPool.count { it.lv == curLv }

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
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text("抽一张", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(34.dp).width(34.dp))
        }

        SectionHead("这次有多生气", "$lvTotal 条可选")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            (1..5).forEach { lv ->
                val on = curLv == lv
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.heat[lv - 1] else MaterialTheme.colorScheme.surface)
                        .then(if (!on) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { curLv = lv; draw(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Quarrel.HEAT_NAMES[lv], fontSize = 12.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Text(Quarrel.HEAT_DESC[curLv], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        /* 抽签卡 */
        FlipCard(trigger = flipTick) {
            Column(
                Modifier.fillMaxWidth().height(200.dp).shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(c.drawGrad[0], c.drawGrad[1])))
                    .clickable { draw(false) }.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("翻 篇 的 方 式", fontSize = 11.sp, letterSpacing = 1.4.sp, color = c.drawCap)
                if (current != null) {
                    Text(current!!.t, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = c.drawTxt, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.heat[current!!.lv - 1]).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(Quarrel.LEVEL_NAMES[current!!.lv], fontSize = 10.sp, color = Color.White)
                        }
                        Text("${current!!.c} · ${if (current!!.m) "下次见面" else "现在就能做"}", fontSize = 11.sp, color = c.qInk)
                    }
                } else {
                    Text("这个组合下暂时没有\n换个筛选试试", fontSize = 14.sp, color = c.drawCap, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                    .clickable { draw(false) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("换一张", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.accent)
                    .clickable {
                        val p = current ?: return@clickable
                        if (quarrelId != null && quarrelId != 0L) {
                            val qid = quarrelId
                            scope.launch {
                                vm.daoQuarrel(qid)?.let { q ->
                                    vm.saveQuarrel(q.copy(cardText = p.t, cardCat = p.c, cardLv = p.lv, cardMeet = p.m)) { }
                                }
                                nav.popBackStack()
                            }
                        } else {
                            nav.previousBackStackEntry?.savedStateHandle?.set("picked_card", "${p.c}|${p.t}|${p.m}|${p.lv}")
                            nav.popBackStack()
                        }
                    }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("就这个 ✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
        }

        SectionHead("翻篇点子库", "共 ${view.size} 条")
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.xiaoman.memo.ui.components.ChipRow(Modifier.weight(1f)) {
                XmChip("全部时机", on = qMo == "all") { qMo = "all" }
                XmChip("现在就能做", on = qMo == "now") { qMo = "now" }
                XmChip("下次见面", on = qMo == "meet") { qMo = "meet" }
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 自定义") { showAdd = true }
        }
        com.xiaoman.memo.ui.components.ChipRow {
            cats.forEach { k -> XmChip(k, on = qCat == k) { qCat = k } }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            view.take(40).forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { current = p; flipTick++ }.padding(10.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.qSoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("${i + 1}", fontSize = 10.sp, color = c.qDeep)
                    }
                    Text(p.t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.tagBg).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(if (p.m) "见面时" else "现在", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        " ✎", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).clickable { editing = p },
                    )
                    Text(
                        " ✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 6.dp).clickable { deleting = p },
                    )
                }
            }
            if (view.size > 40) {
                Text("仅显示前 40 条，用筛选缩小范围", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        XmCard {
            Text("小提示", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
            Text(Quarrel.NOTE, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showAdd) {
        PoolEditDialog(
            editing = null,
            cats = cats.filter { it != "全部" },
            onSave = { text, catSel, meet, lv -> vm.addPoolItem("pool", text, catSel, meet, lv) },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { p ->
        PoolEditDialog(
            editing = p,
            cats = cats.filter { it != "全部" },
            onSave = { text, catSel, meet, lv -> vm.editPoolItem("pool", p.t, text, catSel, meet, lv) },
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

/* 新增/编辑翻篇方式：文本 + 分类 + 兑现时机 + 火气等级；editing=null 表示新增 */
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
