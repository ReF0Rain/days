package com.example.countdown.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 列表排序方式 */
enum class SortOrder { BY_REMAINING_DAYS, BY_CREATED_TIME }

/** 一条事件 + 其派生出来的展示数据 */
data class CountdownItem(
    val event: CountdownEvent,
    val remainingDays: Long,
    val totalDays: Long,
    val progress: Float
)

/** 列表页 UI 状态 */
data class CountdownUiState(
    val items: List<CountdownItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_REMAINING_DAYS,
    val loading: Boolean = true
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * 列表页 ViewModel。
 * - 数据源：Room 的 Flow，数据库一变列表自动刷新
 * - 排序：按剩余天数（默认）或按创建时间
 * - 增删改：直接落库
 */
class CountdownListViewModel(
    application: Application,
    private val repository: CountdownRepository
) : AndroidViewModel(application) {

    private val sortOrder = MutableStateFlow(SortOrder.BY_REMAINING_DAYS)

    /** null 表示首帧还没到（loading） */
    private val rawItems: StateFlow<List<CountdownItem>?> = repository.observeAll()
        .map { events -> events.map { it.toItem() } }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<CountdownUiState> =
        combine(rawItems, sortOrder) { items, order ->
            if (items == null) {
                CountdownUiState(loading = true, sortOrder = order)
            } else {
                val sorted = when (order) {
                    // 按剩余天数升序：已过期的排在最前，越接近今天越靠前
                    SortOrder.BY_REMAINING_DAYS -> items.sortedWith(
                        compareBy<CountdownItem> { it.remainingDays }.thenBy { it.event.title }
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

    private fun CountdownEvent.toItem(): CountdownItem {
        val today = LocalDate.now()
        val target = targetLocalDate
        val createdDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return CountdownItem(
            event = this,
            remainingDays = ChronoUnit.DAYS.between(today, target),
            totalDays = ChronoUnit.DAYS.between(createdDate, target),
            progress = CountdownCalculator.progressPercent(target, createdAt, today)
        )
    }
}

/** 编辑页 ViewModel：读取单条事件与保存 */
class EventEditViewModel(
    private val repository: CountdownRepository
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** UI 消费完“保存成功”事件后复位 */
    fun consumeSaved() {
        _saved.value = false
    }

    suspend fun load(id: Long): CountdownEvent? = repository.getById(id)

    fun save(
        id: Long,
        title: String,
        targetDate: LocalDate,
        note: String?,
        notifyEnabled: Boolean,
        pinned: Boolean
    ) {
        viewModelScope.launch {
            val epochDay = targetDate.toEpochDay()
            val cleanNote = note?.takeIf { it.isNotBlank() }

            if (id == 0L) {
                repository.insert(
                    CountdownEvent(
                        title = title.trim(),
                        targetDate = epochDay,
                        note = cleanNote,
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
                            note = cleanNote,
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
