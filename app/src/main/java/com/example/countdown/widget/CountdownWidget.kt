package com.example.countdown.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.countdown.MainActivity
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownRepository
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

/**
 * 桌面小组件（Glance 实现，可选功能）。
 * 数据同样来自 Room；WorkManager 每天刷新一次，也可以在应用内手动刷新。
 */
class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 读取失败时退化为空列表，避免小组件进程崩溃
        val events = runCatching {
            CountdownRepository.getInstance(context).getAllOnce()
        }.getOrDefault(emptyList())

        provideContent {
            CountdownWidgetContent(events, context)
        }
    }
}

/** 刷新所有已添加到桌面的小组件 */
suspend fun refreshAllWidgets(context: Context) {
    runCatching { CountdownWidget().updateAll(context) }
}

/**
 * 点击小组件时打开主界面。
 * 注意：Glance 1.1.0 里 actionStartActivity 只有接收 Intent 的重载，
 * 没有 `actionStartActivity<T>()` 这种带类型参数的重载。
 */
private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** 小组件内容：置顶优先，其余按紧迫度升序，最多 4 条 */
@Composable
fun CountdownWidgetContent(allEvents: List<CountdownEvent>, context: Context) {
    val events = allEvents
        // 与列表页共用同一个 sortKey 口径（倒计日按剩余、正计日按已过天数取负）
        .sortedWith(
            compareBy({ CountdownCalculator.sortKey(it) }, { it.title })
        )
        .sortedByDescending { it.pinned }
        .take(4)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.White))
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openAppIntent(context)))
    ) {
        Text(
            text = "倒计日",
            style = TextStyle(
                color = ColorProvider(Color(0xFF1565C0)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(GlanceModifier.height(6.dp))

        if (events.isEmpty()) {
            Text(
                text = "暂无事件，点我添加",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF6B6B72)),
                    fontSize = 13.sp
                )
            )
        } else {
            events.forEach { event ->
                WidgetRow(event)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun WidgetRow(event: CountdownEvent) {
    val display = CountdownCalculator.displayDays(event)
    val dayText = display.days.toString()
    // 正计日显示 "天"，倒计日按剩余/过期显示 "天后 / 天前"
    val suffix = when (display.state) {
        CountdownCalculator.DayState.FUTURE -> "天后"
        CountdownCalculator.DayState.TODAY -> "今天"
        CountdownCalculator.DayState.PAST -> "天前"
        CountdownCalculator.DayState.ELAPSED -> "天"
    }
    val accent = when (display.state) {
        CountdownCalculator.DayState.PAST -> Color(0xFFC62828)
        CountdownCalculator.DayState.TODAY -> Color(0xFFFFA000)
        CountdownCalculator.DayState.ELAPSED -> Color(0xFF00796B)
        CountdownCalculator.DayState.FUTURE -> Color(0xFF1565C0)
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = dayText,
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = suffix,
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 11.sp
            )
        )
        Spacer(GlanceModifier.width(10.dp))
        // defaultWeight() 是 RowScope 的成员扩展，不需要（也不能）单独 import。
        // 这里改用 fillMaxWidth()，同样让文字列占满剩余空间。
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = event.title,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1B1B1F)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = event.targetLocalDate.format(SHORT_DATE),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF6B6B72)),
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}
