package com.example.countdown.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 倒计日事件。
 *
 * [targetDate] 以 epochDay（1970-01-01 起的天数）存储，
 * 与 java.time 的 [LocalDate.toEpochDay] 完全对应，避免时区带来的偏移。
 */
@Entity(tableName = "events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 事件标题，例如“结婚纪念日” */
    @ColumnInfo(name = "title")
    val title: String,

    /** 目标日期（epochDay） */
    @ColumnInfo(name = "target_date")
    val targetDate: Long,

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
    /** 转为 [LocalDate]，便于计算剩余天数 */
    val targetLocalDate: LocalDate
        get() = LocalDate.ofEpochDay(targetDate)
}
