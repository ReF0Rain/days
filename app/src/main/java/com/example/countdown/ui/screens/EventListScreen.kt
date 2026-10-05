package com.example.countdown.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
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
                    // 置顶事件排最前（仅在默认排序下生效）
                    val ordered = if (state.sortOrder == SortOrder.BY_REMAINING_DAYS) {
                        state.items.sortedByDescending { it.event.pinned }
                    } else {
                        state.items
                    }
                    val hero = state.pinnedItem ?: ordered.firstOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (hero != null) {
                            item(key = "hero") {
                                HeroCountdownCard(
                                    item = hero,
                                    onClick = { onEditClick(hero.event) }
                                )
                            }
                        }

                        item(key = "section") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "全部事件",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${state.items.size} 个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(items = ordered, key = { it.event.id }) { item ->
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
    onClick: () -> Unit
) {
    if (item.event.hasBackground) {
        EventCard(item = item, onEdit = onClick, onDelete = {})
        return
    }

    Box(
        modifier = Modifier
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
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (item.mode == CountdownMode.COUNTUP) "正计日" else "倒计日",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = item.displayDays.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 68.sp,
                        lineHeight = 70.sp
                    ),
                    color = Color.White
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = when (item.state) {
                        CountdownCalculator.DayState.TODAY -> "就是今天"
                        CountdownCalculator.DayState.ELAPSED -> "天"
                        CountdownCalculator.DayState.PAST -> "天前"
                        CountdownCalculator.DayState.FUTURE -> "天后"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.event.targetLocalDate.formatFull(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 14.dp)
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
