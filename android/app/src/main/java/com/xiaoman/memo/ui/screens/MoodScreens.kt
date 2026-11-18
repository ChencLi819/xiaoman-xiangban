package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.xiaoman.memo.R as AppR
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.MoodEntity
import com.xiaoman.memo.domain.Moods
import com.xiaoman.memo.ui.components.Seg
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import java.time.LocalDate
import java.time.YearMonth

/* ══════════════════════════════════════════════════════════════
   v1.6「心晴」：把每天的心情说出来
   —— 此刻页顶部一张卡：先只给五档纯文字，选完才看动漫大图；
   —— 历史按日期表看，可切「我 / TA / 两人」；
   —— 数据落 Room（moods 表），进同步、进备份，桌面小工具同源。
   ══════════════════════════════════════════════════════════════ */

/* 五档配色：卡片底 + 文字色。与原型 / 小组件令牌一一对应 */
@Composable
private fun moodTone(key: String): Pair<Color, Color> {
    val c = Xm
    return when (key) {
        "tian" -> c.herSoft to c.herInk
        "xiang" -> c.goldSoft to c.goldInk
        "lei" -> c.himSoft to c.himInk
        "sang" -> c.qSoft to c.qInk
        else -> c.accentSoft to c.accentDeep
    }
}

/* 身份配色（卡片描边、标题字色跟人走：她珊瑚 / 他雾蓝） */
@Composable
private fun identityTone(author: String): Pair<Color, Color> {
    val c = Xm
    return if (author == "him") c.himSoft to c.himInk else c.herSoft to c.herInk
}

/* 插画：她 / 他 × 五档，直接映射资源（不走反射）。
   给两种尺寸写法：固定 dp（卡片、日历格子用 Int）与自适应 Dp（大图页按剩余高度算）。 */
@Composable
fun MoodArt(author: String, key: String, size: Int, modifier: Modifier = Modifier) =
    MoodArt(author, key, size.dp, modifier)

@Composable
fun MoodArt(author: String, key: String, size: Dp, modifier: Modifier = Modifier) {
    val id = if (author == "him") {
        when (key) {
            "xiang" -> AppR.drawable.mood_him_xiang
            "lei" -> AppR.drawable.mood_him_lei
            "sang" -> AppR.drawable.mood_him_sang
            "qi" -> AppR.drawable.mood_him_qi
            else -> AppR.drawable.mood_him_tian
        }
    } else {
        when (key) {
            "xiang" -> AppR.drawable.mood_her_xiang
            "lei" -> AppR.drawable.mood_her_lei
            "sang" -> AppR.drawable.mood_her_sang
            "qi" -> AppR.drawable.mood_her_qi
            else -> AppR.drawable.mood_her_tian
        }
    }
    Image(
        painter = painterResource(id),
        contentDescription = Moods.nameOf(key),
        modifier = modifier.size(size),
    )
}

/* ── ① 此刻页 · 今日心情卡 ──
   未记：标题 + 五档纯文字（白底细边，字色跟该档）+ 昨天/对方状态
   已记：收成一行：小图 + 「今天 · 甜」+ 文案 + 看大图 */
@Composable
fun TodayMoodCard(
    me: String,
    mine: MoodEntity?,
    yesterday: MoodEntity?,
    partnerName: String,
    partnerToday: MoodEntity?,
    onPick: (String) -> Unit,
    onOpenBig: () -> Unit,
    onGoHistory: () -> Unit,
) {
    val c = Xm
    val (cardBg, cardInk) = identityTone(me)

    if (mine == null) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.5.dp, cardInk.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天的心情", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cardInk)
                    Text("选一个", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
                }
                Text(
                    "心晴 ›",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onGoHistory() },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Moods.ALL.forEach { m ->
                    val (_, ink) = moodTone(m.key)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { onPick(m.key) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, letterSpacing = 0.5.sp)
                    }
                }
            }
            val yName = yesterday?.let { Moods.nameOf(it.mood) }.orEmpty()
            val pName = partnerToday?.let { Moods.nameOf(it.mood) }.orEmpty()
            val line = buildString {
                append("♡ ")
                append(if (yName.isBlank()) "昨天没记" else "昨天 · $yName")
                append(" ｜ ")
                append(if (pName.isBlank()) "$partnerName 今天还没打卡" else "$partnerName 今天 · $pName")
            }
            Text(
                line,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 11.dp),
            )
        }
        return
    }

    /* 已打卡：折叠成一行，点整行可放大图 */
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.5.dp, cardInk.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .clickable { onOpenBig() }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoodArt(me, mine.mood, 46)
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text("今天 · ${Moods.nameOf(mine.mood)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cardInk)
            Text(Moods.subOf(mine.mood), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Text("点一下可换", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp))
        }
        Text("看大图 ›", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ── ② 大图页 ──
   一屏一张插画：先看图再确认。「换一个」在底部展开五档，选完图立刻换。 */
@Composable
fun MoodBigDialog(
    author: String,
    initialKey: String,
    dateText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = Xm
    var key by remember { mutableStateOf(Moods.normalize(initialKey)) }
    var picking by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,   // 窗口由系统避开状态栏/导航栏，内容永不延伸到栏下
        ),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            /* 全屏 Dialog 不会自己避开状态栏 / 导航栏：不加这层 inset，
               底部按钮会被手势条或三键导航压掉（真机上就是这么切掉的）。 */
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .verticalScroll(rememberScrollState())   // 兜底：内容超出可视区时滚动，按钮不可能被切
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                /* 顶栏：返回 + 日期（右侧留白对齐） */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "‹",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(26.dp).clickable { onDismiss() },
                    )
                    Text(
                        dateText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(26.dp))
                }

                Spacer(Modifier.height(8.dp))

                /* 展示区：插画 + 文案（自然流式，按钮跟在内容下面，不钉屏幕底部）。
                   插画只按「宽度 72%」自适应、上限 340dp —— 与屏幕高度零耦合，任何机型都不挤压按钮。 */
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val side = minOf(maxWidth * 0.72f, 340.dp).coerceAtLeast(120.dp)
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background),
                                ),
                            )
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MoodArt(author, key, side)
                    }
                    Text(
                        "今天 · ${Moods.nameOf(key)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        Moods.subOf(key),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "♡ 已记进今天的心情",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    }
                }

                /* 换一档：展开五档，点选立刻换图 */
                if (picking) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Moods.ALL.forEach { m ->
                            val sel = m.key == key
                            val (bg, ink) = moodTone(m.key)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) ink else MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.5.dp,
                                        if (sel) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { key = m.key }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (sel) Color.White else ink,
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { picking = !picking }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (picking) "收起" else "换一个",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(c.accent, c.accentDeep)))
                            .clickable { onConfirm(key) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("就这样", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/* ── ③ 心晴 · 历史日期表 ── */
@Composable
fun MoodHistoryScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    val me = ui.profile.identity.ifBlank { "her" }
    val partner = if (me == "him") "her" else "him"
    val meName = if (me == "him") ui.profile.himName.ifBlank { "他" } else ui.profile.herName.ifBlank { "她" }
    val partnerName = if (me == "him") ui.profile.herName.ifBlank { "她" } else ui.profile.himName.ifBlank { "他" }

    var mode by remember { mutableStateOf(0) }          // 0 我 / 1 TA / 2 两人
    var ym by remember { mutableStateOf(YearMonth.now()) }

    /* 当前这一月记下的天数（副标题只报数据） */
    val monthCount = ui.moods.count { it.date.startsWith(ym.toString()) }

    /* date+author → mood，查格子用 */
    val byDate = remember(ui.moods, mode) {
        val persons = when (mode) {
            0 -> listOf(me)
            1 -> listOf(partner)
            else -> listOf(me, partner)
        }
        ui.moods.filter { it.author in persons }.groupBy { it.date }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp, 14.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "‹",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(top = 2.dp, end = 8.dp)
                    .clickable { nav.popBackStack() },
            )
            Column {
                Text("心晴", style = com.xiaoman.memo.ui.theme.T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (monthCount > 0) "记了 $monthCount 天" else "还没有记录",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Seg(
            options = listOf("我（$meName）", "TA（$partnerName）", "两人"),
            selected = mode,
            onSelect = { mode = it },
        )

        /* 月历 */
        XmCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { ym = ym.minusMonths(1) }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Text(
                    "${ym.year}年${ym.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { ym = ym.plusMonths(1) }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            /* 星期头（周日起始，与原型一致） */
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                    Text(
                        w, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }

            MoodMonthGrid(
                ym = ym,
                mode = mode,
                me = me,
                partner = partner,
                byDate = byDate,
            )

            if (mode == 2) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(c.her, "上 = 她")
                    LegendDot(c.him, "下 = 他")
                }
            }

            /* 本月汇总 */
            val sumSrc = when (mode) {
                0 -> ui.moods.filter { it.author == me }
                1 -> ui.moods.filter { it.author == partner }
                else -> ui.moods.filter { it.author == me || it.author == partner }
            }.filter { Dates.parse(it.date).let { d -> d.year == ym.year && d.monthValue == ym.monthValue } }
            val counts = sumSrc.groupingBy { it.mood }.eachCount()
            if (counts.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Moods.ALL.filter { (counts[it.key] ?: 0) > 0 }.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m ->
                                val (bg, ink) = moodTone(m.key)
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(bg)
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    /* 两人模式是合计，不放任何一个人的脸 */
                                    if (mode != 2) MoodArt(if (mode == 1) partner else me, m.key, 18)
                                    Text(
                                        "${m.name} × ${counts[m.key]}",
                                        fontSize = 11.sp, color = ink,
                                        modifier = Modifier.padding(start = if (mode == 2) 0.dp else 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /* 最近记下的心情 */
        val recent = ui.moods
            .filter { if (mode == 2) true else it.author == (if (mode == 0) me else partner) }
            /* 同日两条靠 updatedAt 兜底，避免顺序随查询漂移 */
            .sortedWith(compareByDescending<MoodEntity> { it.date }.thenByDescending { it.updatedAt })
            .take(6)
        /* 跨年的那几条要带年份，否则和今年的「11月6日」混在一起；同一年则不长个 */
        val thisYear = LocalDate.now().year
        val dateW = if (recent.any { Dates.yearOf(it.date) != thisYear }) 92.dp else 62.dp
        XmCard {
            Text("最近记下的心情", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (recent.isEmpty()) {
                Text(
                    "还没有记录",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            recent.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Dates.cnY(m.date, thisYear),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(dateW),
                    )
                    MoodArt(m.author, m.mood, 22)
                    Text(
                        Moods.nameOf(m.mood),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    if (mode == 2) {
                        Text(
                            if (m.author == "him") "他" else "她",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Box(Modifier.weight(1f))
                    Text(
                        Moods.subOf(m.mood),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* 月历格子：单人一格一张；两人一格上下两张 */
@Composable
private fun MoodMonthGrid(
    ym: YearMonth,
    mode: Int,
    me: String,
    partner: String,
    byDate: Map<String, List<MoodEntity>>,
) {
    val today = LocalDate.now()
    val days = ym.lengthOfMonth()
    val lead = ym.atDay(1).dayOfWeek.value % 7   // 周日=0
    val cells = lead + days
    val rows = (cells + 6) / 7

    /* 单人模式取谁：mode 0 =「我」，mode 1 =「TA」。
       旧写法恒按 me 取，切到 TA 时格子里永远是空的。 */
    val who = if (mode == 1) partner else me

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col
                    val day = idx - lead + 1
                    if (day < 1 || day > days) {
                        Box(Modifier.weight(1f).aspectRatio(0.86f))
                        continue
                    }
                    val date = ym.atDay(day)
                    val dateStr = Dates.fmt(date)
                    val list = byDate[dateStr].orEmpty()
                    val single = list.find { it.author == who }
                    /* 两人模式固定「她上他下」，与图例一致，不跟当前账号漂移 */
                    val her = list.find { it.author == "her" }
                    val him = list.find { it.author == "him" }
                    val isToday = date == today
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.86f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isToday) MaterialTheme.colorScheme.surface
                                else Color.Transparent,
                            )
                            .border(
                                if (isToday) 1.dp else 0.dp,
                                if (isToday) Xm.accent.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.toString(),
                            fontSize = 9.sp,
                            /* 不写 lineHeight 会继承 bodyLarge 的 24sp 行高，把格子里的插画挤没 */
                            lineHeight = 10.sp,
                            color = if (isToday) Xm.accentDeep else MaterialTheme.colorScheme.outline,
                        )
                        /* 插画尺寸由格子剩余空间算，双人模式上下各占一半 —— 不再固定 17/24dp 被裁 */
                        BoxWithConstraints(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (mode == 2) {
                                /* 上下两张各占一半，中间留 2dp 空隙：不留这道缝会贴在一起看着像粘连 */
                                val gap = 2.dp
                                val each = minOf(maxWidth, maxOf(0.dp, (maxHeight - gap) / 2))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    if (her != null) MoodArt("her", her.mood, each)
                                    else Spacer(Modifier.height(each))
                                    if (him != null) MoodArt("him", him.mood, each)
                                    else Spacer(Modifier.height(each))
                                }
                            } else {
                                val side = minOf(maxWidth, maxHeight)
                                if (single != null) MoodArt(who, single.mood, side)
                                else Spacer(Modifier.size(side))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
