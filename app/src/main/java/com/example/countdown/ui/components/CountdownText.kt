package com.example.countdown.ui.components

/**
 * 纯文本换算逻辑集中在这里，方便单元测试覆盖（不依赖 Compose 运行时）。
 */
object CountdownText {

    /**
     * 正计日的补充说明：把"天数"换成更好懂的周/月/年。
     * 说明：这里用 30 天近似一个月、365 天近似一年 —— 展示用的口语化描述，
     * 不需要日历精度（精确天数已经用大号数字展示了）。
     */
    fun countUpHint(days: Long): String = when {
        days <= 0L -> "从今天开始计算"
        days < 7L -> "已过去 $days 天"
        days < 30L -> "已过去 ${days / 7} 周零 ${days % 7} 天"
        days < 365L -> "已过去 ${days / 30} 个月零 ${days % 30} 天"
        else -> {
            val years = days / 365
            val months = (days % 365) / 30
            if (months == 0L) "已过去 $years 年" else "已过去 $years 年零 $months 个月"
        }
    }
}
