package com.example.countdown.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** CountdownCalculator 的纯逻辑单元测试（无需 Android 环境） */
class CountdownCalculatorTest {

    private val today = LocalDate.of(2026, 10, 3)

    // ---------------- 倒计日 ----------------

    @Test
    fun `daysUntil returns positive for future dates`() {
        assertEquals(10L, CountdownCalculator.daysUntil(LocalDate.of(2026, 10, 13), today))
    }

    @Test
    fun `daysUntil returns zero for today`() {
        assertEquals(0L, CountdownCalculator.daysUntil(today, today))
    }

    @Test
    fun `daysUntil returns negative for past dates`() {
        assertEquals(-2L, CountdownCalculator.daysUntil(LocalDate.of(2026, 10, 1), today))
    }

    // ---------------- 正计日 ----------------

    @Test
    fun `elapsedDays counts days since start`() {
        assertEquals(365L, CountdownCalculator.elapsedDays(LocalDate.of(2025, 10, 3), today))
    }

    @Test
    fun `elapsedDays is zero for today and never negative for future`() {
        assertEquals(0L, CountdownCalculator.elapsedDays(today, today))
        assertEquals(0L, CountdownCalculator.elapsedDays(LocalDate.of(2027, 1, 1), today))
    }

    @Test
    fun `displayDays for countup shows elapsed days`() {
        val event = CountdownEvent(
            title = "入职",
            targetDate = LocalDate.of(2026, 9, 3).toEpochDay(),
            mode = CountdownMode.COUNTUP
        )
        val display = CountdownCalculator.displayDays(event, today)
        assertEquals(30L, display.days)
        assertEquals(CountdownCalculator.DayState.ELAPSED, display.state)
    }

    @Test
    fun `displayDays for countup on the very day is TODAY`() {
        val event = CountdownEvent(
            title = "今天开始",
            targetDate = today.toEpochDay(),
            mode = CountdownMode.COUNTUP
        )
        val display = CountdownCalculator.displayDays(event, today)
        assertEquals(0L, display.days)
        assertEquals(CountdownCalculator.DayState.TODAY, display.state)
    }

    @Test
    fun `countup progress is always full`() {
        val event = CountdownEvent(
            title = "入职",
            targetDate = LocalDate.of(2026, 1, 1).toEpochDay(),
            mode = CountdownMode.COUNTUP,
            createdAt = 0L
        )
        assertEquals(
            1f,
            CountdownCalculator.progressPercent(
                event.targetLocalDate, event.createdAt, today, event.mode
            )
        )
    }

    // ---------------- 排序 ----------------

    @Test
    fun `sortByRemaining puts overdue first then nearest`() {
        val events = listOf(
            CountdownEvent(id = 1, title = "远", targetDate = LocalDate.of(2027, 1, 1).toEpochDay()),
            CountdownEvent(id = 2, title = "近", targetDate = LocalDate.of(2026, 10, 5).toEpochDay()),
            CountdownEvent(id = 3, title = "过期", targetDate = LocalDate.of(2026, 9, 30).toEpochDay())
        )
        assertEquals(
            listOf("过期", "近", "远"),
            CountdownCalculator.sortByRemaining(events, today).map { it.title }
        )
    }

    @Test
    fun `countup events sort by how long ago they started`() {
        val events = listOf(
            CountdownEvent(
                id = 1, title = "刚发生",
                targetDate = LocalDate.of(2026, 10, 1).toEpochDay(),
                mode = CountdownMode.COUNTUP
            ),
            CountdownEvent(
                id = 2, title = "很久以前",
                targetDate = LocalDate.of(2020, 1, 1).toEpochDay(),
                mode = CountdownMode.COUNTUP
            )
        )
        // 已过越久 -> sortKey 越小 -> 排越前
        assertEquals(
            listOf("很久以前", "刚发生"),
            CountdownCalculator.sortByRemaining(events, today).map { it.title }
        )
    }

    @Test
    fun `mixed modes sort together by urgency`() {
        val events = listOf(
            CountdownEvent(
                id = 1, title = "倒计时3天",
                targetDate = LocalDate.of(2026, 10, 6).toEpochDay()
            ),
            CountdownEvent(
                id = 2, title = "正计1天前",
                targetDate = LocalDate.of(2026, 10, 2).toEpochDay(),
                mode = CountdownMode.COUNTUP
            ),
            CountdownEvent(
                id = 3, title = "已过期",
                targetDate = LocalDate.of(2026, 9, 1).toEpochDay()
            )
        )
        val sorted = CountdownCalculator.sortByRemaining(events, today).map { it.title }
        // 已过期(-32) < 正计1天前(-1) < 倒计时3天(3)
        assertEquals(listOf("已过期", "正计1天前", "倒计时3天"), sorted)
    }

    // ---------------- 枚举与存储 ----------------

    @Test
    fun `mode survives epochDay round trip`() {
        val date = LocalDate.of(2026, 2, 28)
        val event = CountdownEvent(title = "回环", targetDate = date.toEpochDay())
        assertEquals(date, event.targetLocalDate)
    }

    @Test
    fun `unknown stored mode falls back to countdown`() {
        assertEquals(CountdownMode.COUNTDOWN, CountdownMode.fromStorage(null))
        assertEquals(CountdownMode.COUNTDOWN, CountdownMode.fromStorage("SOMETHING_ELSE"))
        assertEquals(CountdownMode.COUNTUP, CountdownMode.fromStorage("COUNTUP"))
    }

    @Test
    fun `hasBackground reflects uri presence`() {
        assertFalse(CountdownEvent(title = "a", targetDate = 0L).hasBackground)
        assertFalse(CountdownEvent(title = "a", targetDate = 0L, backgroundUri = "  ").hasBackground)
        assertTrue(
            CountdownEvent(title = "a", targetDate = 0L, backgroundUri = "file:///tmp/x.jpg")
                .hasBackground
        )
    }

    @Test
    fun `usesLightForeground only when a background is visible`() {
        val withImage = CountdownEvent(
            title = "a", targetDate = 0L, backgroundUri = "file:///x.jpg", backgroundDim = 0.35f
        )
        assertTrue(withImage.usesLightForeground)
        assertFalse(withImage.copy(backgroundDim = 0.1f).usesLightForeground)
        assertFalse(CountdownEvent(title = "a", targetDate = 0L).usesLightForeground)
    }
}
