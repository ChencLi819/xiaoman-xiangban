package com.xiaoman.memo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoman.memo.ui.theme.Xm

/* ── 卡片 ── */
@Composable
fun XmCard(
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.surface,
    radius: Int = 24,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(radius.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.dp))
            .background(bg)
            .padding(16.dp),
        content = content,
    )
}

/* ── 区块标题行：accent 小色条 + 标题 + 尾注 ── */
@Composable
fun SectionHead(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Xm.accent),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(enabled = onTrailing != null) { onTrailing?.invoke() },
            )
        }
    }
}

/* ── 按背景亮度选字色：暗色模式下强调色变浅，固定白字会看不清（体验反馈），
      统一用这个函数决定落在彩色背景上的文字用白还是深墨 ── */
fun inkOn(bg: Color): Color =
    if (bg.luminance() > 0.5f) Color(0xFF3A2E33) else Color.White

/* ── 胶囊 chip（仅用于筛选/单选：有选中态，点了改变选中状态）── */
@Composable
fun XmChip(
    text: String,
    on: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Xm
    val bg = when {
        on && accent != null -> accent
        on -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = if (on) inkOn(bg) else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (on && accent != null) accent else MaterialTheme.colorScheme.outlineVariant
    val weight = if (on) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.sp, color = fg, fontWeight = weight)
    }
}

/* ── 动作 chip（新建/导入/跳转入口：实底强调色，视觉上是"按钮"而非"选项"，
      与筛选 chip 明确二分——点了发生一件事，而不是改变选中状态）── */
@Composable
fun XmActionChip(
    text: String,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val c = Xm
    val bg = accent ?: c.accent
    Box(
        Modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(50), ambientColor = bg.copy(alpha = 0.33f), spotColor = bg.copy(alpha = 0.33f))
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text, fontSize = 12.sp, color = inkOn(bg),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}

/* ── 待办勾选框 ── */
@Composable
fun Tick(on: Boolean, size: Int = 22, onClick: () -> Unit = {}) {
    val c = Xm
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (on) c.ok else MaterialTheme.colorScheme.surface)
            .border(
                1.6.dp,
                if (on) c.ok else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(7.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (on) Text("✓", color = inkOn(c.ok), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/* ── 归属圆点 ── */
@Composable
fun WhoDot(author: String) {
    val c = Xm
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (author == "her") c.her else c.him),
    )
}

/* ── 标签小片 ── */
@Composable
fun TagChip(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        /* maxLines=1 + softWrap=false：横排空间不够时宁可截断，也不要逐字换行把整行撑高 */
        Text(text, fontSize = 11.sp, color = fg, maxLines = 1, softWrap = false)
    }
}

/* ── 32dp 图标动作按钮（32×32 · 圆角 8dp · 浅底）──
   用在卡片右上角/末尾的 ✎ / ✕。
   ⚠️ 它是**固定宽度**元素。放进 Row 时必须是「不带 weight」的子项，这样它会先拿到
   自己的宽度，不会被 Compose 压窄到 0 宽（那是末尾放不定宽文字才会出的故障）。
   纯文字图标在白色卡片上会"飘"，所以给一层浅底让"可以点"看得见。 */
@Composable
fun IconAction(sym: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(32.dp)
            .width(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(sym, fontSize = 15.sp, color = fg, maxLines = 1, softWrap = false)
    }
}

/* ── 开关行 ── */
@Composable
fun SwitchRow(label: String, sub: String? = null, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) {
                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Xm.accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = Xm.line2,
            ),
            modifier = Modifier.height(26.dp),
        )
    }
}

/* ── 详细内容控件：默认收起（一行开关），点击展开多行输入，可再收起 ──
   所有随手记标签通用的「名称 + 详细内容」分层中的详细内容编辑器。
   ⚠️ onClose 必须显式回传：open 由父级持有，早期版本收起只 onChange 而不关，
   导致「收起 ▴」点了没反应（控件永远展开）。 */
@Composable
fun NoteToggleField(
    open: Boolean,
    value: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onChange: (String) -> Unit,
) {
    if (!open) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable { onOpen() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (value.isBlank()) "＋ 添加详细内容" else "详细内容 · ${value.length} 字（点开查看/修改）",
                fontSize = 13.sp,
                color = if (value.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.weight(1f))
            Text("▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("详细内容", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "收起 ▴", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onChange(value.trim()); onClose() },
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(11.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box { if (value.isEmpty()) Text("想写多详细都可以…", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline); inner() }
                },
            )
        }
    }
}

/* ── 分段控件（共同/我的/TA 的；她/他）── */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Xm
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.tagBg)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    o, fontSize = 12.sp,
                    color = if (on) c.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                )
            }
        }
    }
}

/* ── 双头像 ── */
/* ── 双头像（昵称未设置时回退为 她/他，避免空字符串取首字崩溃）── */
@Composable
fun PairAvatars(a: String, b: String, size: Int = 32) {
    val c = Xm
    val ta = a.ifBlank { "她" }
    val tb = b.ifBlank { "他" }
    Box(Modifier.width((size + 24).dp).height(size.dp)) {
        Avatar(ta.first().toString(), c.her, Modifier.align(Alignment.CenterStart), size)
        Avatar(tb.first().toString(), c.him, Modifier.align(Alignment.CenterEnd).padding(start = 24.dp), size)
    }
}

@Composable
fun Avatar(t: String, bg: Color, modifier: Modifier = Modifier, size: Int = 32) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(t, color = inkOn(bg), fontSize = (size * 0.42f).sp)
    }
}

/* ── 空状态 ── */
@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", fontSize = 16.sp, color = MaterialTheme.colorScheme.outlineVariant, textAlign = TextAlign.Center)
        Text(
            text,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/* ── 月份条 ── */
@Composable
fun MonthBar(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/* ── 翻卡：trigger 变化时 3D 翻转一次 ── */
@Composable
fun FlipCard(
    trigger: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            anim.animateTo(88f, tween(200))
            anim.animateTo(0f, tween(260))
        }
    }
    Box(
        modifier.graphicsLayer {
            rotationY = anim.value
            cameraDistance = 14 * density
        },
    ) { content() }
}

/* ── 横向 chips 行（放不下时横向滚动，绝不竖排换行）── */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/* ── 渐变卡（阶段卡 / 互动卡 / 抽签卡用）── */
@Composable
fun GradientCard(colors: List<Color>, border: Color? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x33C9B8AE), spotColor = Color(0x33C9B8AE))
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .let { if (border != null) it.border(1.5.dp, border, RoundedCornerShape(24.dp)) else it }
            .padding(16.dp),
        content = content,
    )
}
