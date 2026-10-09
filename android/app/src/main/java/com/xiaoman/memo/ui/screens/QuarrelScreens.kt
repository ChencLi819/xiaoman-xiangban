package com.xiaoman.memo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.xiaoman.memo.data.QuarrelEntity
import com.xiaoman.memo.domain.Quarrel
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.FlipCard
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates

/* ── 雨过 列表 ── */
@Composable
fun QuarrelsScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var filter by remember { mutableStateOf("全部") }

    val list = ui.quarrels.filter {
        filter == "全部" || (filter == "已翻篇" && it.resolved) || (filter == "还没翻篇" && !it.resolved)
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
                Text("雨过", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("吵过的事，别白吵", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 记一次", accent = c.q) { nav.navigate("quarrelEdit?quarrelId=0&heat=3") }
        }

        /* 三个统计全部取真实数据：
           一共记下 = 条数；
           平均和好用时 = 已翻篇记录的 (翻篇时间-记下时间) 均值；
           最常因为 = 简述+情绪 的关键词族命中，打平取最近。 */
        val resolvedList = ui.quarrels.filter { it.resolved && it.resolvedAt > it.createdAt }
        val avgLabel = if (resolvedList.isEmpty()) {
            "—"
        } else {
            val avgH = resolvedList.map { (it.resolvedAt - it.createdAt) / 3_600_000.0 }.average()
            if (avgH >= 72) "约 ${(avgH / 24).toInt()} 天" else "约 ${avgH.toInt()}h"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QStat("${ui.quarrels.size}", "一共记下", Modifier.weight(1f))
            QStat(avgLabel, "平均和好用时", Modifier.weight(1f))
            QStat(Quarrel.topReason(ui.quarrels.map { Triple(it.text, it.emotions, it.createdAt) }), "最常因为", Modifier.weight(1f))
        }

        com.xiaoman.memo.ui.components.ChipRow {
            listOf("全部", "已翻篇", "还没翻篇").forEach { f -> XmChip(f, on = filter == f) { filter = f } }
        }

        if (list.isEmpty()) {
            EmptyView("还没有记录")
        }
        list.forEach { q ->
            QuarrelCard(
                q,
                me = ui.profile.identity,
                onOpen = { nav.navigate("quarrelEdit?quarrelId=${q.id}&heat=${q.heat}") },
                onToggleResolved = { vm.resolveQuarrel(q.id, !q.resolved) },
            )
        }
    }
}

@Composable
private fun QStat(num: String, label: String, modifier: Modifier = Modifier) {
    /* 与记录卡同一套几何（WB 的 .wb-card）：12dp 圆角 + 1dp 描边 + 无阴影。
       原来是 16dp 圆角带投影，和下面的新卡片放一起明显不是一套。 */
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(vertical = 13.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(num, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Xm.qDeep)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuarrelCard(q: QuarrelEntity, me: String, onOpen: () -> Unit, onToggleResolved: () -> Unit) {
    val c = Xm
    /* 卡片几何：圆角 12dp、描边 1dp、无阴影、内边距 16dp；
       配色仍用小满自己的（雨过紫 / 珊瑚粉 / 翻篇绿）。
       动作只留一个整行主按钮（翻篇是这张卡唯一要做的事），编辑收进右上角 32dp 图标按钮
       —— 这样结构上不可能再被窄屏或大字号挤坏。 */
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            /* 整卡可点 = 进「说开了」改这条记录（改写/换翻篇方式/补约定/删除都在那一页） */
            .clickable { onOpen() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                q.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            /* 图标按钮：32×32、圆角 8dp、浅底（WB 的图标按钮规格）——比纯文字更像"可以点" */
            com.xiaoman.memo.ui.components.IconAction(
                "✎", c.tagBg, MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            ) { onOpen() }
        }

        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Badge(Quarrel.faultName(q.fault, me), c.qSoft, c.qDeep)
            Badge(Dates.cn(q.date), c.tagBg, MaterialTheme.colorScheme.onSurfaceVariant)
            Badge(
                if (q.resolved) "已翻篇" else "还没翻篇",
                if (q.resolved) c.okSoft else c.tagBg,
                if (q.resolved) c.ok else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (q.cardText.isNotBlank()) {
            Box(
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(c.qSoft).padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("翻篇方式：${q.cardText}", fontSize = 12.sp, color = c.qDeep)
            }
        }
        if (q.pact.isNotBlank()) {
            Box(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(c.qSoft).padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("下次约好：${q.pact}", fontSize = 12.sp, color = c.qDeep)
            }
        }

        /* 主按钮：珊瑚粉实心（未翻篇）/ 描边次级（已翻篇）。整行宽，文字再长也挤不掉别人。 */
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (q.resolved) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                )
                .background(if (q.resolved) MaterialTheme.colorScheme.surface else c.accent)
                .clickable { onToggleResolved() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (q.resolved) "撤销翻篇" else "和好了，标记翻篇",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (q.resolved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, color = fg, maxLines = 1, softWrap = false)
    }
}

/* 「翻篇的方式」抽到的结果必须能跨 Activity 重建存活。
   它原来是普通 remember，而 dirty 基准（originFp / text / note…）全是 rememberSaveable ——
   一旦系统重建 Activity（改字号、改显示大小、分屏），card 丢了而其余状态还在，
   指纹就对不上 → 打开带翻篇方式的老记录、什么都没动、按返回也会弹「还没保存」。
   PoolItem 不是 Parcelable，用 listSaver 手写一份。 */
private val PoolItemSaver: androidx.compose.runtime.saveable.Saver<Quarrel.PoolItem?, Any> =
    androidx.compose.runtime.saveable.listSaver(
        save = { c -> if (c == null) emptyList() else listOf(c.c, c.t, c.m, c.lv) },
        restore = { l ->
            if (l.isEmpty()) null
            else Quarrel.PoolItem(l[0] as String, l[1] as String, l[2] as Boolean, l[3] as Int)
        },
    )

/* ── 说开了（记一次吵架）── */
@Composable
fun QuarrelEditScreen(nav: NavHostController, vm: AppViewModel, quarrelId: Long, heatArg: Int) {
    val c = Xm
    val ui by vm.ui.collectAsState()

    /* ⚠️ 全部用 rememberSaveable：跳去「抽一张」时本屏会离开组合，
       remember 的状态会随 composition 丢失（用户填到一半的原因/选项全没）。 */
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    /* 详细内容：与点滴一致 —— 标题一句话，想写多详细都可以；不写也能存 */
    var note by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var noteOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var fault by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("both") }
    var emotion by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var heat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(heatArg.coerceIn(1, 5)) }
    var card by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = PoolItemSaver) { mutableStateOf<Quarrel.PoolItem?>(null) }
    var pact by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var confirmExit by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var date by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Dates.fmt(Dates.today())) }
    var resolved by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var resolvedAt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0L) }
    /* 编辑时必须保留原行的 uuid / createdAt：它们是同步合并的身份键 */
    var originalUuid by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var createdTs by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0L) }
    /* 载入时的内容指纹：只用来判断「有没有改过」，避免打开老记录后按返回就弹「还没保存」 */
    var originFp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    /* 简述为空时保存被挡下，就地提示（以前是静默 no-op，看着像「保存没反应」） */
    var saveHint by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var confirmDel by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    fun fingerprint(): String = listOf(
        date, fault, emotion, heat.toString(),
        card?.t ?: "", pact.trim(), resolved.toString(), text.trim(), note.trim(),
    ).joinToString("|")

    LaunchedEffect(quarrelId) {
        if (quarrelId != 0L) {
            vm.daoQuarrel(quarrelId)?.let { q ->
                text = q.text; fault = q.fault; emotion = q.emotions; heat = q.heat
                pact = q.pact
                note = q.note; noteOpen = q.note.isNotBlank()
                date = q.date
                resolved = q.resolved
                resolvedAt = q.resolvedAt
                if (q.cardText.isNotBlank()) card = Quarrel.PoolItem(q.cardCat, q.cardText, q.cardMeet, q.cardLv)
                originalUuid = q.uuid; createdTs = q.createdAt
                originFp = fingerprint()
            }
        }
    }

    fun saveNow(onDone: () -> Unit) {
        /* 标题留空时用「详细内容」第一行兜底：内容已经写了就不该保存不上（与点滴同一套规则） */
        val title = text.trim().ifBlank {
            note.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
        if (title.isBlank()) { saveHint = "先写一句「因为什么」"; return }
        val q = QuarrelEntity(
            id = quarrelId, date = date, text = title, note = note.trim(),
            fault = fault, emotions = emotion, heat = heat,
            cardText = card?.t ?: "", cardCat = card?.c ?: "", cardLv = card?.lv ?: 0,
            cardMeet = card?.m ?: false, pact = pact.trim(),
            resolved = resolved, resolvedAt = resolvedAt,
            createdAt = if (createdTs > 0) createdTs else System.currentTimeMillis(),
            uuid = originalUuid.ifBlank { java.util.UUID.randomUUID().toString() },
        )
        vm.saveQuarrel(q) { onDone() }
    }

    /* 新建：有输入就算脏；编辑：与载入时比对，翻篇/改写/换翻篇方式都能识别出来 */
    val dirty = if (quarrelId == 0L) {
        text.isNotBlank() || pact.isNotBlank()
    } else {
        originFp.isNotBlank() && fingerprint() != originFp
    }

    BackHandler(enabled = dirty) { confirmExit = true }

    /* 从「抽一张」返回：接收所选翻篇方式 */
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val picked = handle?.getStateFlow("picked_card", "")?.collectAsState()?.value ?: ""
    LaunchedEffect(picked) {
        if (picked.isNotBlank()) {
            val p = picked.split("|")
            if (p.size >= 4) card = Quarrel.PoolItem(p[0], p[1], p[2] == "true", p[3].toIntOrNull() ?: 2)
            nav.currentBackStackEntry?.savedStateHandle?.set("picked_card", "")
        }
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
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface).clickable {
                    if (dirty) confirmExit = true else nav.popBackStack()
                }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(if (quarrelId == 0L) "说开了" else "改一改", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                "保存", color = c.qDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { saveNow { nav.popBackStack() } }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        XmCard {
            androidx.compose.foundation.text.BasicTextField(
                value = text, onValueChange = { text = it; saveHint = "" },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (text.isEmpty()) Text("一句话说说因为什么…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
        }

        /* 详细内容：复用点滴那个控件 —— 默认收起成一行入口，点开变多行，可再收起 */
        XmCard {
            com.xiaoman.memo.ui.components.NoteToggleField(
                open = noteOpen, value = note,
                onOpen = { noteOpen = true }, onClose = { noteOpen = false },
                onChange = { note = it; saveHint = "" },
            )
        }

        if (saveHint.isNotBlank()) {
            Text(saveHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        XmCard {
            Row(Modifier.padding(bottom = 10.dp)) {
                Text("这一次", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("  选不出来就选「说不清」", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Quarrel.faultOptions(ui.profile.identity).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (k, name, sub) ->
                            val on = fault == k
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (on) c.qSoft else MaterialTheme.colorScheme.surface)
                                    .clickable { fault = k }
                                    .padding(14.dp, 12.dp),
                            ) {
                                Text(name, fontSize = 14.sp, color = if (on) c.qDeep else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                                Text(sub, fontSize = 11.sp, color = if (on) c.qInk else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        XmCard {
            Text("当时的情绪", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 10.dp))
            com.xiaoman.memo.ui.components.ChipRow {
                Quarrel.EMOTIONS.forEach { e -> XmChip(e, on = emotion == e, accent = c.q) { emotion = if (emotion == e) "" else e } }
            }
        }

        XmCard {
            Row(Modifier.padding(bottom = 10.dp)) {
                Text("这次有多生气", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("  决定能抽到多重", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (1..5).forEach { lv ->
                    val on = heat == lv
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) c.heat[lv - 1] else MaterialTheme.colorScheme.surface)
                            .then(if (!on) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { heat = lv; card = null }
                            .padding(0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Quarrel.HEAT_NAMES[lv], fontSize = 12.sp,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Text(Quarrel.HEAT_DESC[heat], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 9.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        /* 我们的规矩入口（与分寸咬合） */
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { nav.navigate(R.RULES) }.padding(12.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我们的规矩", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (ui.rules.isEmpty()) "还没有约定，点这里补上" else "已约定 ${ui.rules.size} 条，吵架前先看看",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text("›", color = MaterialTheme.colorScheme.outline)
        }

        /* 翻篇的方式 */
        XmCard {
            Text("翻篇的方式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 10.dp))
            if (card == null) {
                Text("还没抽 · 点「去抽签」由系统随机选一个，这次归谁来领。", fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.drawGrad[0], c.drawGrad[1]))).padding(18.dp)) {
                    Text("翻 篇 的 方 式", fontSize = 11.sp, letterSpacing = 1.4.sp, color = c.drawCap)
                    Text(card!!.t, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = c.drawTxt, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.heat[card!!.lv - 1]).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(Quarrel.LEVEL_NAMES[card!!.lv], fontSize = 10.sp, color = Color.White)
                        }
                        Text("${card!!.c} · ${if (card!!.m) "下次见面" else "现在就能做"}", fontSize = 11.sp, color = c.qInk)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                        .clickable { nav.navigate("draw?quarrelId=$quarrelId&heat=$heat") }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("抽一张 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                        .clickable { card = null }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("我自己来", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        XmCard {
            Text("下次我们约好", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = pact, onValueChange = { pact = it },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, color = c.qDeep),
                decorationBox = { inner ->
                    Box { if (pact.isEmpty()) Text("把这次的教训变成一条可执行的约定…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
            Text("这条会存进「雨过」，下次吵架前可以翻出来看。", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
        }

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(c.qDeep)
                .clickable { saveNow { nav.popBackStack() } },
            contentAlignment = Alignment.Center,
        ) {
            Text("保存这次记录", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        /* 删除入口：只在编辑已存在的记录时出现（软删 + 墓碑，会同步到对方） */
        if (quarrelId != 0L) {
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable { confirmDel = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("删除这次记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmDel) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除这次记录？") },
            text = { Text("对方那边也会同步删除，删掉就不会再出现在「雨过」里了。") },
            confirmButton = {
                TextButton(onClick = { confirmDel = false; vm.deleteQuarrel(quarrelId); nav.popBackStack() }) {
                    Text("删除", color = c.qDeep)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("还没保存") },
            text = { Text("要保存这次记录，还是放弃？") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续编辑") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; nav.popBackStack() }) { Text("放弃") }
            },
        )
    }
}
