package com.example.countdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.countdown.data.CountdownDatabase
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 给 debug 包灌一份可复现的示例数据，方便在真机上核对各种卡片的渲染与排版。
 *
 * 为什么需要：debug 包的 applicationId 带 `.debug` 后缀，是**独立应用**，
 * 数据库与 release 包不共享。手点着造数据又慢又难复现。
 *
 * 用法：
 *     adb shell am instrument -w \
 *       -e class com.example.countdown.SeedSampleDataTest \
 *       com.example.countdown.debug.test/androidx.test.runner.AndroidJUnitRunner
 * 然后打开 debug 版的「倒计日」即可看到 6 条覆盖各种形态的事件。
 *
 * 注意：会清空 debug 包的现有数据（本来就只用于测试）。
 */
@RunWith(AndroidJUnit4::class)
class SeedSampleDataTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedSampleEvents() {
        val db = CountdownDatabase.getInstance(context)
        val dao = db.countdownDao()
        val today = LocalDate.now()

        runBlocking {
            dao.deleteAll()
            val samples = listOf(
                // 倒计日：远期（蓝）
                CountdownEvent(
                    title = "结婚纪念日",
                    targetDate = today.plusDays(128).toEpochDay(),
                    note = "准备一份小礼物",
                    pinned = true
                ),
                // 倒计日：临近（橙）
                CountdownEvent(
                    title = "项目交付",
                    targetDate = today.plusDays(3).toEpochDay()
                ),
                // 正计日：跨年（青）
                CountdownEvent(
                    title = "入职第一天",
                    targetDate = today.minusDays(400).toEpochDay(),
                    mode = CountdownMode.COUNTUP
                ),
                // 正计日：刚发生（青，个位数天）
                CountdownEvent(
                    title = "开始健身",
                    targetDate = today.minusDays(5).toEpochDay(),
                    mode = CountdownMode.COUNTUP
                ),
                // 倒计日：已过期（红）
                CountdownEvent(
                    title = "已过期的目标",
                    targetDate = today.minusDays(10).toEpochDay()
                ),
                // 当天（橙）
                CountdownEvent(
                    title = "就是今天",
                    targetDate = today.toEpochDay()
                ),
                // 备注很长的边界情况：检查会不会把卡片撑坏
                CountdownEvent(
                    title = "一个标题特别长的事件用来测试换行表现如何",
                    targetDate = today.plusDays(300).toEpochDay(),
                    note = "这是一段很长的备注文字，用来观察卡片在文字很多的时候排版是否会溢出、截断或者把其他元素挤变形。"
                )
            )
            samples.forEach { dao.insert(it) }

            assertEquals(samples.size, dao.count())
        }
    }
}
