package com.example.countdown.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 剩余天数计算 + 排序工具。
 *
 * 约定：
 *  - 今天到期 -> 0
 *  - 未来日期 -> 正数
 *  - 已过去   -> 负数
 */
object CountdownCalculator {

    fun daysUntil(target: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, target)

    /** 按剩余天数升序排序：已过期（负数）在最前，越接近今天越靠前 */
    fun sortByRemaining(
        events: List<CountdownEvent>,
        today: LocalDate = LocalDate.now()
    ): List<CountdownEvent> = events.sortedWith(
        compareBy<CountdownEvent> { daysUntil(it.targetLocalDate, today) }
            .thenBy { it.title }
    )

    fun progressPercent(target: LocalDate, createdAtMillis: Long, today: LocalDate = LocalDate.now()): Float {
        val start = java.time.Instant.ofEpochMilli(createdAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val total = ChronoUnit.DAYS.between(start, target)
        if (total <= 0L) return 1f
        val elapsed = ChronoUnit.DAYS.between(start, today)
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
