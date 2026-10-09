package com.xiaoman.memo.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.ui.components.PairAvatars
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UsScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showTheme by remember { mutableStateOf(false) }
    var showIdentity by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showSyncConfig by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncStep by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    val sync by vm.syncConfig.collectAsState()

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("无法读取文件")
                    }
                    vm.importJson(text)
                }.onSuccess { r -> toast = "已导入 ${r.total} 条（备忘${r.memos} 清单${r.checklists} 约定${r.plans} 雨过${r.quarrels} 分寸${r.rules} 周期${r.cycles} 情书${r.letters}）" }
                    .onFailure { toast = "导入失败：${it.message}" }
            }
        }
    }

    val todoDone = ui.memos.count { it.isTodo && it.done }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("我们俩", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (ui.profile.anniversary.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) "绑定于 ${Dates.cnFull(ui.profile.anniversary)}" else "纪念日未设置",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp).clickable { showProfile = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PairAvatars(ui.profile.herName, ui.profile.himName, size = 34)
            Column(Modifier.padding(start = 14.dp)) {
                Text("${ui.profile.herName.ifBlank { "她" }} & ${ui.profile.himName.ifBlank { "他" }}", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("共享 1 个小满空间 · 点此编辑", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 15.sp)
        }

        GroupBar("心意")
        EntryRow("♡", "互动", "${ui.actMarks.count { it.played }} 条已玩 · ${ui.actMarks.count { it.fav }} 条收藏", c.goldSoft, c.goldInk) { nav.navigate(R.INTERACT) }
        EntryRow("✉", "情书", if (ui.letters.isEmpty()) "写给对方的话" else "${ui.letters.size} 封 · 最近 ${Dates.cn(ui.letters.first().date)}", c.himSoft, c.himInk) { nav.navigate(R.LETTERS) }
        EntryRow("▣", "相册", if (ui.photos.isEmpty()) "把本地照片放进来" else "${ui.photos.size} 张照片 · 点开可改归属", c.herSoft, c.herInk) { nav.navigate(R.PHOTOS) }

        GroupBar("相处")
        EntryRow("☂", "雨过", quarrelSub(ui), c.qSoft, c.qInk) { nav.navigate(R.QUARRELS) }
        EntryRow("⚖", "分寸", if (ui.rules.isEmpty()) "记下彼此的喜欢与禁忌" else "${ui.rules.size} 条 · 底线 ${ui.rules.count { it.line }} 条", c.tagBg, MaterialTheme.colorScheme.onSurfaceVariant) { nav.navigate(R.RULES) }

        GroupBar("照顾她")
        EntryRow("◍", "周期", if (ui.phaseToday == null) "记录与预测" else "${ui.phaseToday!!.name} · 平均 ${ui.cycleStats?.avgCycle ?: 28} 天", c.calP, c.calPInk) { nav.navigate(R.CYCLE) }

        if (com.xiaoman.memo.BuildConfig.SYNC_ENABLED) {
            GroupBar("同步 · WebDAV 网盘")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
            ) {
                if (!sync.paired) {
                    SetRow("连接网盘（坚果云 / Nextcloud / NAS…）") { showSyncConfig = true }
                } else {
                    SetRow(
                        "已连接 · ${if (sync.role == "host") "发起方" else "加入方"} · " +
                            (if (sync.lastSyncAt > 0) "上次同步 ${Dates.relTime(sync.lastSyncAt)}" else "尚未同步") +
                            (if (sync.lastError.isNotBlank()) " · 上次出错" else ""),
                    ) { showSyncConfig = true }
                    SetRow(if (syncing) "同步中…" else "立即同步") {
                        if (!syncing) {
                            syncing = true
                            vm.syncNow(onStep = { syncStep = it }) { r ->
                                toast = r.message
                                syncing = false
                            }
                        }
                    }
                    if (syncing && syncStep.isNotBlank()) {
                        Text(
                            syncStep,
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (sync.lastError.isNotBlank()) {
                        Text(
                            "上次同步出错：" + sync.lastError,
                            fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    SetRow("解除配对") {
                        scope.launch { vm.setSyncConfig(sync.copy(paired = false, lastSyncAt = 0, lastError = "")) }
                        toast = "已解除配对（本机数据不受影响）"
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("${ui.memos.size}", "条备忘", Modifier.weight(1f))
            StatBox("$todoDone", "件已完成", Modifier.weight(1f))
            StatBox("${ui.checklists.size}", "个清单", Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
        ) {
            SetRow("使用说明") { nav.navigate(R.USAGE) }
            SetRow("外观（浅色 / 深色 / 跟随系统）") { showTheme = true }
            SetRow("我的身份（谁在用这台手机）") { showIdentity = true }
            SetRow("数据导出（JSON）") {
                scope.launch {
                    runCatching {
                        val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
                        if (!dir.exists()) dir.mkdirs()
                        val f = File(dir, "xiaoman_backup_${System.currentTimeMillis()}.json")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            f.writeText(vm.exportJson())
                        }
                        toast = "已导出到 ${f.absolutePath}"
                    }.onFailure { toast = "导出失败：${it.message}" }
                }
            }
            SetRow("数据导入（选择备份 JSON）") { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
            SetRow("清空全部数据") { confirmClear = true }
        }

        if (toast.isNotEmpty()) {
            Text(toast, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }

    if (showTheme) {
        var sel by remember { mutableStateOf(ui.profile.theme) }
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("外观") },
            text = {
                Column {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (k, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = k }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (sel == k) Text("✓", color = c.accentDeep)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setTheme(sel); showTheme = false }) { Text("确定", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showTheme = false }) { Text("取消") } },
        )
    }

    if (showIdentity) {
        var sel by remember { mutableStateOf(ui.profile.identity) }
        AlertDialog(
            onDismissRequest = { showIdentity = false },
            title = { Text("我的身份") },
            text = {
                Column {
                    listOf("her" to "我是 ${ui.profile.herName}（她）", "him" to "我是 ${ui.profile.himName}（他）").forEach { (k, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = k }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (sel == k) Text("✓", color = c.accentDeep)
                        }
                    }
                    Text("新记的备忘、清单会默认归属这个身份。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setIdentity(sel); showIdentity = false }) { Text("确定", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showIdentity = false }) { Text("取消") } },
        )
    }

    if (showProfile) {
        var her by remember { mutableStateOf(ui.profile.herName) }
        var him by remember { mutableStateOf(ui.profile.himName) }
        var anniv by remember { mutableStateOf(ui.profile.anniversary) }
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("我们俩") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(her, { her = it }, label = { Text("她的名字") }, singleLine = true)
                    OutlinedTextField(him, { him = it }, label = { Text("他的名字") }, singleLine = true)
                    OutlinedTextField(anniv, { anniv = it }, label = { Text("在一起的日子（yyyy-MM-dd）") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNames(her, him)
                    if (anniv.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) vm.setAnniversary(anniv)
                    showProfile = false
                }) { Text("保存", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("取消") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部数据？") },
            text = {
                Text(
                    "所有备忘、清单、约定、雨过、分寸、周期、情书与相册记录将被永久删除（含图片文件），且不可恢复。\n\n建议先「数据导出」留一份备份。",
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        vm.clearAllData { }
                        toast = "已清空全部数据"
                    }
                }) { Text("确认清空", color = c.accentDeep) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    if (showSyncConfig) {
        var preset by remember { mutableStateOf(if (sync.server.contains("jianguoyun")) "坚果云" else if (sync.user.isBlank()) "坚果云" else "自定义") }
        var server by remember { mutableStateOf(sync.server) }
        var user by remember { mutableStateOf(sync.user) }
        var pass by remember { mutableStateOf(sync.pass) }
        // 配对码只生成一次；文件夹默认值必须由同一个码派生，
        // 否则两次随机调用会让「配对码」和「文件夹后缀」对不上 → 双端永远不同目录
        var code by remember { mutableStateOf(sync.code.ifBlank { vm.generatePairCode() }) }
        var role by remember { mutableStateOf(sync.role) }
        var folder by remember { mutableStateOf(sync.folder.ifBlank { "/xiaoman-" + code }) }
        var trustAll by remember { mutableStateOf(sync.trustAll) }
        var testMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSyncConfig = false },
            title = { Text("连接 WebDAV 网盘") },
            text = {
                // 内容超过一屏（弹窗 + 键盘会把说明文本顶没），必须可滚动
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("选择服务商", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.xiaoman.memo.ui.components.ChipRow {
                        listOf(
                            Triple("坚果云", "https://dav.jianguoyun.com/dav/", false),
                            Triple("Nextcloud", "https://你的域名/remote.php/dav/files/用户名/", false),
                            Triple("NAS / 自建", "https://NAS地址:端口/", true),
                        ).forEach { (name, url, selfSigned) ->
                            XmChip(name, on = preset == name) {
                                preset = name; server = url; trustAll = selfSigned
                            }
                        }
                    }
                    OutlinedTextField(server, { server = it; preset = "自定义" }, label = { Text("WebDAV 服务器地址") }, singleLine = true)
                    OutlinedTextField(
                        user, { user = it },
                        label = { Text(if (preset == "坚果云") "坚果云账号（邮箱/手机号）" else "WebDAV 用户名") }, singleLine = true,
                    )
                    OutlinedTextField(
                        pass, { pass = it },
                        label = { Text(if (preset == "坚果云") "应用密码（网页版生成，非登录密码）" else "密码 / 应用密码") },
                        singleLine = true,
                        supportingText = if (preset == "坚果云") {
                            { Text("坚果云：网页版登录 → 账户信息 → 安全选项 → 添加应用，生成后把整串密码粘到这里") }
                        } else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("我的角色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.xiaoman.memo.ui.components.Seg(listOf("发起方", "加入方"), if (role == "host") 0 else 1) { role = if (it == 0) "host" else "guest" }
                    }
                    OutlinedTextField(
                        code, { input ->
                            val filtered = input.filter { ch -> ch.isDigit() }.take(6)
                            val prev = code
                            code = filtered
                            // 加入方输入对方配对码时，若文件夹还是自动生成的（或为空），让它跟随新码，
                            // 避免双方指向不同目录
                            if (folder.isBlank() || folder == "/xiaoman-$prev") folder = "/xiaoman-$filtered"
                        },
                        label = { Text(if (role == "host") "配对码（自动生成，告诉对方）" else "输入对方的配对码") }, singleLine = true,
                    )
                    OutlinedTextField(folder, { folder = it }, label = { Text("同步文件夹（双方须指向同一共享目录）") }, singleLine = true)
                    if (preset == "NAS / 自建" || trustAll) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Switch(
                                checked = trustAll,
                                onCheckedChange = { trustAll = it },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                "忽略自签名证书（家用 NAS 常用；仅私有网络使用）",
                                fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (testMsg.isNotBlank()) {
                        Text(
                            testMsg,
                            fontSize = 12.sp, lineHeight = 17.sp,
                            color = if (testMsg.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                            toast = "请先填写服务器地址、账号和密码"
                            return@TextButton
                        }
                        val cfg = com.xiaoman.memo.data.SyncConfig(
                            paired = true, server = server.trim(), user = user.trim(), pass = pass.trim(),
                            folder = folder.trim(), role = role, code = code, trustAll = trustAll,
                        )
                        testMsg = "测试中…"
                        vm.testConnection(cfg, onStep = { testMsg = it }) { r -> testMsg = r }
                    }) { Text("测试连接") }
                    TextButton(onClick = {
                        if (user.isNotBlank() && pass.isNotBlank() && code.length == 6 && folder.isNotBlank() && server.isNotBlank()) {
                            scope.launch {
                                vm.setSyncConfig(
                                    com.xiaoman.memo.data.SyncConfig(
                                        paired = true, server = server.trim(), user = user.trim(), pass = pass.trim(),
                                        folder = folder.trim(), role = role, code = code, trustAll = trustAll,
                                        lastSyncAt = sync.lastSyncAt,
                                    ),
                                )
                                // 保存后立刻试同步一次，成功/失败当场给出反馈
                                toast = "连接中…"
                                vm.syncNow(onStep = { toast = it }) { r -> toast = r.message }
                            }
                            showSyncConfig = false
                        } else {
                            toast = "请补全服务器、账号、密码、6 位配对码和文件夹"
                        }
                    }) { Text("保存并连接", color = c.accentDeep) }
                }
            },
            dismissButton = { TextButton(onClick = { showSyncConfig = false }) { Text("取消") } },
        )
    }
}

private fun quarrelSub(ui: com.xiaoman.memo.data.AppUiState): String {
    if (ui.quarrels.isEmpty()) return "吵过的事，别白吵"
    val resolved = ui.quarrels.count { it.resolved }
    val latest = Dates.cn(ui.quarrels.first().date)
    return "${ui.quarrels.size} 次 · 最近一次 $latest · ${if (resolved == ui.quarrels.size) "都已翻篇" else "$resolved 次已翻篇"}"
}

@Composable
private fun GroupBar(text: String) {
    Text(
        text,
        fontSize = 11.sp, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )
}

@Composable
private fun EntryRow(icon: String, title: String, sub: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), ambientColor = androidx.compose.ui.graphics.Color(0x33C9B8AE), spotColor = androidx.compose.ui.graphics.Color(0x33C9B8AE))
            .fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp, 16.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 16.sp, color = fg)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 15.sp)
    }
}

@Composable
private fun StatBox(num: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), ambientColor = androidx.compose.ui.graphics.Color(0x33C9B8AE), spotColor = androidx.compose.ui.graphics.Color(0x33C9B8AE))
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(num, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Xm.accent)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SetRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("›", color = MaterialTheme.colorScheme.outline)
    }
}
