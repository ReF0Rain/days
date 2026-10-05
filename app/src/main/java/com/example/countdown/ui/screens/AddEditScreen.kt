package com.example.countdown.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.countdown.R
import com.example.countdown.data.CountdownMode
import com.example.countdown.notification.DailyUpdateScheduler
import com.example.countdown.ui.CountdownViewModelFactory
import com.example.countdown.ui.EventEditViewModel
import com.example.countdown.ui.components.formatFull
import com.example.countdown.ui.components.formatWeek
import com.example.countdown.util.BackgroundImageStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 新建 / 编辑页面。
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

    var title by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var mode by remember { mutableStateOf(CountdownMode.COUNTDOWN) }
    var note by remember { mutableStateOf("") }
    var backgroundUri by remember { mutableStateOf<String?>(null) }
    var backgroundDim by remember { mutableFloatStateOf(0.35f) }
    var originalBackgroundUri by remember { mutableStateOf<String?>(null) }
    var notifyEnabled by remember { mutableStateOf(true) }
    var pinned by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(!isNew) }
    var showDatePicker by remember { mutableStateOf(false) }

    /**
     * 本次编辑里新导入、尚未保存的背景图。
     * 离开页面时如果它既没被保存、也不等于原图，就必须删掉 ——
     * 否则"选图 -> 取消"会不断在内部存储里留下孤儿文件。
     */
    var pendingImageUri by remember { mutableStateOf<String?>(null) }
    /** 是否由用户主动选过日期（用于切模式时决定要不要纠正默认值） */
    var dateTouched by remember { mutableStateOf(false) }

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

    // ---------------- 图片选择（系统照片选择器，不需要存储权限） ----------------
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            // 复制并降采样后存入内部存储（见 BackgroundImageStore）
            val stored = BackgroundImageStore.import(context, picked)
            if (stored != null) {
                // 上一次导入但还没保存的图先删掉，避免反复换图时堆积
                pendingImageUri?.takeIf { it != backgroundUri && it != originalBackgroundUri }
                    ?.let { BackgroundImageStore.deleteIfUnused(context, it) }
                backgroundUri = stored
                pendingImageUri = stored
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.background_import_failed)
                    )
                }
            }
        }
    }

    // ---------------- 离开页面时清理未保存的新图 ----------------
    // 用 rememberUpdatedState 保证回调里读到的永远是最新值（DisposableEffect 的 key 是 Unit，
    // 只注册一次，直接捕获变量会一直是旧值）。
    val currentBackground by rememberUpdatedState(backgroundUri)
    val currentPending by rememberUpdatedState(pendingImageUri)
    val currentOriginal by rememberUpdatedState(originalBackgroundUri)
    val currentSaved by rememberUpdatedState(savedState)
    DisposableEffect(Unit) {
        onDispose {
            val pending = currentPending
            val keep = currentSaved || pending == null ||
                pending == currentBackground || pending == currentOriginal
            if (!keep) {
                BackgroundImageStore.deleteIfUnused(context, pending)
            }
        }
    }

    // ---------------- 读取既有数据 ----------------
    LaunchedEffect(eventId) {
        if (!isNew) {
            viewModel.load(eventId)?.let { event ->
                title = event.title
                targetDate = event.targetLocalDate
                mode = event.mode
                note = event.note.orEmpty()
                backgroundUri = event.backgroundUri
                originalBackgroundUri = event.backgroundUri
                backgroundDim = event.backgroundDim
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

    // 局部函数必须声明在调用点之前（Kotlin 局部函数不提升）
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
            mode = mode,
            note = note,
            backgroundUri = backgroundUri,
            notifyEnabled = notifyEnabled && hasNotificationPermission(context),
            pinned = pinned
        )
        // 换了背景图就删掉旧文件，避免内部存储里堆积无主图片
        if (originalBackgroundUri != null && originalBackgroundUri != backgroundUri) {
            BackgroundImageStore.deleteIfUnused(context, originalBackgroundUri)
        }
        // 这张新图已经被事件引用了，从"待清理"里移除，避免 onDispose 误删
        pendingImageUri = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isNew) R.string.form_title_new else R.string.form_title_edit
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
                // ---------- 计时方式 ----------
                SectionLabel(stringResource(R.string.field_mode))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == CountdownMode.COUNTDOWN,
                        onClick = {
                            // 切模式时若日期还是系统给的默认值，纠正成符合语义的默认值，
                            // 避免"正计日 + 起始日在未来"这种一看就是选反了的组合
                            if (!dateTouched) {
                                targetDate = LocalDate.now().plusDays(1)
                            }
                            mode = CountdownMode.COUNTDOWN
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.mode_countdown))
                    }
                    SegmentedButton(
                        selected = mode == CountdownMode.COUNTUP,
                        onClick = {
                            if (!dateTouched && targetDate.isAfter(LocalDate.now())) {
                                targetDate = LocalDate.now()
                            }
                            mode = CountdownMode.COUNTUP
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.mode_countup))
                    }
                }
                Text(
                    text = stringResource(
                        if (mode == CountdownMode.COUNTDOWN) R.string.mode_countdown_desc
                        else R.string.mode_countup_desc
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                // ---------- 日期与模式矛盾时的提示 ----------
                // 正计日的起始日在未来 -> 永远显示 0 天（数据层用 coerceAtLeast(0) 兜底），
                // 这里明确告诉用户改日期，而不是让它静默显示错误结果
                val today = LocalDate.now()
                val dateWarning = when {
                    mode == CountdownMode.COUNTUP && targetDate.isAfter(today) ->
                        stringResource(R.string.warn_countup_future)
                    mode == CountdownMode.COUNTDOWN && targetDate.isBefore(today) ->
                        stringResource(R.string.warn_countdown_past)
                    else -> null
                }
                if (dateWarning != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = dateWarning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

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
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(stringResource(R.string.field_title_hint)) },
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.error_title_required)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(16.dp))

                // ---------- 日期 ----------
                SectionLabel(
                    stringResource(
                        if (mode == CountdownMode.COUNTDOWN) R.string.field_date
                        else R.string.field_date_countup
                    )
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
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

                // ---------- 背景图 ----------
                SectionLabel(stringResource(R.string.field_background))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (backgroundUri.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.field_background),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.field_background_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = backgroundUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Color.Black.copy(alpha = backgroundDim.coerceIn(0f, 0.85f))
                                        )
                                )
                                Text(
                                    text = "预览",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "文字清晰度",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(Modifier.width(8.dp))
                                Slider(
                                    value = backgroundDim,
                                    onValueChange = { backgroundDim = it },
                                    valueRange = 0f..0.85f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (backgroundUri.isNullOrBlank()) R.string.action_pick_image
                                        else R.string.action_change_image
                                    )
                                )
                            }
                            if (!backgroundUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        BackgroundImageStore.deleteIfUnused(context, backgroundUri)
                                        backgroundUri = null
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_remove_image))
                                }
                            }
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
                        .height(110.dp),
                    shape = RoundedCornerShape(14.dp),
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

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(16.dp),
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
                            // 用户主动选过日期后，切模式就不再覆盖他的选择
                            dateTouched = true
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
        shape = RoundedCornerShape(18.dp),
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
