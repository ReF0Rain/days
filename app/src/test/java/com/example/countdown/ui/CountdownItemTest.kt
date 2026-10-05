package com.example.countdown.ui

import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * CountdownItem.toItem 的单元测试。
 *
 * 这个函数是"跨零点刷新"与"天数展示"的汇合点：它必须完全由传入的 date 决定，
 * 不允许内部偷偷取 LocalDate.now() —— 否则前台过夜时天数不会变。
 * 用固定日期调用两次，就能验证这一点。
 */
class CountdownItemTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun createdOn(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `remaining days follow the passed date not the system clock`() {
        val target = LocalDate.of(2026, 10, 10)
        val event = CountdownEvent(
            title = "目标",
            targetDate = target.toEpochDay(),
            createdAt = createdOn(LocalDate.of(2026, 10, 1))
        )

        // 同一条记录，换一个"今天"，天数必须跟着变
        assertEquals(5L, event.toItem(LocalDate.of(2026, 10, 5)).displayDays)
        assertEquals(0L, event.toItem(LocalDate.of(2026, 10, 10)).displayDays)
        assertEquals(2L, event.toItem(LocalDate.of(2026, 10, 12)).displayDays)
    }

    @Test
    fun `state transitions across the midnight boundary`() {
        val target = LocalDate.of(2026, 10, 6)
        val event = CountdownEvent(title = "明天", targetDate = target.toEpochDay())

        // 前一天：FUTURE；当天：TODAY；之后：PAST —— 这正是跨零点要发生的变化
        assertEquals(
            CountdownCalculator.DayState.FUTURE,
            event.toItem(LocalDate.of(2026, 10, 5)).state
        )
        assertEquals(
            CountdownCalculator.DayState.TODAY,
            event.toItem(LocalDate.of(2026, 10, 6)).state
        )
        assertEquals(
            CountdownCalculator.DayState.PAST,
            event.toItem(LocalDate.of(2026, 10, 7)).state
        )
    }

    @Test
    fun `countup elapsed days grow with the passed date`() {
        val start = LocalDate.of(2026, 1, 1)
        val event = CountdownEvent(
            title = "入职",
            targetDate = start.toEpochDay(),
            mode = CountdownMode.COUNTUP
        )

        assertEquals(0L, event.toItem(start).displayDays)
        assertEquals(99L, event.toItem(LocalDate.of(2026, 4, 10)).displayDays)
        assertEquals(364L, event.toItem(LocalDate.of(2026, 12, 31)).displayDays)
    }

    @Test
    fun `totalDays is never negative even when target precedes creation`() {
        // 正计日常见：先创建记录，起始日填的是过去 —— 创建日 -> 目标日是负跨度
        val event = CountdownEvent(
            title = "入职",
            targetDate = LocalDate.of(2020, 1, 1).toEpochDay(),
            mode = CountdownMode.COUNTUP,
            createdAt = createdOn(LocalDate.of(2026, 10, 5))
        )
        val item = event.toItem(LocalDate.of(2026, 10, 5))
        assertTrue("totalDays 不应为负数，实际 ${item.totalDays}", item.totalDays >= 0L)
    }

    @Test
    fun `sort value keeps overdue first and far future last`() {
        val today = LocalDate.of(2026, 10, 5)
        val overdue = CountdownEvent(title = "过期", targetDate = LocalDate.of(2026, 9, 1).toEpochDay())
        val soon = CountdownEvent(title = "三天后", targetDate = LocalDate.of(2026, 10, 8).toEpochDay())

        assertTrue(overdue.toItem(today).sortValue < soon.toItem(today).sortValue)
    }

    @Test
    fun `createdAt in the past still yields a sane progress`() {
        val today = LocalDate.of(2026, 10, 5)
        val event = CountdownEvent(
            title = "目标",
            targetDate = LocalDate.of(2026, 10, 15).toEpochDay(),
            createdAt = createdOn(LocalDate.of(2026, 10, 1))
        )
        val progress = event.toItem(today).progress
        assertTrue("progress 应在 0..1，实际 $progress", progress in 0f..1f)
        // 创建日 10-01 -> 目标 10-15 共 14 天，今天 10-05 已过 4 天 => 4/14
        assertEquals(4f / 14f, progress, 0.0001f)
    }

    @Test
    fun `createdAt use epoch millis is stable for a fixed date`() {
        // 防御性：确认测试辅助方法本身没算错（避免测试假通过）
        val millis = createdOn(LocalDate.of(2026, 10, 5))
        val back = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 5), back)
    }
}
