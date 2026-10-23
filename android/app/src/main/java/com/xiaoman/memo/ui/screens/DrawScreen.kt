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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.launch

@Composable
fun DrawScreen(nav: NavHostController, vm: AppViewModel, quarrelId: Long?, heatArg: Int) {
    val c = Xm
    val ui by vm.ui.collectAsState()
    var curLv by remember { mutableIntStateOf(heatArg.coerceIn(1, 5)) }
    var current by remember { mutableStateOf<Quarrel.PoolItem?>(null) }
    var flipTick by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun drawOne(force: Boolean) {
        val v = ui.qPool.filter { it.lv == curLv }
        if (v.isEmpty()) { current = null; return }
        var n = (0 until v.size).random()
        if (!force && v.size > 1 && current != null) {
            var g = 0
            while (v[n].t == current!!.t && g++ < 20) n = (0 until v.size).random()
        }
        current = v[n]
        flipTick++
    }

    LaunchedEffect(Unit) { drawOne(true) }
    /* 火气等级切换时重抽一张，避免显示上一级的卡 */
    LaunchedEffect(curLv) { drawOne(true) }

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
                        .clickable { curLv = lv },
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
                    .clickable { drawOne(false) }.padding(22.dp),
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
                    Text("这个等级下暂时没有条目\n去点子库看看", fontSize = 14.sp, color = c.drawCap, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                    .clickable { drawOne(false) }.padding(vertical = 12.dp),
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

        /* ── 翻篇点子库：单独入口通道 ──
           2600 条不再内嵌在本页（以前只能显示前 40 条），搜索 / 筛选 / 增删改
           全部收进独立的「翻篇点子库」页面，与互动那边「翻翻看」同一模式。 */
        PoolEntryCard(total = ui.qPool.size) { nav.navigate(R.POOL_LIB) }

        XmCard {
            Text("小提示", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
            Text(Quarrel.NOTE, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* 翻篇点子库的入口卡：只做「通道」，不展示具体条目。
   与互动页「翻翻看」入口卡同款式，用雨过的渐变配色区分板块。 */
@Composable
private fun PoolEntryCard(total: Int, onOpen: () -> Unit) {
    val c = Xm
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 5.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(c.drawGrad[0], c.drawGrad[1])))
            .clickable { onOpen() }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("翻篇点子库", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.drawTxt)
                Text(
                    "$total 条",
                    fontSize = 11.sp, color = c.drawCap, modifier = Modifier.padding(top = 5.dp),
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text("进入 ›", fontSize = 12.sp, color = c.drawTxt, fontWeight = FontWeight.SemiBold) }
        }
    }
}
