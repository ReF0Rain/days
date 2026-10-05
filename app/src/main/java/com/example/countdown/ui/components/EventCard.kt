package com.example.countdown.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import com.example.countdown.ui.CountdownItem
import com.example.countdown.ui.theme.CountdownTheme
import java.time.LocalDate

/** 有背景图时卡片的固定高度：图片铺满这一块，文字叠在上面 */
private val CARD_IMAGE_HEIGHT = 210.dp

/**
 * 事件卡片。
 *
 * 两种视觉形态：
 *  - 无背景图：Material3 卡片 + 主题色数字
 *  - 有背景图：图片铺满 + 暗化蒙版 + 渐变 + 浅色文字，保证任何图片上都可读
 */
@Composable
fun EventCard(
    item: CountdownItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val event = item.event
    var menuExpanded by remember { mutableStateOf(false) }

    val onImage = event.hasBackground
    val accent = if (onImage) Color.White else stateColor(item)
    val primaryText = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryText =
        if (onImage) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (event.pinned) 6.dp else 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // ---------- 背景图层 ----------
            if (event.hasBackground) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_IMAGE_HEIGHT)
                        .clip(RoundedCornerShape(22.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_IMAGE_HEIGHT)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Color.Black.copy(alpha = event.backgroundDim.coerceIn(0f, 0.85f))
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.50f)
                                )
                            )
                        )
                )
            }

            // ---------- 内容层 ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (event.hasBackground) Modifier.height(CARD_IMAGE_HEIGHT) else Modifier)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryText,
                        modifier = Modifier.weight(1f)
                    )

                    if (event.pinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (onImage) Color.White else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (event.notifyEnabled) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多操作",
                                tint = secondaryText
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (event.hasBackground) 22.dp else 8.dp))

                // 天数主视觉
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.displayDays.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 56.sp,
                            lineHeight = 58.sp
                        ),
                        color = accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = unitLabel(item),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent
                        )
                        if (item.state == CountdownCalculator.DayState.PAST) {
                            Text(
                                text = "已过期",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (onImage) Color.White.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Event,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = event.targetLocalDate.formatFull(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText
                            )
                        }
                        Text(
                            text = event.targetLocalDate.formatWeek(),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText
                        )
                    }
                }

                if (!event.note.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryText
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 正计日不画进度条：语义上"已经开始"，画进度会显得像未完成
                if (item.mode == CountdownMode.COUNTDOWN) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = accent,
                        trackColor = (if (onImage) Color.White else MaterialTheme.colorScheme.outline)
                            .copy(alpha = 0.3f)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when {
                            item.state == CountdownCalculator.DayState.PAST -> "目标日期已过"
                            item.mode == CountdownMode.COUNTUP -> "从这一天开始算起"
                            else -> "距离目标日期"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText
                    )
                    Text(
                        text = if (item.mode == CountdownMode.COUNTUP) "正计日" else "倒计日",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryText
                    )
                }
            }
        }
    }
}

/** 天数后面的单位文案 */
private fun unitLabel(item: CountdownItem): String = when (item.state) {
    CountdownCalculator.DayState.FUTURE -> "天后"
    CountdownCalculator.DayState.TODAY -> "就是今天"
    CountdownCalculator.DayState.PAST -> "天前"
    CountdownCalculator.DayState.ELAPSED -> "天"
}

/** 根据状态给出强调色 */
@Composable
fun stateColor(item: CountdownItem): Color = when (item.state) {
    CountdownCalculator.DayState.PAST -> MaterialTheme.colorScheme.error
    CountdownCalculator.DayState.TODAY -> MaterialTheme.colorScheme.tertiary
    CountdownCalculator.DayState.FUTURE ->
        if (item.displayDays <= 7L) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary
    CountdownCalculator.DayState.ELAPSED -> MaterialTheme.colorScheme.secondary
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F9FC)
@Composable
private fun EventCardPreview() {
    CountdownTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EventCard(
                item = CountdownItem(
                    event = CountdownEvent(
                        id = 1L,
                        title = "结婚纪念日",
                        targetDate = LocalDate.now().plusDays(128).toEpochDay(),
                        note = "准备一份小礼物",
                        pinned = true
                    ),
                    displayDays = 128L,
                    state = CountdownCalculator.DayState.FUTURE,
                    remainingDays = 128L,
                    elapsedDays = 0L,
                    totalDays = 200L,
                    progress = 0.36f,
                    sortValue = 128L
                ),
                onEdit = {},
                onDelete = {}
            )
            EventCard(
                item = CountdownItem(
                    event = CountdownEvent(
                        id = 2L,
                        title = "入职第一天",
                        targetDate = LocalDate.now().minusDays(365).toEpochDay(),
                        mode = CountdownMode.COUNTUP
                    ),
                    displayDays = 365L,
                    state = CountdownCalculator.DayState.ELAPSED,
                    remainingDays = -365L,
                    elapsedDays = 365L,
                    totalDays = -365L,
                    progress = 1f,
                    sortValue = -365L
                ),
                onEdit = {},
                onDelete = {}
            )
        }
    }
}
