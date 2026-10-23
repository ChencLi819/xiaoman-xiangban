package com.xiaoman.memo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.domain.ActItem
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.TagChip
import com.xiaoman.memo.ui.components.XmActionChip
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm

/* 翻翻看：互动库的独立浏览页（v1.5）
   - 互动父页面只保留一个入口通道，具体条目不再出现在父页面的栏目里；
   - 这里用 LazyColumn 承载四位数条目，搜索 / 分类 / 状态三重筛选；
   - 增删改仍在「互动库」，本页只管看与标记（收藏 / 已玩）。 */
@Composable
fun FlipBrowseScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("全部") }
    var mode by remember { mutableStateOf("all") }   // all | fav | done | new

    val marks = ui.actMarks.associateBy { it.text }
    val cats = listOf("全部") + ACT_CATS
    val pool = ui.actPool

    val view = pool.filter { a ->
        (cat == "全部" || a.c == cat) &&
            (q.isBlank() || a.t.contains(q, true)) &&
            when (mode) {
                "fav" -> marks[a.t]?.fav == true
                "done" -> marks[a.t]?.played == true
                "new" -> marks[a.t]?.played != true
                else -> true
            }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("翻翻看", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "${pool.size} 条 · 已玩 ${marks.count { it.value.played }} · 收藏 ${marks.count { it.value.fav }}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp),
                )
            }
            XmActionChip("互动库") { nav.navigate(R.LIBRARY) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp, 13.dp),
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
                        if (q.isEmpty()) Text("搜一条想做的…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
                singleLine = true,
            )
        }

        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${view.size} 条",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        com.xiaoman.memo.ui.components.ChipRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            listOf("all" to "全部", "fav" to "收藏", "done" to "已玩过", "new" to "还没玩").forEach { (k, label) ->
                XmChip(label, on = mode == k) { mode = k }
            }
        }
        com.xiaoman.memo.ui.components.ChipRow(Modifier.padding(horizontal = 20.dp)) {
            cats.forEach { k -> XmChip(k, on = cat == k) { cat = k } }
        }

        if (view.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) { EmptyView("这里还没有内容") }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(view.size) { i ->
                    val a = view[i]
                    FlipRow(
                        a = a,
                        fav = marks[a.t]?.fav == true,
                        played = marks[a.t]?.played == true,
                        onFav = { vm.toggleActFav(a.t) },
                        onPlayed = { vm.toggleActPlayed(a.t) },
                    )
                }
            }
        }
    }
}

/* 单条：分类 + 内容 + 两个标记按钮。整行不承担「抽签」职责（抽签在互动页与雨过「抽一张」） */
@Composable
private fun FlipRow(a: ActItem, fav: Boolean, played: Boolean, onFav: () -> Unit, onPlayed: () -> Unit) {
    val c = Xm
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagChip(a.c, MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
        Text(
            a.t,
            fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (a.custom) {
            Text("自定义", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
        FlipMark("♥", fav, Color(0xFFB85F73), Color(0xFFFBE0E7), onFav)
        FlipMark("✓", played, c.ok, c.okSoft, onPlayed)
    }
}

@Composable
private fun FlipMark(sym: String, on: Boolean, fg: Color, bg: Color, onClick: () -> Unit) {
    val c = Xm
    Box(
        Modifier.padding(start = 6.dp).height(28.dp).clip(RoundedCornerShape(9.dp))
            .background(if (on) bg else c.tagBg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sym, fontSize = 13.sp,
            color = if (on) fg else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
