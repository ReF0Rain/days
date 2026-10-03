package com.example.countdown.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.countdown.R
import com.example.countdown.data.CountdownEvent
import com.example.countdown.ui.CountdownItem
import com.example.countdown.ui.CountdownUiState
import com.example.countdown.ui.SortOrder
import com.example.countdown.ui.components.EventCard
import com.example.countdown.ui.theme.CountdownTheme

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.list_title),
                            style = MaterialTheme.typography.titleLarge
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.isEmpty -> EmptyState(
                onAddClick = onAddClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 置顶事件排在最前（仅在默认排序下生效）
                val ordered = if (state.sortOrder == SortOrder.BY_REMAINING_DAYS) {
                    state.items.sortedByDescending { it.event.pinned }
                } else {
                    state.items
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
                            targetDate = java.time.LocalDate.now().plusDays(128).toEpochDay(),
                            pinned = true
                        ),
                        remainingDays = 128L,
                        totalDays = 200L,
                        progress = 0.36f
                    ),
                    CountdownItem(
                        event = CountdownEvent(
                            id = 2L,
                            title = "项目交付",
                            targetDate = java.time.LocalDate.now().plusDays(3).toEpochDay()
                        ),
                        remainingDays = 3L,
                        totalDays = 30L,
                        progress = 0.9f
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
