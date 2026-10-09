package com.xiaoman.memo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoman.memo.data.MemoEntity
import com.xiaoman.memo.ui.theme.Xm
import com.xiaoman.memo.util.Dates

/* 内置标签固定色；自定义标签按名字哈希从柔和色板轮转 */
@Composable
fun tagColors(name: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val c = Xm
    return when (name) {
        "生活" -> c.ok to c.okSoft
        "想法" -> c.goldInk to c.goldSoft
        "约定" -> c.himInk to c.himSoft
        "待办" -> c.accentDeep to c.accentSoft
        "想买" -> MaterialTheme.colorScheme.onSurfaceVariant to c.tagBg
        else -> {
            val palette = listOf(
                c.himInk to c.himSoft,
                c.goldInk to c.goldSoft,
                c.herInk to c.herSoft,
                c.ok to c.okSoft,
                c.qInk to c.qSoft,
            )
            palette[name.hashCode().mod(palette.size)]
        }
    }
}

@Composable
fun MemoCard(m: MemoEntity, onClick: () -> Unit) {
    val c = Xm
    val (tagFg, tagBg) = tagColors(m.tag)
    val isEvent = m.kind == "event"
    Row(
        Modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), ambientColor = androidx.compose.ui.graphics.Color(0x33C9B8AE), spotColor = androidx.compose.ui.graphics.Color(0x33C9B8AE))
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp, 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // 双色归属色条；事件用金色区分
        Box(
            Modifier
                .width(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isEvent) c.gold else if (m.author == "her") c.her else c.him),
        )
        Box(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (isEvent) {
                    Text("📍", fontSize = 13.sp, modifier = Modifier.padding(end = 5.dp))
                }
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isEvent) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                    color = if (m.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (m.done) TextDecoration.LineThrough else null,
                )
            }
            if (isEvent && m.eventDate.isNotBlank()) {
                Text(
                    Dates.cn(m.eventDate),
                    fontSize = 11.sp,
                    color = c.goldInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            /* 详细内容（note）不上列表：点名称后在详情层查看，保持卡片轻 */
            if (!isEvent) {
                Row(Modifier.padding(top = 7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TagChip(m.tag, tagFg, tagBg)
                    if (m.privateOnly) {
                        TagChip("仅自己", MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
                        Box(Modifier.padding(start = 8.dp))
                    }
                    Text(
                        " · " + Dates.relTime(m.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Row(Modifier.padding(top = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TagChip("事件", c.goldInk, c.goldSoft)
                    if (m.privateOnly) {
                        TagChip("仅自己", MaterialTheme.colorScheme.onSurfaceVariant, c.tagBg)
                    }
                    Text(
                        " · " + Dates.relTime(m.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/* 「TA 加的」归属行 */
@Composable
fun WhoAdded(author: String, name: String, done: Boolean) {
    val c = Xm
    Row(
        Modifier.padding(top = 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        WhoDot(author)
        Text(
            " $name 加的",
            fontSize = 11.sp,
            color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
