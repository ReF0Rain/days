package com.example.countdown.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "2026年10月3日" */
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

/** "周六" */
private val WEEK_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

/** "10/03" */
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)

fun LocalDate.formatFull(): String = format(FULL_DATE)

fun LocalDate.formatWeek(): String = format(WEEK_DAY)

fun LocalDate.formatShort(): String = format(SHORT_DATE)

/** 列表右侧/详情里的剩余天数大数字 */
fun remainingNumberText(days: Long): String = when {
    days > 0L -> days.toString()
    days == 0L -> "0"
    else -> (-days).toString()
}

/** 剩余天数的说明文案 */
fun remainingLabel(days: Long): String = when {
    days > 0L -> "天后"
    days == 0L -> "就是今天"
    else -> "天前"
}

/** 通知里的完整描述 */
fun remainingSentence(days: Long): String = when {
    days > 0L -> "还有 $days 天"
    days == 0L -> "就是今天"
    else -> "已过去 ${-days} 天"
}
