package com.example.countdown.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 天数计算 + 排序工具。
 *
 * 两种模式共用一个字段 [CountdownEvent.targetDate]：
 *  - [CountdownMode.COUNTDOWN] 倒计日：target 在未来 -> 剩余天数；今天 -> 0；已过去 -> 负数
 *  - [CountdownMode.COUNTUP]   正计日：target 在过去 -> 已经过去多少天；今天 -> 0
 */
object CountdownCalculator {

    fun daysUntil(target: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, target)

    /** 已经过去多少天（正计日用）：今天为 0，未来日期也按 0 处理 */
    fun elapsedDays(start: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(start, today).coerceAtLeast(0L)

    /**
     * 排序用的键，数值越小越靠前。
     *
     * 排序规则（分组，避免倒计日与正计日混排后出现无意义的结果）：
     *   1. 先按模式分组：倒计日在前，正计日在后
     *   2. 组内：
     *      - 倒计日：按剩余天数升序 —— 已过期的最靠前，越接近今天越靠前
     *      - 正计日：按已过天数降序 —— 刚发生的靠前，久远的靠后
     *
     * 为什么必须分组：如果把正计日的"已过 400 天"也当作数值一起升序排，
     * 它会排到"已过期 10 天"的前面，看起来像"400 天前的事比刚过期的事更紧急"，
     * 这是没有意义的。
     */
    fun sortKey(event: CountdownEvent, today: LocalDate = LocalDate.now()): Long {
        // 组偏移：倒计日整体排在正计日之前。
        // 倒计日的键范围是 [-10^9, 10^9]，减 10^10 后落到正计日键之下。
        val groupOffset = when (event.mode) {
            CountdownMode.COUNTDOWN -> -GROUP_GAP
            CountdownMode.COUNTUP -> 0L
        }
        val value = when (event.mode) {
            CountdownMode.COUNTDOWN -> daysUntil(event.targetLocalDate, today)
            // 负值：已过越多值越小 -> 久远的靠后，刚发生的靠前
            CountdownMode.COUNTUP -> -elapsedDays(event.targetLocalDate, today)
        }
        return groupOffset + value
    }

    /** 分组间隔，远大于任何实际天数，保证两组不会交叉 */
    private const val GROUP_GAP = 10_000_000_000L

    /** 按紧急程度升序排序 */
    fun sortByRemaining(
        events: List<CountdownEvent>,
        today: LocalDate = LocalDate.now()
    ): List<CountdownEvent> = events.sortedWith(
        compareBy<CountdownEvent> { sortKey(it, today) }.thenBy { it.title }
    )

    /**
     * 进度 0f~1f。
     * 倒计日：创建日 -> 目标日的完成比例；
     * 正计日：用 1f，表示"已经发生"，避免视觉上像未完成。
     */
    fun progressPercent(
        target: LocalDate,
        createdAtMillis: Long,
        today: LocalDate = LocalDate.now(),
        mode: CountdownMode = CountdownMode.COUNTDOWN
    ): Float {
        if (mode == CountdownMode.COUNTUP) return 1f
        val start = java.time.Instant.ofEpochMilli(createdAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val total = ChronoUnit.DAYS.between(start, target)
        if (total <= 0L) return 1f
        val elapsed = ChronoUnit.DAYS.between(start, today)
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** 统一出"展示用的大数字 + 语义状态" */
    fun displayDays(event: CountdownEvent, today: LocalDate = LocalDate.now()): DisplayDays =
        when (event.mode) {
            CountdownMode.COUNTDOWN -> {
                val d = daysUntil(event.targetLocalDate, today)
                when {
                    d > 0L -> DisplayDays(d, DayState.FUTURE)
                    d == 0L -> DisplayDays(0L, DayState.TODAY)
                    else -> DisplayDays(-d, DayState.PAST)
                }
            }
            CountdownMode.COUNTUP -> {
                val d = elapsedDays(event.targetLocalDate, today)
                if (d == 0L) DisplayDays(0L, DayState.TODAY)
                else DisplayDays(d, DayState.ELAPSED)
            }
        }

    data class DisplayDays(val days: Long, val state: DayState)

    enum class DayState {
        /** 倒计日：目标在未来 */
        FUTURE,

        /** 就是今天 */
        TODAY,

        /** 倒计日：目标已过去 */
        PAST,

        /** 正计日：已经过去一段时间 */
        ELAPSED
    }
}
