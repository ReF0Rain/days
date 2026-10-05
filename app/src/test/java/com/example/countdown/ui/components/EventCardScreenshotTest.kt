package com.example.countdown.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import com.example.countdown.ui.CountdownItem
import com.example.countdown.ui.theme.CountdownTheme
import com.example.countdown.ui.toItem
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * 卡片渲染的截图测试（JVM + Robolectric + Roborazzi，不需要设备或模拟器）。
 *
 * 解决的实际问题：之前改 UI 只能"装到手机上看"，导致"正计日看不出区别"这类问题
 * 要等用户反馈才发现。这里把各种卡片形态渲染成 PNG，输出到：
 *     app/build/outputs/roborazzi/
 * CI 上会作为 artifact 上传，人来对比即可；文件变化也能在 PR 里直接看出来。
 *
 * 为什么用 Roborazzi 而不是 Compose 的 captureToImage()：
 * 后者在 Robolectric 下依赖真实窗口绘制，会抛
 *     ComposeTimeoutException: Condition still not satisfied after 2000 ms
 * Roborazzi 走 GraphicsLayer 绘制路径，在 JVM 上稳定可用。
 *
 * 派生数据直接复用生产代码的 `CountdownItem.toItem(date)`，不在测试里重写一份。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Roborazzi 规则：由规则负责出图（默认 CaptureType 会在每个测试后捕获根节点）。
     *
     * 踩过的坑：把 captureType 设成 CaptureType.None 想"只用手动 captureRoboImage()"，
     * 结果是文件根本不写 —— 记录行为受 captureType 控制。这里交回默认值。
     *
     * 不做像素比对（comparison）：不同机器/字体渲染有噪声，严格比对容易假失败。
     * 目标是"把画面导出来给人看"。
     */
    @get:Rule
    val roborazziRule = RoborazziRule(
        composeRule = composeRule,
        captureRoot = composeRule.onRoot(),
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi"
        )
    )

    private val today: LocalDate = LocalDate.of(2026, 10, 5)

    private fun renderCards(vararg items: CountdownItem) {
        composeRule.setContent {
            CountdownTheme(darkTheme = false) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items.forEach { item ->
                        EventCard(item = item, onEdit = {}, onDelete = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shoot(name: String) {
        // 显式指定文件名（用绝对路径，避免相对路径落到不确定的工作目录）
        val file = File(outputDir, "$name.png")
        composeRule.onRoot().captureRoboImage(file.absolutePath)
        println("screenshot -> ${file.absolutePath} exists=${file.exists()} size=${file.length()}")
    }

    private val outputDir: File by lazy {
        // 测试的工作目录是模块目录（app/），这里统一落到 app/build/outputs/roborazzi
        File("build/outputs/roborazzi").apply { mkdirs() }
    }

    @Test
    fun countdown_card() {
        renderCards(
            CountdownEvent(
                id = 1L,
                title = "结婚纪念日",
                targetDate = today.plusDays(128).toEpochDay(),
                note = "倒计日：有进度条",
                pinned = true
            ).toItem(today)
        )
        shoot("card_01_countdown")
    }

    @Test
    fun countup_card() {
        renderCards(
            CountdownEvent(
                id = 2L,
                title = "入职第一天",
                targetDate = today.minusDays(400).toEpochDay(),
                mode = CountdownMode.COUNTUP
            ).toItem(today)
        )
        shoot("card_02_countup")
    }

    @Test
    fun overdue_card() {
        renderCards(
            CountdownEvent(
                id = 3L,
                title = "已过期的目标",
                targetDate = today.minusDays(10).toEpochDay()
            ).toItem(today)
        )
        shoot("card_03_overdue")
    }

    @Test
    fun today_card() {
        renderCards(
            CountdownEvent(
                id = 4L,
                title = "就是今天",
                targetDate = today.toEpochDay()
            ).toItem(today)
        )
        shoot("card_04_today")
    }

    /** 四种形态并排一张图，用于一眼对比倒计日与正计日是否有明显区别 */
    @Test
    fun all_variants_side_by_side() {
        renderCards(
            CountdownEvent(
                id = 1L,
                title = "结婚纪念日",
                targetDate = today.plusDays(128).toEpochDay(),
                note = "倒计日：有进度条",
                pinned = true
            ).toItem(today),
            CountdownEvent(
                id = 2L,
                title = "入职第一天",
                targetDate = today.minusDays(400).toEpochDay(),
                mode = CountdownMode.COUNTUP
            ).toItem(today),
            CountdownEvent(
                id = 3L,
                title = "已过期的目标",
                targetDate = today.minusDays(10).toEpochDay()
            ).toItem(today),
            CountdownEvent(
                id = 4L,
                title = "今天到期",
                targetDate = today.toEpochDay()
            ).toItem(today)
        )
        shoot("card_00_all_variants")
    }

    companion object {
        /** 固定 Locale，避免不同机器/CI 默认语言导致文字宽度与换行不同 */
        @JvmStatic
        @BeforeClass
        fun fixLocale() {
            Locale.setDefault(Locale.CHINA)
        }
    }
}
