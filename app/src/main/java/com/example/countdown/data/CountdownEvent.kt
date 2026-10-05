package com.example.countdown.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** 事件的计时方向 */
enum class CountdownMode {
    /** 倒计日：距离目标日期还有多少天（目标在未来） */
    COUNTDOWN,

    /** 正计日：从起始日期起已经过去多少天（起始在过去） */
    COUNTUP;

    companion object {
        /** 数据库里存字符串；读到未知值时回落到倒计日，保证升级/降级都不崩 */
        fun fromStorage(value: String?): CountdownMode =
            entries.firstOrNull { it.name == value } ?: COUNTDOWN
    }
}

/**
 * 倒计日 / 正计日事件。
 *
 * [targetDate] 以 epochDay（1970-01-01 起的天数）存储，
 * 与 java.time 的 [LocalDate.toEpochDay] 完全对应，避免时区带来的偏移。
 *
 * 注意：字段的 [ColumnInfo.defaultValue] 必须与 CountdownDatabase 里的迁移 SQL 保持一致，
 * 否则 Room 校验 schema 时会报 identityHash 不匹配。
 */
@Entity(
    tableName = "events",
    indices = [Index(value = ["target_date"], name = "index_events_target_date")]
)
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 事件标题，例如“结婚纪念日” */
    @ColumnInfo(name = "title")
    val title: String,

    /** 目标日期（倒计日）/ 起始日期（正计日），epochDay */
    @ColumnInfo(name = "target_date")
    val targetDate: Long,

    /** 计时方向，数据库存字符串 */
    @ColumnInfo(name = "mode", defaultValue = "COUNTDOWN")
    val mode: CountdownMode = CountdownMode.COUNTDOWN,

    /**
     * 卡片背景图的持久化 URI 字符串（由图片选择器返回的 content:// URI）。
     * 为空表示不使用自定义背景。
     */
    @ColumnInfo(name = "background_uri")
    val backgroundUri: String? = null,

    /** 背景图上的暗化蒙版强度 0f~1f，越大文字越清晰 */
    @ColumnInfo(name = "background_dim", defaultValue = "0.35")
    val backgroundDim: Float = 0.35f,

    /** 备注（可空） */
    @ColumnInfo(name = "note")
    val note: String? = null,

    /** 是否为置顶/重要事件（用于 UI 高亮） */
    @ColumnInfo(name = "pinned", defaultValue = "0")
    val pinned: Boolean = false,

    /** 是否参与“每日倒数通知” */
    @ColumnInfo(name = "notify_enabled", defaultValue = "1")
    val notifyEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 转为 [LocalDate]，便于计算天数 */
    val targetLocalDate: LocalDate
        get() = LocalDate.ofEpochDay(targetDate)

    /** 是否带自定义背景图 */
    val hasBackground: Boolean
        get() = !backgroundUri.isNullOrBlank()
}
