package com.example.countdown.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 每日更新任务的注册与触发（WorkManager）。
 *
 * - 周期性任务：每 24 小时跑一次（首次对齐到次日 09:00）
 * - 一次性任务：手动“立即刷新”时使用
 */
object DailyUpdateScheduler {

    const val PERIODIC_WORK_NAME = "countdown_daily_update"
    const val ONESHOT_WORK_NAME = "countdown_daily_update_once"

    /** 每天几点执行（默认 09:00） */
    private val TARGET_TIME: LocalTime = LocalTime.of(9, 0)

    /** 注册（或更新）周期性每日任务，可在 Application / 权限授予后调用 */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag(DailyUpdateWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** 立即执行一次（用于权限刚授予、事件增删后） */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyUpdateWorker>()
            .addTag(DailyUpdateWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /** 距离下一个 09:00 还有多少分钟（用于 initialDelay） */
    private fun initialDelayMinutes(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(TARGET_TIME)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMinutes().coerceAtLeast(1L)
    }
}
