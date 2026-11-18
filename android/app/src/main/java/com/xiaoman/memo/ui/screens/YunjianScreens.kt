package com.xiaoman.memo.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.data.DraftKey
import com.xiaoman.memo.data.MailEntity
import com.xiaoman.memo.ui.components.AutoDraft
import com.xiaoman.memo.ui.components.ChipRow
import com.xiaoman.memo.ui.components.EmptyView
import com.xiaoman.memo.ui.components.MonthBar
import com.xiaoman.memo.ui.components.Seg
import com.xiaoman.memo.ui.components.WhoDot
import com.xiaoman.memo.ui.components.XmActionChip
import com.xiaoman.memo.ui.components.XmCard
import com.xiaoman.memo.ui.components.XmChip
import com.xiaoman.memo.ui.components.rememberDraft
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.theme.T
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates
import org.json.JSONObject

/* ── 云笺 ──
   两个人的信匣：想对方了就写一封寄过去，对方在「收到的」里拆开。
   只增不删：界面没有删除入口，数据层也没有 deleteMail（详见 MailEntity 注释）。
   「收到的 / 我寄的」分成两个分区；月份筛选按分区各自独立（切分区即清空），
   关键词搜索跨分区保留 —— 方便用同一个词把两边各查一遍。 */

private fun MailEntity.day(): String = Dates.dateOf(sentAt)
private fun MailEntity.month(): String = day().substring(0, 7)
private fun monthCn(ym: String): String = ym.replace("-", "年") + "月"

@Composable
fun MailScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val me = ui.profile.identity
    var seg by remember { mutableIntStateOf(0) }   // 0 = 收到的 / 1 = 我寄的
    var q by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }   // "" = 不限月份

    /* 两个分区各自独立的筛选状态：切分区时把月份筛选清掉，避免「收到的按 8 月筛过，切到我寄的却空着」 */
    LaunchedEffect(seg) { month = "" }

    val box = remember(ui.mails, me, seg) {
        ui.mails.filter { if (seg == 0) it.author != me else it.author == me }
    }
    /* 关键词先过滤，月份 chip 只列「搜出来的信」里真实存在的月份，避免点进去是空的 */
    val scoped = box.filter { q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) }
    val months = scoped.map { it.month() }.distinct().sortedDescending()
    val list = scoped.filter { month.isBlank() || it.month() == month }
    val unread = box.count { it.readAt == 0L && it.author != me }

    /* 改关键词后如果原月份已不在结果里，自动退回「全部」 */
    LaunchedEffect(q) { if (month.isNotBlank() && month !in months) month = "" }

    /* 空态占位高度：按屏高比例取，各种尺寸下都落在页面中部 */
    val emptyH = (LocalConfiguration.current.screenHeightDp * 0.58f).coerceAtLeast(340f).dp

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
                Text("云笺", style = T.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    when {
                        ui.mails.isEmpty() -> "还没有信"
                        seg == 0 && unread > 0 -> "有 $unread 封还没拆"
                        seg == 0 -> "收到的 ${box.size} 封"
                        else -> "寄出的 ${box.size} 封"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            XmActionChip("＋ 写一封") { nav.navigate(R.MAIL_EDIT) }
        }

        Seg(listOf("收到的", "我寄的"), seg) { seg = it }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(12.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 9.dp))
            BasicTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) Text("搜标题或信里的字", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
                singleLine = true,
            )
        }

        if (months.size > 1 || month.isNotBlank()) {
            ChipRow {
                XmChip("全部", on = month.isBlank()) { month = "" }
                months.forEach { ym ->
                    XmChip(monthCn(ym), on = month == ym) { month = if (month == ym) "" else ym }
                }
            }
        }

        when {
            /* 整页为空时按屏高留白放空态，避免紧贴搜索框、下方一大片空 */
            box.isEmpty() && seg == 0 ->
                Box(Modifier.fillMaxWidth().height(emptyH), contentAlignment = Alignment.Center) { EmptyView("还没有收到信") }
            box.isEmpty() ->
                Box(Modifier.fillMaxWidth().height(emptyH), contentAlignment = Alignment.Center) { EmptyView("还没有寄出过信") }
            list.isEmpty() -> EmptyView("没有匹配的信")
            else -> {
                /* 按「年-月」分组：跨年使用时会自动出现两个不同的月份头，不会混在一起 */
                val groups = list.groupBy { it.month() }.toSortedMap(compareByDescending { it })
                groups.forEach { (ym, ms) ->
                    MonthBar(monthCn(ym))
                    ms.forEach { m ->
                        MailCard(m, vm, mine = seg == 1) {
                            nav.navigate(R.MAIL_READ.replace("{mailId}", m.id.toString()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MailCard(m: MailEntity, vm: AppViewModel, mine: Boolean, onClick: () -> Unit) {
    val c = Xm
    val fresh = !mine && m.readAt == 0L
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /* 未读标记：一条 3dp 竖线，比红点安静 */
        Box(
            Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp))
                .background(if (fresh) c.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.title.ifBlank { "（无题）" },
                    fontSize = 15.sp,
                    fontWeight = if (fresh) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Dates.timeOf(m.sentAt),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                m.body.replace("\n", " ").ifBlank { "（空白）" },
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                WhoDot(m.author)
                Text(
                    " ${vm.nameOf(m.author)} · ${Dates.cnY(m.day())}" +
                        if (mine) (if (m.readAt > 0) " · 已拆" else " · 未拆") else "",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/* ── 拆信 ── */
@Composable
fun MailReadScreen(nav: NavHostController, vm: AppViewModel, mailId: Long) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    val m = ui.mails.find { it.id == mailId }

    /* 对方寄来的信，打开即视为拆过（本机自己写的信不改 readAt） */
    LaunchedEffect(mailId, m?.readAt, m?.author) {
        if (m != null && m.author != ui.profile.identity && m.readAt == 0L) vm.markMailRead(m.id)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "云笺", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(46.dp))
        }

        if (m == null) {
            EmptyView("这封信不在了")
        } else {
            XmCard {
                Text(
                    m.title.ifBlank { "（无题）" },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WhoDot(m.author)
                    Text(
                        " ${vm.nameOf(m.author)} · ${Dates.stampCn(m.sentAt)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            XmCard {
                Text(
                    m.body.ifBlank { "（只有标题）" },
                    fontSize = 14.sp, lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                )
            }

            if (m.author != ui.profile.identity) {
                Text(
                    "写回一封",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { nav.navigate(R.MAIL_EDIT) }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

/* ── 写一封 ── */
@Composable
fun MailEditScreen(nav: NavHostController, vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val c = Xm
    val me = ui.profile.identity
    val to = if (me == "her") ui.profile.himName.ifBlank { "他" } else ui.profile.herName.ifBlank { "她" }

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val canSend = title.isNotBlank() || body.isNotBlank()

    val draft = rememberDraft(vm, DraftKey.MAIL, 0L)
    var resumed by rememberSaveable { mutableStateOf(false) }

    /* 进页面时续上没写完的云笺 */
    LaunchedEffect(Unit) {
        if (resumed) return@LaunchedEffect
        resumed = true
        draft.read()?.let { o ->
            title = o.optString("title"); body = o.optString("body")
        }
    }

    AutoDraft(draft, canSend) {
        if (!canSend) null else JSONObject().apply { put("title", title); put("body", body) }
    }

    /* 返回 = 存草稿走人：写一半的信留在本机，下次进来接着写；绝不替用户寄出。 */
    BackHandler { nav.popBackStack() }

    fun send() {
        if (!canSend) return
        /* 先清草稿再退出：否则页面离开组合时的兜底写入会把刚寄出的信又存回草稿 */
        vm.sendMail(title, body) { draft.finish(); nav.popBackStack() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.popBackStack() }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "写一封", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Text(
                "寄出", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (canSend) c.accent else MaterialTheme.colorScheme.outline,
                modifier = Modifier.clickable { send() }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        XmCard {
            BasicTextField(
                value = title, onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) Text("给这封信起个标题", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
                singleLine = true,
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("寄给", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
                Text(to, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                Box(Modifier.weight(1f))
                Text(Dates.stampCn(System.currentTimeMillis(), withYear = false), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        XmCard {
            BasicTextField(
                value = body, onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().height(240.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)),
                decorationBox = { inner ->
                    Box {
                        if (body.isEmpty()) Text("想说的话写在这里…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
            )
        }

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
                .background(if (canSend) c.accent else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { send() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "寄出这封信",
                color = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
