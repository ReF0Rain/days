package com.example.countdown.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CountdownText 的单元测试。
 * 纯 JVM，不需要 Android 环境（CountdownText 刻意不依赖 Compose 运行时）。
 */
class CountdownTextTest {

    @Test
    fun `zero and negative days say starting today`() {
        assertEquals("从今天开始计算", CountdownText.countUpHint(0L))
        assertEquals("从今天开始计算", CountdownText.countUpHint(-5L))
    }

    @Test
    fun `under one week shows plain days`() {
        assertEquals("已过去 1 天", CountdownText.countUpHint(1L))
        assertEquals("已过去 6 天", CountdownText.countUpHint(6L))
    }

    @Test
    fun `under one month shows weeks plus days`() {
        assertEquals("已过去 1 周零 0 天", CountdownText.countUpHint(7L))
        assertEquals("已过去 2 周零 3 天", CountdownText.countUpHint(17L))
        assertEquals("已过去 4 周零 1 天", CountdownText.countUpHint(29L))
    }

    @Test
    fun `under one year shows months plus days`() {
        assertEquals("已过去 1 个月零 0 天", CountdownText.countUpHint(30L))
        assertEquals("已过去 3 个月零 10 天", CountdownText.countUpHint(100L))
    }

    @Test
    fun `one year and beyond shows years`() {
        assertEquals("已过去 1 年", CountdownText.countUpHint(365L))
        assertEquals("已过去 1 年零 1 个月", CountdownText.countUpHint(400L))
        assertEquals("已过去 2 年零 6 个月", CountdownText.countUpHint(365 * 2 + 180))
    }

    @Test
    fun `large values stay readable`() {
        // 18 年多，不应该出现 "已过去 18 年零 0 个月" 这类尾巴
        assertEquals("已过去 18 年", CountdownText.countUpHint(365 * 18 + 10))
    }
}
