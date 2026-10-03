package com.example.countdown.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.countdown.R
import com.example.countdown.notification.DailyUpdateScheduler
import com.example.countdown.ui.CountdownViewModelFactory
import com.example.countdown.ui.EventEditViewModel
import com.example.countdown.ui.components.formatFull
import com.example.countdown.ui.components.formatWeek
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 新建 / 编辑页面。
 *
 * [eventId] 为 0L 表示新建，否则编辑对应记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    eventId: Long,
    factory: CountdownViewModelFactory,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: EventEditViewModel = viewModel(factory = factory)

    val isNew = eventId == 0L

    // ---------------- 表单状态 ----------------
    var title by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var note by remember { mutableStateOf("") }
    var notifyEnabled by remember { mutableStateOf(true) }
    var pinned by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(!isNew) }
    var showDatePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val savedState by viewModel.saved.collectAsStateWithLifecycle()

    // ---------------- Android 13 通知权限 ----------------
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notifyEnabled = true
            DailyUpdateScheduler.schedule(context)
            DailyUpdateScheduler.runNow(context)
        } else {
            notifyEnabled = false
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.permission_denied_toast))
            }
        }
    }

    // ---------------- 读取既有数据 ----------------
    LaunchedEffect(eventId) {
        if (!isNew) {
            viewModel.load(eventId)?.let { event ->
                title = event.title
                targetDate = event.targetLocalDate
                note = event.note.orEmpty()
                notifyEnabled = event.notifyEnabled
                pinned = event.pinned
            }
            loading = false
        }
    }

    // ---------------- 保存成功后的收尾 ----------------
    LaunchedEffect(savedState) {
        if (savedState) {
            DailyUpdateScheduler.runNow(context)
            snackbarHostState.showSnackbar(context.getString(R.string.saved_toast))
            viewModel.consumeSaved()
            onFinished()
        }
    }

    // ---------------- 校验 + 保存（局部函数必须声明在调用点之前） ----------------
    fun submit() {
        if (title.isBlank()) {
            titleError = true
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_title_required))
            }
            return
        }
        viewModel.save(
            id = eventId,
            title = title,
            targetDate = targetDate,
            note = note,
            notifyEnabled = notifyEnabled && hasNotificationPermission(context),
            pinned = pinned
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isNew) R.string.form_title_new else R.string.form_title_edit
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { submit() }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.action_save)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ---------- 事件名称 ----------
                SectionLabel(stringResource(R.string.field_title))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = titleError,
                    placeholder = { Text(stringResource(R.string.field_title_hint)) },
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.error_title_required)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(20.dp))

                // ---------- 目标日期 ----------
                SectionLabel(stringResource(R.string.field_date))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    onClick = { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = targetDate.formatFull(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = targetDate.formatWeek(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(R.string.pick_date))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ---------- 备注 ----------
                SectionLabel(stringResource(R.string.field_note))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text(stringResource(R.string.field_note_hint)) },
                    maxLines = 4
                )

                Spacer(Modifier.height(20.dp))

                // ---------- 每日通知 ----------
                SwitchRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.field_notify),
                    desc = stringResource(R.string.field_notify_desc),
                    checked = notifyEnabled && hasNotificationPermission(context),
                    onCheckedChange = { checked ->
                        if (checked && needsNotificationPermission(context)) {
                            // Android 13+ 首次开启需要运行时权限
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifyEnabled = checked
                            DailyUpdateScheduler.runNow(context)
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // ---------- 置顶 ----------
                SwitchRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    title = stringResource(R.string.field_pinned),
                    desc = stringResource(R.string.field_pinned_desc),
                    checked = pinned,
                    onCheckedChange = { pinned = it }
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { submit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_save))
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ---------------- 日期选择对话框 ----------------
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            targetDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// ---------------------------------------------------------------------------
// 通知权限小工具
// ---------------------------------------------------------------------------

internal fun hasNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

internal fun needsNotificationPermission(context: Context): Boolean =
    !hasNotificationPermission(context)
