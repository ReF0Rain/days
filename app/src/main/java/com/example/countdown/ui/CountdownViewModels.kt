package com.example.countdown.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import com.example.countdown.data.CountdownRepository
import com.example.countdown.util.DateTicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 列表排序方式 */
enum class SortOrder { BY_REMAINING_DAYS, BY_CREATED_TIME }

/**
 * 一条事件 + 派生出来的展示数据。
 * UI 只依赖这个，不自己算天数，避免多处口径不一致。
 */
data class CountdownItem(
    val event: CountdownEvent,
    /** 展示用的大数字（始终非负） */
    val displayDays: Long,
    /** 语义状态：未来 / 今天 / 已过期 / 正计日 */
    val state: CountdownCalculator.DayState,
    /** 剩余天数（倒计日可为负） */
    val remainingDays: Long,
    /** 已过去天数（正计日） */
    val elapsedDays: Long,
    val totalDays: Long,
    val progress: Float,
    /** 当前的排序键，越小越靠前 */
    val sortValue: Long
) {
    val mode: CountdownMode get() = event.mode
}

/** 列表页 UI 状态 */
data class CountdownUiState(
    val items: List<CountdownItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_REMAINING_DAYS,
    val loading: Boolean = true
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()

    /** 置顶事件，用于顶部 Hero 展示位 */
    val pinnedItem: CountdownItem? get() = items.firstOrNull { it.event.pinned }
}

/**
 * 列表页 ViewModel。
 * - 数据源：Room 的 Flow，数据库一变列表自动刷新
 * - 排序：按天数（默认）或按创建时间
 * - 增删改：直接落库
 */
class CountdownListViewModel(
    application: Application,
    private val repository: CountdownRepository
) : AndroidViewModel(application) {

    private val sortOrder = MutableStateFlow(SortOrder.BY_REMAINING_DAYS)

    /** 首帧数据是否已到达：Room 的 Flow 首次发射前显示 loading */
    private val loaded = MutableStateFlow(false)

    /**
     * "今天"的日期，每跨一个本地零点推进一步。
     *
     * 为什么需要：天数是在这里按今天的日期算出来的，而 Room 只在数据变化时重新发射。
     * 没有这个 ticker 的话，应用在前台过夜时"剩余 1 天"不会变成 0（核心功能失效）。
     * 同时它也覆盖了时区/夏令时切换导致日期跳变的情况。
     */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(DateTicker.today())
            delay(DateTicker.millisUntilNextDay())
        }
    }

    /**
     * 原始事件流。注意这里只发射实体，不与日期耦合 ——
     * 派生数据（天数/排序键）在下面的 combine 里按"当前日期"计算，
     * 这样日期一变就会整体重算，而不用等数据库变化。
     */
    private val events: StateFlow<List<CountdownEvent>> = repository.observeAll()
        .onEach { loaded.value = true }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<CountdownUiState> =
        combine(events, sortOrder, loaded, today) { list, order, isLoaded, date ->
            if (!isLoaded) {
                CountdownUiState(loading = true, sortOrder = order)
            } else {
                val items = list.map { it.toItem(date) }
                val sorted = when (order) {
                    // 数值越小越靠前：倒计日的已过期、正计日的已过越久都排在前面
                    SortOrder.BY_REMAINING_DAYS -> items.sortedWith(
                        compareBy<CountdownItem> { it.sortValue }.thenBy { it.event.title }
                    )
                    SortOrder.BY_CREATED_TIME -> items.sortedByDescending { it.event.createdAt }
                }
                CountdownUiState(items = sorted, sortOrder = order, loading = false)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CountdownUiState(loading = true)
        )

    fun toggleSortOrder() {
        sortOrder.value = when (sortOrder.value) {
            SortOrder.BY_REMAINING_DAYS -> SortOrder.BY_CREATED_TIME
            SortOrder.BY_CREATED_TIME -> SortOrder.BY_REMAINING_DAYS
        }
    }

    fun delete(event: CountdownEvent) {
        viewModelScope.launch { repository.delete(event) }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }
}

/**
 * 把实体转成带派生数据的展示项。
 *
 * 抽成 internal 顶层函数是为了可单测（不需要 Application/ViewModel）。
 * 注意 [date] 必须显式传入 —— 之前用 LocalDate.now() 默认参数导致跨零点不刷新。
 */
internal fun CountdownEvent.toItem(date: LocalDate): CountdownItem {
    val target = targetLocalDate
    val createdDate = Instant.ofEpochMilli(createdAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val display = CountdownCalculator.displayDays(this, date)

    return CountdownItem(
        event = this,
        displayDays = display.days,
        state = display.state,
        remainingDays = CountdownCalculator.daysUntil(target, date),
        elapsedDays = CountdownCalculator.elapsedDays(target, date),
        // 创建日 -> 目标日的时间跨度。倒计日为正；正计日因为目标日早于创建日会是负数，
        // 所以统一取绝对值，避免把负数天数暴露给 UI。
        totalDays = kotlin.math.abs(ChronoUnit.DAYS.between(createdDate, target)),        progress = CountdownCalculator.progressPercent(target, createdAt, date, mode),
        sortValue = CountdownCalculator.sortKey(this, date)
    )
}

/** 编辑页 ViewModel：读取单条事件与保存 */
class EventEditViewModel(
    private val repository: CountdownRepository
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** UI 消费完"保存成功"事件后复位 */
    fun consumeSaved() {
        _saved.value = false
    }

    suspend fun load(id: Long): CountdownEvent? = repository.getById(id)

    fun save(
        id: Long,
        title: String,
        targetDate: LocalDate,
        mode: CountdownMode,
        note: String?,
        backgroundUri: String?,
        notifyEnabled: Boolean,
        pinned: Boolean
    ) {
        viewModelScope.launch {
            val epochDay = targetDate.toEpochDay()
            val cleanNote = note?.takeIf { it.isNotBlank() }
            val cleanBg = backgroundUri?.takeIf { it.isNotBlank() }

            if (id == 0L) {
                repository.insert(
                    CountdownEvent(
                        title = title.trim(),
                        targetDate = epochDay,
                        mode = mode,
                        note = cleanNote,
                        backgroundUri = cleanBg,
                        pinned = pinned,
                        notifyEnabled = notifyEnabled
                    )
                )
            } else {
                repository.getById(id)?.let { existing ->
                    repository.update(
                        existing.copy(
                            title = title.trim(),
                            targetDate = epochDay,
                            mode = mode,
                            note = cleanNote,
                            backgroundUri = cleanBg,
                            pinned = pinned,
                            notifyEnabled = notifyEnabled,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            _saved.value = true
        }
    }

    fun delete(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteById(id)
            onDone()
        }
    }
}

/** 没有使用 Hilt，用手写 Factory 提供依赖 */
class CountdownViewModelFactory(
    private val repository: CountdownRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            ?: throw IllegalStateException("无法获取 Application，请检查 ViewModel 的创建方式")
        return when {
            modelClass.isAssignableFrom(CountdownListViewModel::class.java) ->
                CountdownListViewModel(application, repository) as T
            modelClass.isAssignableFrom(EventEditViewModel::class.java) ->
                EventEditViewModel(repository) as T
            else -> throw IllegalArgumentException("未知的 ViewModel: ${modelClass.name}")
        }
    }
}
