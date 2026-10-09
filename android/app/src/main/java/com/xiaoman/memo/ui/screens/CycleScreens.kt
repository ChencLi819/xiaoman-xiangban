package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.CycleEntity
import com.xiaoman.memo.domain.CycleMath
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.TagChip
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CycleScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var deleting by remember { mutableStateOf<CycleEntity?>(null) }

    val st = ui.cycleStats
    val phase = ui.phaseToday
    val today = Dates.today()
    var calYm by remember { mutableStateOf(java.time.YearMonth.now()) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 14.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("周期", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (ui.cycles.isEmpty()) "还没有记录" else "已记录 ${ui.cycles.size} 次 · 平均 ${st?.avgCycle ?: 28} 天",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.xiaoman.memo.ui.components.XmActionChip("＋ 记一次") { nav.navigate(R.CYCLE_EDIT) }
                    com.xiaoman.memo.ui.components.XmActionChip("记今天状态", accent = c.calO) { nav.navigate("cycleEdit?daily=1") }
                }
            }
        }

        item {
            /* 阶段卡 */
            if (phase == null || st == null) {
                Column(
                    Modifier.fillMaxWidth().shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(c.phGrad[1].first, c.phGrad[1].second))).padding(16.dp),
                ) {
                    Text("暂无经期记录", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("记录后这里会显示当前阶段", fontSize = 12.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 7.dp))
                }
            } else {
                val left = Dates.diff(Dates.fmt(today), st.nextStart).toInt()
                Column(
                    Modifier.fillMaxWidth().shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(c.phGrad[phase.k - 1].first, c.phGrad[phase.k - 1].second))).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(phase.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  周期第 ${phase.n} 天", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                    }
                    Text(CycleMath.PH_TIPS[phase.k] ?: "", fontSize = 12.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 8.dp))
                    Text("平均周期 ${st.avgCycle} 天 · 经期 ${st.avgPeriod} 天 · 已记录 ${st.records} 次", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 4.dp))

                    /* ── 周期时间轴：四个阶段按天数占比排布，当前阶段亮起 ── */
                    val spans = CycleMath.phaseSpans(st)
                    val curIdx = CycleMath.spanIndexOf(Dates.fmt(today), spans)
                    Row(
                        Modifier.padding(top = 13.dp).fillMaxWidth().height(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        spans.forEachIndexed { i, s ->
                            Box(
                                Modifier
                                    .weight(s.days.toFloat())
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = if (i == curIdx) 0.95f else 0.32f)),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                        spans.forEach { s ->
                            Text(
                                "${CycleMath.PH_NAMES[s.k]} ${s.days}天",
                                fontSize = 9.sp, lineHeight = 12.sp,
                                color = Color.White.copy(alpha = 0.88f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(s.days.toFloat()),
                            )
                        }
                    }
                    val nextLine = when {
                        curIdx in 0..2 -> {
                            val nx = spans[curIdx + 1]
                            "接下来：${CycleMath.PH_NAMES[nx.k]} · 预计 ${Dates.cn(nx.start)} 开始"
                        }
                        curIdx == 3 -> "接下来：下次经期 · 预计 ${Dates.cn(st.nextStart)} 开始"
                        else -> ""
                    }
                    if (nextLine.isNotEmpty()) {
                        Text(nextLine, fontSize = 11.sp, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 6.dp))
                    }

                    Box(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.22f)).padding(9.dp, 11.dp),
                    ) {
                        Text(
                            when {
                                left > 0 -> "下次经期预计 ${Dates.cn(st.nextStart)}（还有 $left 天）"
                                left == 0 -> "今天可能是经期开始日"
                                else -> "经期已推迟 ${-left} 天，注意身体"
                            },
                            fontSize = 12.sp, color = Color.White,
                        )
                    }
                }
            }
        }

        item { SectionHead("本周期四个阶段", if (st != null) "按平均周期估算" else "") }
        item {
            XmCard {
                if (st == null) {
                    Text(
                        "记录一次经期后，这里会展示完整的四个阶段",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    )
                } else {
                    val spans = CycleMath.phaseSpans(st)
                    val cur = CycleMath.spanIndexOf(Dates.fmt(today), spans)
                    spans.forEachIndexed { i, s ->
                        if (i > 0) {
                            Box(
                                Modifier.fillMaxWidth().height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.padding(top = 3.dp).height(11.dp).aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp)).background(c.phGrad[s.k - 1].second),
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        CycleMath.PH_NAMES[s.k] ?: "", fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (i == cur) {
                                        Box(Modifier.padding(start = 7.dp)) {
                                            TagChip("现在", MaterialTheme.colorScheme.onPrimary, c.accent)
                                        }
                                    }
                                    Box(Modifier.weight(1f))
                                    Text(
                                        "约 ${s.days} 天",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Text(
                                    "${Dates.cn(s.start)} – ${Dates.cn(s.end)}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 1.dp),
                                )
                                Text(
                                    CycleMath.PH_TIPS[s.k] ?: "",
                                    fontSize = 11.sp, lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "阶段区间按平均周期与末次经期推算，每个人的节奏略有不同，仅作了解身体的参考",
                        fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
        }

        item {
            XmCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        "‹",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { calYm = calYm.minusMonths(1) }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    Text(
                        "${calYm.year}年${calYm.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "›",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { calYm = calYm.plusMonths(1) }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                CycleCalendar(ui, calYm)
                Row(Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Legend(c.calP, "经期")
                    Legend(c.calF, "卵泡期")
                    Legend(c.calO, "排卵期")
                    Legend(c.calL, "黄体期")
                    Legend(c.calN, "预测经期", dashed = true)
                    if (calYm != java.time.YearMonth.now()) {
                        Box(Modifier.weight(1f))
                        Text(
                            "回到本月",
                            fontSize = 11.sp,
                            color = c.accent,
                            modifier = Modifier.clickable { calYm = java.time.YearMonth.now() },
                        )
                    }
                }
            }
        }

        item { SectionHead("预测", if (st != null) "基于 ${st.records} 次记录" else "") }
        item {
            XmCard {
                if (st == null) {
                    Text("暂无预测", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp))
                } else {
                    val spans = CycleMath.phaseSpans(st)
                    val fol = spans.first { it.k == 2 }
                    val ovu = spans.first { it.k == 3 }
                    val lut = spans.first { it.k == 4 }
                    /* 预测依据：讲清楚数字是怎么算出来的 */
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(11.dp, 10.dp),
                    ) {
                        Text("预测依据", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val basis = when (st.method) {
                            "median" -> "最近 ${st.usedGaps.size} 次周期（${st.usedGaps.joinToString(" · ")} 天）取中位数 ${st.avgCycle} 天"
                            "mean" -> "最近 2 次周期（${st.usedGaps.joinToString(" · ")} 天）取平均 ${st.avgCycle} 天"
                            "single" -> "仅有 1 次完整间隔（${st.usedGaps.first()} 天），暂按该间隔估算"
                            else -> "周期间隔记录不足，暂按 ${CycleMath.DEFAULT_CYCLE} 天标准周期估算"
                        }
                        Text(basis, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                        if (st.outliers.isNotEmpty()) {
                            Text(
                                "已忽略异常间隔：${st.outliers.joinToString(" · ")} 天（间隔过短或过长，可能漏记或误记）",
                                fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        if (st.irregular) {
                            Text(
                                "最近几次周期长短相差 ${st.usedGaps.max() - st.usedGaps.min()} 天，波动较大，预测仅供参考",
                                fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    PredRow("平均周期", "${st.avgCycle} 天", modifier = Modifier.padding(top = 6.dp))
                    PredRow("平均经期", "${st.avgPeriod} 天")
                    PredRow("卵泡期（估算）", "${Dates.cn(fol.start)} – ${Dates.cn(fol.end)}", c.phGrad[1].second)
                    PredRow("排卵期（估算）", "${Dates.cn(ovu.start)} – ${Dates.cn(ovu.end)}", c.phGrad[2].second)
                    PredRow("黄体期（估算）", "${Dates.cn(lut.start)} – ${Dates.cn(lut.end)}", c.phGrad[3].second)
                    PredRow("下次经期", Dates.cn(st.nextStart), c.accentDeep)
                    Text(
                        "排卵通常发生在下次月经前 14 天左右，其余阶段由预测周期与末次经期推算；以上均为估算值，仅作了解身体的参考",
                        fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
        }

        item { SectionHead("历史记录", "${ui.cycles.size} 条") }
        if (ui.cycles.isEmpty()) {
            item { EmptyView("暂无记录") }
        } else {
            // 按「记录当日」排序展示（B4），而不是统一显示经期开始日期
            val rows = ui.cycles.sortedByDescending { if (it.recordDate.isNotBlank()) it.recordDate else it.start }
            items(rows.size) { i ->
                val r = rows[i]
                val F = listOf("少", "中", "多")
                val P = listOf("没有", "轻微", "明显", "很痛")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { nav.navigate("cycleEdit?cycleId=${r.id}") }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(Dates.cn(if (r.recordDate.isNotBlank()) r.recordDate else r.start), MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (r.daily) {
                            Text(
                                "当日记录" + (if (r.mood.isNotBlank()) " · 心情 ${r.mood}" else "") + (if (r.symptoms.isNotBlank()) " · ${r.symptoms}" else ""),
                                fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                (if (r.end.isNotBlank()) "持续 ${Dates.diff(r.start, r.end).toInt() + 1} 天" else "进行中") + " · 量${F[r.flow.coerceIn(0, 2)]} · 痛经${P[r.pain.coerceIn(0, 3)]}",
                                fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (!r.daily && (r.symptoms.isNotBlank() || r.mood.isNotBlank())) {
                            Text(
                                listOf(r.symptoms, if (r.mood.isNotBlank()) "情绪 ${r.mood}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (r.note.isNotBlank()) {
                            Text(r.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (!r.daily && r.recordDate.isNotBlank() && r.recordDate != r.start) {
                            Text("记于 ${Dates.cn(r.recordDate)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 1.dp))
                        }
                    }
                    Text(" ✎", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.clickable { nav.navigate("cycleEdit?cycleId=${r.id}") })
                    Text(" ✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, softWrap = false, modifier = Modifier.clickable { deleting = r })
                }
            }
        }
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除 ${Dates.cn(if (r.recordDate.isNotBlank()) r.recordDate else r.start)} 的记录？") },
            confirmButton = { TextButton(onClick = { vm.deleteCycle(r.id); deleting = null }) { Text("删除", color = c.accentDeep) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(10.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(3.dp))
                .then(if (dashed) Modifier.border(1.5.dp, color, RoundedCornerShape(3.dp)) else Modifier.background(color)),
        )
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun PredRow(k: String, v: String, color: Color? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(k, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CycleCalendar(ui: com.xiaoman.memo.data.AppUiState, ym: java.time.YearMonth) {
    val c = Xm
    val today = Dates.today()
    val marks = CycleMath.monthMarks(ym.year, ym.monthValue, ui.cycles, ui.cycleStats)
    val firstDow = ym.atDay(1).dayOfWeek.value % 7 // 周日=0
    val daysInMonth = ym.lengthOfMonth()

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Text(w, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(3.dp))
        val cells = firstDow + daysInMonth
        val rows = (cells + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                repeat(7) { col ->
                    val idx = r * 7 + col - firstDow + 1
                    val day = idx
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..daysInMonth) {
                            val date = ym.atDay(day)
                            val m = marks[date]
                            val isToday = date == today
                            val bg = when (m) {
                                "p" -> c.calP
                                "o" -> c.calO
                                "f" -> c.calF
                                "l" -> c.calL
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                    .then(
                                        when {
                                            m == "n" -> Modifier.border(1.5.dp, c.calN, RoundedCornerShape(10.dp))
                                            else -> Modifier.background(bg)
                                        }
                                    )
                                    .then(if (isToday) Modifier.border(2.dp, c.accent, RoundedCornerShape(10.dp)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day",
                                    fontSize = 12.sp,
                                    fontWeight = if (m == "p" || m == "o") FontWeight.SemiBold else FontWeight.Normal,
                                    color = when (m) {
                                        "p" -> c.calPInk
                                        "o" -> c.calOInk
                                        "f" -> c.calFInk
                                        "l" -> c.calLInk
                                        "n" -> c.calNInk
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── 记一次 / 编辑 / 每日状态（周期表单，B4）── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleEditScreen(nav: NavHostController, vm: AppViewModel, cycleId: Long = 0L, dailyArg: Boolean = false) {
    val c = Xm
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf(1) }
    var pain by remember { mutableStateOf(0) }
    var mood by remember { mutableStateOf("") }
    var syms by remember { mutableStateOf(listOf<String>()) }
    var note by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf(dailyArg) }
    var err by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var originalCycle by remember { mutableStateOf<CycleEntity?>(null) }
    var loaded by remember { mutableStateOf(cycleId == 0L) }
    var recordDate by remember { mutableStateOf(Dates.fmt(Dates.today())) }

    LaunchedEffect(cycleId) {
        if (cycleId != 0L) {
            vm.daoCycle(cycleId)?.let { r ->
                originalCycle = r
                start = r.start; end = r.end; flow = r.flow; pain = r.pain
                mood = r.mood; syms = r.symptoms.split(",").filter { it.isNotBlank() }
                note = r.note; daily = r.daily
                recordDate = if (r.recordDate.isNotBlank()) r.recordDate else r.start
                loaded = true
            }
        }
    }

    val moods = listOf("平静", "烦躁", "低落", "想哭", "黏人", "还好")
    val symptoms = listOf("腰酸", "头痛", "腹胀", "疲惫", "胸胀", "失眠", "食欲差", "长痘")

    fun save() {
        if (daily) {
            // 每日记录：start = 记录当日，不参与周期计算
            val base = (originalCycle ?: CycleEntity(start = "", end = "", uuid = java.util.UUID.randomUUID().toString()))
                .copy(
                    id = originalCycle?.id ?: 0L, uuid = originalCycle?.uuid ?: java.util.UUID.randomUUID().toString(),
                    start = recordDate, end = "", flow = flow, pain = pain, mood = mood,
                    symptoms = syms.joinToString(","), note = note.trim(), daily = true, recordDate = recordDate,
                )
            vm.saveCycle(base)
            nav.popBackStack()
            return
        }
        if (start.isBlank()) { err = "请选择开始日期"; return }
        if (end.isNotBlank() && end < start) { err = "结束日期不能早于开始日期"; return }
        val base = (originalCycle ?: CycleEntity(start = "", end = "", uuid = java.util.UUID.randomUUID().toString()))
        vm.saveCycle(
            base.copy(
                id = originalCycle?.id ?: 0L, uuid = originalCycle?.uuid ?: java.util.UUID.randomUUID().toString(),
                start = start, end = end, flow = flow, pain = pain, mood = mood,
                symptoms = syms.joinToString(","), note = note.trim(), daily = false,
                recordDate = if (originalCycle != null) (if (originalCycle!!.recordDate.isNotBlank()) originalCycle!!.recordDate else recordDate) else recordDate,
            ),
        )
        nav.popBackStack()
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
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                when { daily -> "记今天状态"; cycleId != 0L -> "编辑记录"; else -> "记一次" },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            Text(
                "保存", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { save() }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        if (daily) {
            XmCard {
                FormRow("记录日期（哪天的状态就记哪天，可补录）") {
                    DateField(recordDate) { pickDate = "record" }
                }
                FormRow("提示") {
                    Text(
                        "当日状态记录不会参与周期平均与预测，只用来留痕这一天的心情和身体感受",
                        fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            XmCard {
                FormRow("开始日期") {
                    DateField(start.ifBlank { "点此选择" }) { pickDate = "start" }
                }
                FormRow("结束日期（还没结束可留空，事后可随时补填或修改）") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(end.ifBlank { "点此选择" }, Modifier.weight(1f)) { pickDate = "end" }
                        if (end.isNotBlank()) {
                            Text("清除", fontSize = 12.sp, color = c.accent, modifier = Modifier.clickable { end = "" })
                        }
                    }
                }
            }
        }

        XmCard {
            FormRow("经量" + if (daily) "（可选）" else "") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("少" to 0, "中" to 1, "多" to 2).forEach { (label, v) ->
                        XmChip(label, on = flow == v, accent = c.accent) { flow = v }
                    }
                }
            }
            FormRow("痛经程度" + if (daily) "（可选）" else "") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("没有" to 0, "轻微" to 1, "明显" to 2, "很痛" to 3).forEach { (label, v) ->
                        XmChip(label, on = pain == v, accent = c.accent) { pain = v }
                    }
                }
            }
        }

        XmCard {
            FormRow("情绪") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    moods.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { m -> XmChip(m, on = mood == m, accent = c.accent) { mood = if (mood == m) "" else m } }
                        }
                    }
                }
            }
            FormRow("症状（可多选）") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    symptoms.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { s -> XmChip(s, on = s in syms, accent = c.accent) { syms = if (s in syms) syms - s else syms + s } }
                        }
                    }
                }
            }
        }

        XmCard {
            Text("想说点什么", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 7.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = note, onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (note.isEmpty()) Text("今天特别累，想被抱着", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
        }

        if (err.isNotEmpty()) {
            Text(err, fontSize = 12.sp, color = c.accentDeep, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                .clickable { save() },
            contentAlignment = Alignment.Center,
        ) { Text("保存这次记录", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }

    pickDate?.let { which ->
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        when (which) {
                            "start" -> start = Dates.fmt(d)
                            "end" -> end = Dates.fmt(d)
                            else -> recordDate = Dates.fmt(d)
                        }
                    }
                    pickDate = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 7.dp))
        content()
    }
}

@Composable
private fun DateField(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(11.dp, 12.dp),
    ) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
