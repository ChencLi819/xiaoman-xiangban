package com.xiaoman.memo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.PhotoEntity
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.MonthBar
import com.xiaoman.memo.ui.components.Seg
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.ImageStore
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    val ctx = LocalContext.current
    var who by remember { mutableStateOf("all") }
    var lightbox by remember { mutableStateOf<PhotoEntity?>(null) }
    var confirmDel by remember { mutableStateOf<Long?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)) { uris ->
        if (uris.isNotEmpty()) {
            val entities = uris.mapNotNull { u ->
                ImageStore.saveFromUri(ctx, u, "photos", 800, 0.75f)?.let { path ->
                    PhotoEntity(path = path, ts = System.currentTimeMillis(), author = "both", note = "")
                }
            }
            if (entities.isNotEmpty()) vm.addPhotos(entities)
        }
    }

    val list = ui.photos.filter { who == "all" || it.author == who }

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
                Text("相册", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (list.isEmpty()) "还没有照片" else "共 ${list.size} 张 · 点开可改归属与备注",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
            com.xiaoman.memo.ui.components.XmActionChip("＋ 导入") {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        com.xiaoman.memo.ui.components.ChipRow {
            listOf("all" to "全部", "her" to "她", "him" to "他", "both" to "共同").forEach { (k, label) ->
                XmChip(label, on = who == k) { who = k }
            }
        }

        if (list.isEmpty()) {
            EmptyView("还没有照片")
        } else {
            val groups = list.groupBy { p ->
                val d = Instant.ofEpochMilli(p.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                "${d.year}年${d.monthValue}月"
            }.toSortedMap(compareByDescending { it })
            groups.forEach { (month, ps) ->
                MonthBar(month)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(((ps.size + 2) / 3 * 130).dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    userScrollEnabled = false,
                ) {
                    items(ps, key = { it.id }) { p ->
                        var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(p.path) { bmp = ImageStore.loadScaled(p.path, 240) }
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(c.tagBg)
                                .clickable { lightbox = p },
                        ) {
                            if (bmp != null) {
                                Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            val d = Instant.ofEpochMilli(p.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(
                                "${d.monthValue}.${d.dayOfMonth}",
                                fontSize = 9.sp, color = Color.White, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Color(0x8C000000)).padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    lightbox?.let { p ->
        Dialog(
            onDismissRequest = { lightbox = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            var author by remember { mutableStateOf(p.author) }
            var note by remember { mutableStateOf(p.note) }
            var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(p.path) { bmp = ImageStore.loadScaled(p.path, 1080) }
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val d = Instant.ofEpochMilli(p.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("${d.year}年${d.monthValue}月${d.dayOfMonth}日", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x2BFFFFFF))
                            .clickable { lightbox = null }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("×", fontSize = 14.sp, color = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (bmp != null) {
                        Image(bmp!!.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("her" to "她", "him" to "他", "both" to "共同").forEachIndexed { i, (k, label) ->
                        SegmentedButton(
                            selected = author == k,
                            onClick = { author = k; vm.setPhotoAuthor(p.id, k) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color(0x2BFFFFFF),
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color(0x14FFFFFF),
                                inactiveContentColor = Color.White,
                            ),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = note, onValueChange = { note = it; vm.setPhotoNote(p.id, it) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).padding(11.dp, 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 13.sp),
                    decorationBox = { inner ->
                        Box { if (note.isEmpty()) Text("给这张照片写一句话", fontSize = 13.sp, color = Color(0x99FFFFFF)); inner() }
                    },
                    singleLine = true,
                )
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF))
                        .clickable { confirmDel = p.id }.padding(0.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("删除这张", fontSize = 13.sp, color = Color.White) }
            }
        }
    }

    confirmDel?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDel = null; lightbox = null },
            title = { Text("删除这张照片？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePhoto(id)
                    confirmDel = null
                    lightbox = null
                }) { Text("删除", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("取消") } },
        )
    }
}
