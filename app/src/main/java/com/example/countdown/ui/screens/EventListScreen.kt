package com.example.countdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countdown.R
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import com.example.countdown.ui.CountdownItem
import com.example.countdown.ui.CountdownUiState
import com.example.countdown.ui.SortOrder
import com.example.countdown.ui.components.EventCard
import com.example.countdown.ui.components.formatFull
import com.example.countdown.ui.components.formatWeek
import com.example.countdown.ui.theme.CountdownTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    state: CountdownUiState,
    onAddClick: () -> Unit,
    onEditClick: (CountdownEvent) -> Unit,
    onDeleteClick: (CountdownEvent) -> Unit,
    onToggleSort: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<CountdownEvent?>(null) }

    // 顶部主题色渐变：让页面不再是纯平铺底色，有层次感
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.list_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.sortOrder) {
                                SortOrder.BY_REMAINING_DAYS -> stringResource(R.string.sort_by_remaining)
                                SortOrder.BY_CREATED_TIME -> stringResource(R.string.sort_by_created)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleSort) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.action_sort)
                        )
                    }
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                state.isEmpty -> EmptyState(
                    onAddClick = onAddClick,
                    modifier = Modifier.fillMaxSize()
                )

                else -> {
                    // 顶部 Hero 只用于置顶事件。
                    // 没有置顶时不再自动拿第一条来撑场面 —— 那样会白占顶部一大块空间，
                    // 而且那个事件在下面的列表里还会出现一次，显得重复。
                    val hero = state.pinnedItem
                    val listItems = if (hero == null) {
                        state.items
                    } else {
                        state.items.filterNot { it.event.id == hero.event.id }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (hero != null) {
                            HeroCountdownCard(
                                item = hero,
                                onClick = { onEditClick(hero.event) },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "section") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "全部事件",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "${listItems.size} 个",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(items = listItems, key = { it.event.id }) { item ->
                                EventCard(
                                    item = item,
                                    onEdit = { onEditClick(item.event) },
                                    onDelete = { pendingDelete = item.event }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(event)
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * 顶部 Hero 展示位：把最近/置顶的事件放大。
 * 无背景图时用主题色渐变（保证任何情况下都有视觉重点）；
 * 有背景图时直接复用 EventCard，保持形态一致。
 */
@Composable
private fun HeroCountdownCard(
    item: CountdownItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (item.event.hasBackground) {
        // 有背景图时直接复用 EventCard 的图片形态，保持形态一致
        EventCard(item = item, onEdit = onClick, onDelete = {}, modifier = modifier)
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            // 必须可点击：之前重写这块时漏了 clickable，导致点 Hero 没有任何反应
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：徽章 + 模式标签（与列表卡片保持同样的信息结构）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (item.mode == CountdownMode.COUNTUP) {
                            Icons.Outlined.HourglassEmpty
                        } else {
                            Icons.Outlined.Event
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (item.mode == CountdownMode.COUNTUP) "正计日" else "倒计日",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (item.event.pinned) "已置顶" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // 中部：标题
            Text(
                text = item.event.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1
            )

            // 底部：天数 + 日期（结构与列表卡片一致，避免同一事件在 Hero 与列表里长得完全不同）
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.displayDays.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp,
                            lineHeight = 66.sp
                        ),
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (item.state) {
                            CountdownCalculator.DayState.TODAY -> "就是今天"
                            CountdownCalculator.DayState.ELAPSED -> "天"
                            CountdownCalculator.DayState.PAST -> "天前"
                            CountdownCalculator.DayState.FUTURE -> "天后"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (item.mode == CountdownMode.COUNTUP) {
                        "始于 " + item.event.targetLocalDate.formatFull()
                    } else {
                        "目标 " + item.event.targetLocalDate.formatFull() +
                            " " + item.event.targetLocalDate.formatWeek()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.empty_action))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventListScreenPreview() {
    CountdownTheme {
        EventListScreen(
            state = CountdownUiState(
                items = listOf(
                    CountdownItem(
                        event = CountdownEvent(
                            id = 1L,
                            title = "结婚纪念日",
                            targetDate = LocalDate.now().plusDays(128).toEpochDay(),
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
                    CountdownItem(
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
                    )
                ),
                loading = false
            ),
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {},
            onToggleSort = {},
            onRefreshClick = {}
        )
    }
}
