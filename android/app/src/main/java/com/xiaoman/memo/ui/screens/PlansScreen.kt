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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.PlanEntity
import com.xiaoman.memo.ui.components.SectionHead
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PlansScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlanEntity?>(null) }
    var editing by remember { mutableStateOf<PlanEntity?>(null) }

    val t = Dates.today()
    val dated = ui.plans.filter { it.date.isNotBlank() }
    val undatedPlans = ui.plans.filter { it.date.isBlank() }
    val todayPlans = dated.filter { Dates.parse(it.date) == t }.sortedBy { it.time }
    val tmrPlans = dated.filter { Dates.parse(it.date) == t.plusDays(1) }.sortedBy { it.time }
    val laterPlans = dated.filter { Dates.parse(it.date) > t.plusDays(1) }.sortedWith(compareBy({ it.date }, { it.time }))
    val next7 = dated.count { Dates.parse(it.date) <= t.plusDays(7) }

    // 纪念日未设置时（默认空白）不计算，显示占位符
    val annivSet = ui.profile.anniversary.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    val daysTogether = if (annivSet) Dates.diff(ui.profile.anniversary, Dates.fmt(t)).toInt().coerceAtLeast(0) else 0
    val toAnniv = if (annivSet) {
        val annivThisYear = LocalDate.parse(ui.profile.anniversary).withYear(t.year)
        val nextAnniv = if (!annivThisYear.isBefore(t)) annivThisYear else annivThisYear.plusYears(1)
        java.time.temporal.ChronoUnit.DAYS.between(t, nextAnniv).toInt()
    } else -1

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 14.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("往后", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text("接下来 7 天有 $next7 件事", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.weight(1f))
                com.xiaoman.memo.ui.components.XmActionChip("＋ 新建") { showNew = true }
            }
        }

        item {
            /* 金色纪念卡 */
            Row(
                Modifier
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
                    .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.goldSoft).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(if (annivSet) "$daysTogether" else "—", fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = c.gold)
                    Text("在一起的天数", fontSize = 12.sp, color = c.goldInk, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (annivSet) "$toAnniv 天" else "—", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = c.gold)
                    Text("后纪念日", fontSize = 11.sp, color = c.goldInk, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        if (todayPlans.isNotEmpty()) {
            item { SectionHead("今天") }
            items(todayPlans.size) { PlanRow(todayPlans[it], { deleting = todayPlans[it] }, { editing = todayPlans[it] }) }
        }
        if (tmrPlans.isNotEmpty()) {
            item { SectionHead("明天") }
            items(tmrPlans.size) { PlanRow(tmrPlans[it], { deleting = tmrPlans[it] }, { editing = tmrPlans[it] }) }
        }
        if (laterPlans.isNotEmpty()) {
            item { SectionHead("之后") }
            items(laterPlans.size) { PlanRow(laterPlans[it], { deleting = laterPlans[it] }, { editing = laterPlans[it] }) }
        }
        if (undatedPlans.isNotEmpty()) {
            item { SectionHead("随时想做", "不限日期") }
            items(undatedPlans.size) { PlanRow(undatedPlans[it], { deleting = undatedPlans[it] }, { editing = undatedPlans[it] }) }
        }
        if (ui.plans.isEmpty()) {
            item {
                Text(
                    "还没有约定",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (showNew) {
        AddPlanDialog(vm, null) { showNew = false }
    }

    editing?.let { p ->
        AddPlanDialog(vm, p) { editing = null }
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条约定？") },
            text = { Text(p.title) },
            confirmButton = {
                TextButton(onClick = { vm.deletePlan(p.id); deleting = null }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PlanRow(p: PlanEntity, onLongDelete: () -> Unit, onEdit: () -> Unit = {}) {
    val c = Xm
    /* 时间/日期一行 + 星期一行，都强制单行：日期用 M/d 短格式保证放进 52dp 框 */
    val whenBig: String
    val whenBigSize: Int
    val whenSmall: String
    if (p.date.isBlank()) {
        whenBig = "随时"; whenBigSize = 13; whenSmall = "不限日期"
    } else {
        val d = Dates.parse(p.date)
        val today = Dates.today()
        if (p.time.isNotBlank()) {
            whenBig = p.time; whenBigSize = 16
            whenSmall = when (d) {
                today -> "今天"
                today.plusDays(1) -> "明天"
                else -> "周" + Dates.weekdayCn(d).removePrefix("星期")
            }
        } else {
            whenBig = "${d.monthValue}/${d.dayOfMonth}"; whenBigSize = 14
            whenSmall = "周" + Dates.weekdayCn(d).removePrefix("星期")
        }
    }

    Row(
        Modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
            .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { onEdit() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(52.dp).clip(RoundedCornerShape(12.dp)).background(c.tagBg).padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(whenBig, fontSize = whenBigSize.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
            Text(whenSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp), maxLines = 1, softWrap = false)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(p.title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (p.note.isNotBlank()) {
                Text(p.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (p.date.isNotBlank() && p.title.contains("生日")) {
                val d = Dates.parse(p.date)
                val today = Dates.today()
                var anniv = d.withYear(today.year)
                if (anniv.isBefore(today)) anniv = anniv.plusYears(1)
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, anniv).toInt()
                Box(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("还有 $days 天", fontSize = 11.sp, color = c.accentDeep)
                }
            }
        }
        Text(
            " ✎", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false,
            modifier = Modifier.clickable { onEdit() },
        )
        Text(
            " ✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
            maxLines = 1, softWrap = false,
            modifier = Modifier.clickable { onLongDelete() },
        )
    }
}

/* 可点击的伪输入框：只负责显示当前值 + 响应点击弹选择器 */
@Composable
private fun FakeField(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

/* 新建 / 编辑两用（B2）：日期可不选（纯文字约定），有日期的按日期排序 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlanDialog(vm: AppViewModel, initial: PlanEntity?, onDone: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: Dates.fmt(Dates.today())) }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initial == null) "新的约定" else "编辑约定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, placeholder = { Text("做什么？") }, singleLine = true)
                /* ⚠️ 不能用 readOnly 的 OutlinedTextField 做选择入口：
                   TextField 会吞掉点击，外层 clickable 永远不触发（用户实测的「日期不能选」就是它）。
                   改成 Box 自绘字段，点击可靠。 */
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FakeField(
                        text = if (date.isBlank()) "不限日期 · 点此选择" else "📅 $date",
                        modifier = Modifier.weight(1f),
                        onClick = { showDate = true },
                    )
                    FakeField(
                        text = if (time.isBlank()) "时间（可选）" else "🕐 $time",
                        modifier = Modifier.weight(1f),
                        onClick = { showTime = true },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (date.isBlank()) "✓ 不限日期" else "改为不限日期",
                        fontSize = 12.sp,
                        color = if (date.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { date = ""; time = "" },
                    )
                    if (time.isNotBlank()) {
                        Text("清除时间", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { time = "" })
                    }
                }
                OutlinedTextField(note, { note = it }, placeholder = { Text("备注（可选）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    val entity = (initial ?: PlanEntity(date = "", time = "", title = "")).copy(
                        date = date, time = time, title = title.trim(), note = note.trim(),
                    )
                    if (initial == null) vm.addPlan(entity) else vm.updatePlan(entity)
                }
                onDone()
            }) { Text("保存", color = androidx.compose.material3.MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("取消") } },
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().let { Dates.fmt(it) }
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { date = ""; showDate = false }) { Text("不限日期") }
            },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val tp = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = tp) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(tp.hour, tp.minute)
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("清除") } },
        )
    }
}
