package com.example.countdown.util

import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 跨零点刷新的时间工具。
 *
 * 背景：天数是在 ViewModel 里按"今天的日期"算出来的，而 Room 只有在数据变化时才会
 * 重新发射。如果应用在前台过夜，显示的"剩余 1 天"第二天不会变成 0 —— 对倒计日 App
 * 来说这是核心功能失效。所以需要一个按天推进的时间源来驱动重算。
 *
 * 这里刻意把"距下一个零点还有多少毫秒"抽成纯函数，注入 [Clock] 便于单测。
 */
object DateTicker {

    /** 距下一个本地零点还有多少毫秒，至少返回 1 毫秒（避免 delay(0) 造成忙循环） */
    fun millisUntilNextDay(clock: Clock = Clock.systemDefaultZone()): Long {
        val now = ZonedDateTime.now(clock)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val millis = ChronoUnit.MILLIS.between(now, nextMidnight)
        return millis.coerceAtLeast(1L)
    }

    /** 当前本地日期 */
    fun today(clock: Clock = Clock.systemDefaultZone()): LocalDate =
        LocalDate.now(clock)
}
