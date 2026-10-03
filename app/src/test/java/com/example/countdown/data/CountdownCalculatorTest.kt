package com.example.countdown.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** CountdownCalculator 的纯逻辑单元测试（无需 Android 环境） */
class CountdownCalculatorTest {

    @Test
    fun `daysUntil returns positive for future dates`() {
        val today = LocalDate.of(2026, 10, 3)
        val target = LocalDate.of(2026, 10, 13)
        assertEquals(10L, CountdownCalculator.daysUntil(target, today))
    }

    @Test
    fun `daysUntil returns zero for today`() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(0L, CountdownCalculator.daysUntil(today, today))
    }

    @Test
    fun `daysUntil returns negative for past dates`() {
        val today = LocalDate.of(2026, 10, 3)
        val target = LocalDate.of(2026, 10, 1)
        assertEquals(-2L, CountdownCalculator.daysUntil(target, today))
    }

    @Test
    fun `sortByRemaining puts overdue first then nearest`() {
        val today = LocalDate.of(2026, 10, 3)
        val events = listOf(
            CountdownEvent(id = 1, title = "远", targetDate = LocalDate.of(2027, 1, 1).toEpochDay()),
            CountdownEvent(id = 2, title = "近", targetDate = LocalDate.of(2026, 10, 5).toEpochDay()),
            CountdownEvent(id = 3, title = "过期", targetDate = LocalDate.of(2026, 9, 30).toEpochDay())
        )

        val sorted = CountdownCalculator.sortByRemaining(events, today)

        assertEquals(listOf("过期", "近", "远"), sorted.map { it.title })
    }

    @Test
    fun `epochDay round trip keeps the date`() {
        val date = LocalDate.of(2026, 2, 28)
        val event = CountdownEvent(title = "回环", targetDate = date.toEpochDay())
        assertEquals(date, event.targetLocalDate)
    }
}
