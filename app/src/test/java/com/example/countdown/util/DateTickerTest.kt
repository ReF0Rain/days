package com.example.countdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * DateTicker 的单元测试。
 *
 * 这里是跨零点刷新功能的正确性基础：如果 millisUntilNextDay() 算错，
 * 要么天数不刷新，要么会疯狂循环。
 */
class DateTickerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun clockAt(localDateTime: String): Clock =
        Clock.fixed(Instant.parse(localDateTime), zone)

    @Test
    fun `noon waits until next midnight`() {
        // 12:00 -> 次日 00:00 = 12 小时
        val clock = clockAt("2026-10-05T04:00:00Z") // 上海时间 12:00
        assertEquals(12 * 60 * 60 * 1000L, DateTicker.millisUntilNextDay(clock))
    }

    @Test
    fun `one minute before midnight waits one minute`() {
        val clock = clockAt("2026-10-05T15:59:00Z") // 上海时间 23:59
        assertEquals(60_000L, DateTicker.millisUntilNextDay(clock))
    }

    @Test
    fun `just after midnight waits almost a full day`() {
        val clock = clockAt("2026-10-05T16:00:01Z") // 上海时间 00:00:01
        val expected = 24 * 60 * 60 * 1000L - 1000L
        assertEquals(expected, DateTicker.millisUntilNextDay(clock))
    }

    @Test
    fun `always positive so delay never becomes a busy loop`() {
        // 极接近零点时也不能返回 0 或负数
        val clock = Clock.fixed(Instant.parse("2026-10-05T15:59:59.999Z"), zone)
        assertTrue(DateTicker.millisUntilNextDay(clock) > 0L)
    }

    @Test
    fun `today follows the given clock and zone`() {
        val clock = clockAt("2026-10-05T16:30:00Z") // 上海时间已是 10-06 00:30
        assertEquals(LocalDate.of(2026, 10, 6), DateTicker.today(clock))

        // 同一时刻在 UTC 还是 10-05
        val utcClock = Clock.fixed(Instant.parse("2026-10-05T16:30:00Z"), ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 10, 5), DateTicker.today(utcClock))
    }

    @Test
    fun `handles daylight saving transition without negative values`() {
        // 用有夏令时的时区，跨切换日也要保证结果为正
        val dstZone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), dstZone)
        assertTrue(DateTicker.millisUntilNextDay(clock) > 0L)
    }
}
