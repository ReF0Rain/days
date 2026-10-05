package com.example.countdown.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.countdown.MainActivity
import com.example.countdown.data.CountdownCalculator
import com.example.countdown.data.CountdownEvent
import com.example.countdown.data.CountdownMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 通知相关的一切集中在这里：
 *  - 通知渠道创建（Android 8.0+ 必须）
 *  - Android 13 通知权限判断/请求时机
 *  - 每日倒数通知的构建与发送
 */
object CountdownNotifications {

    const val CHANNEL_DAILY = "channel_daily_countdown"

    /** 单条通知的固定 ID（同一天只保留一条，避免刷屏） */
    private const val SUMMARY_NOTIFICATION_ID = 1001

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    // ------------------------------------------------------------------
    // 渠道
    // ------------------------------------------------------------------

    /** 幂等创建通知渠道，可在 Application 中调用 */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(com.example.countdown.R.string.channel_daily_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(com.example.countdown.R.string.channel_daily_description)
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------
    // 权限（Android 13+）
    // ------------------------------------------------------------------

    /** 是否需要申请 POST_NOTIFICATIONS 运行时权限 */
    fun needsPermissionRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return !granted && enabled
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------------
    // 每日更新
    // ------------------------------------------------------------------

    /** 单个事件的天数文案，区分倒计日与正计日 */
    fun daysText(event: CountdownEvent): String =
        when (event.mode) {
            CountdownMode.COUNTUP -> {
                val d = CountdownCalculator.elapsedDays(event.targetLocalDate)
                if (d == 0L) "就是今天" else "已经过去 $d 天"
            }
            CountdownMode.COUNTDOWN -> {
                val d = CountdownCalculator.daysUntil(event.targetLocalDate)
                when {
                    d > 0L -> "还有 $d 天"
                    d == 0L -> "就是今天"
                    else -> "已过去 ${-d} 天"
                }
            }
        }

    /**
     * 刷新每日通知：把所有开启了通知的事件汇总成一条通知。
     *
     * 入选规则：
     *  - 正计日：始终入选（每天都在累加，正是它要提醒的内容）
     *  - 倒计日：只保留还没过期的，过期的不再打扰
     *
     * 排序按 sortKey 升序，与列表页保持一致的"紧迫度"口径。
     */
    fun updateDailyNotification(context: Context, events: List<CountdownEvent>) {
        val today = LocalDate.now()
        val actionable = events
            .filter { it.notifyEnabled }
            .filter { event ->
                event.mode == CountdownMode.COUNTUP ||
                    CountdownCalculator.daysUntil(event.targetLocalDate, today) >= 0L
            }
            .sortedBy { CountdownCalculator.sortKey(it, today) }

        if (actionable.isEmpty() || !hasPermission(context)) {
            cancelDailyNotification(context)
            return
        }

        val manager = NotificationManagerCompat.from(context)
        val nearest = actionable.first()
        val title = "${nearest.title} · ${daysText(nearest)}"

        val lines = actionable.take(6).map { event ->
            "· ${event.title}：${daysText(event)}（${event.targetLocalDate.format(dateFormatter)}）"
        }
        val moreCount = actionable.size - lines.size
        val content = buildString {
            append(lines.joinToString("\n"))
            if (moreCount > 0) append("\n· 以及另外 $moreCount 个事件")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(com.example.countdown.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull()?.removePrefix("· ") ?: title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .build()

        try {
            manager.notify(SUMMARY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限在运行中被撤销，忽略即可
        }
    }

    fun cancelDailyNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
