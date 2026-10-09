package com.xiaoman.memo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.LetterEntity
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.MonthBar
import com.xiaoman.memo.ui.components.Seg
import com.xiaoman.memo.ui.components.WhoDot
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import com.xiaoman.memo.util.ImageStore
import kotlinx.coroutines.flow.map

/* ── 情书列表 ── */
@Composable
fun LettersScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    var q by remember { mutableStateOf("") }

    val list = ui.letters
        .filter { q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("情书", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("${ui.letters.size} 封", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 写一封") { nav.navigate("letterEdit?letterId=0") }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 9.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (q.isEmpty()) Text("搜标题或正文里的关键词", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
                singleLine = true,
            )
        }

        if (ui.letters.isEmpty()) {
            EmptyView("还没有情书")
        } else if (list.isEmpty()) {
            EmptyView("没有匹配的内容")
        } else {
            val groups = list.groupBy { it.date.substring(0, 7) }.toSortedMap(compareByDescending { it })
            groups.forEach { (month, ls) ->
                MonthBar(month.replace("-", "年") + "月")
                ls.forEach { l -> LetterCard(l, vm) { nav.navigate("letterEdit?letterId=${l.id}") } }
            }
        }
    }
}

@Composable
private fun LetterCard(l: LetterEntity, vm: AppViewModel, onClick: () -> Unit) {
    val c = Xm
    val ctx = LocalContext.current
    val firstImg = l.images.split(",").firstOrNull { it.isNotBlank() }
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(firstImg) {
        bmp = firstImg?.let { ImageStore.loadScaled(it, 200) }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(c.tagBg),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            } else {
                Text("✉", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(l.title.ifBlank { "无题" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                l.body.take(60).ifBlank { "（没有正文）" },
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
            )
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                WhoDot(l.author)
                Text(
                    " ${vm.nameOf(l.author)} · ${Dates.cn(l.date)}" + if (l.images.isNotBlank()) " · ${l.images.split(",").count { it.isNotBlank() }} 张图" else "",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/* ── 写一封 ── */
@Composable
fun LetterEditScreen(nav: NavHostController, vm: AppViewModel, letterId: Long) {
    val c = Xm
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var authorIdx by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf(listOf<String>()) }
    var loaded by remember { mutableStateOf(letterId == 0L) }
    var original by remember { mutableStateOf<LetterEntity?>(null) }
    var confirmDel by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(Dates.fmt(Dates.today())) }

    LaunchedEffect(letterId) {
        if (letterId != 0L) {
            vm.daoLetter(letterId)?.let { l ->
                original = l
                title = l.title; body = l.body
                authorIdx = if (l.author == "him") 1 else 0
                images = l.images.split(",").filter { it.isNotBlank() }
                date = l.date
                loaded = true
            }
        }
    }

    val dirty = title.isNotBlank() || body.isNotBlank() || images.isNotEmpty()
    BackHandler(enabled = dirty) {
        /* 写信场景：返回即视为放弃草稿，与原型一致 */
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ImageStore.saveFromUri(ctx, uri, "letters", 760, 0.78f)?.let { images = images + it }
        }
    }

    fun save(onDone: () -> Unit) {
        if (title.isBlank() && body.isBlank() && images.isEmpty()) return
        val author = if (authorIdx == 1) "him" else "her"
        val entity = (original ?: LetterEntity(title = "", body = "", author = author, date = date)).copy(
            title = title.trim().ifBlank { "无题" }, body = body, author = author,
            date = original?.date ?: date, images = images.joinToString(","),
        )
        vm.saveLetter(entity)
        onDone()
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
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(if (letterId == 0L) "写一封" else "编辑", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                "保存", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { save { nav.popBackStack() } }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        XmCard {
            androidx.compose.foundation.text.BasicTextField(
                value = title, onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (title.isEmpty()) Text("给这封信起个标题", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline); inner() }
                },
                singleLine = true,
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("来自", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                Seg(listOf("她", "他"), authorIdx) { authorIdx = it }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(Dates.cn(original?.date ?: date), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        XmCard {
            androidx.compose.foundation.text.BasicTextField(
                value = body, onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().height(210.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)),
                decorationBox = { inner ->
                    Box { if (body.isEmpty()) Text("想说的话写在这里…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
            images.forEachIndexed { i, path ->
                var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(path) { bmp = ImageStore.loadScaled(path, 600) }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (bmp != null) {
                        Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).height(28.dp).clip(RoundedCornerShape(9.dp))
                            .background(Color(0xE6FFFFFF)).clickable { images = images.filterIndexed { j, _ -> j != i } }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("×", fontSize = 14.sp, color = Color(0xFFB85F73)) }
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("＋ 插入图片", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (letterId != 0L) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                            .clickable { confirmDel = true }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        XmCard {
            Text(
                "情书只存在你们两个人的设备里，不会上传。写完可以设为「对方可见」，对方打开就能看到。",
                fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                .clickable { save { nav.popBackStack() } },
            contentAlignment = Alignment.Center,
        ) { Text("保存这封信", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }

    if (confirmDel && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("删除这封情书？") },
            confirmButton = {
                TextButton(onClick = { vm.deleteLetter(original!!.id); nav.popBackStack() }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("取消") } },
        )
    }
}
