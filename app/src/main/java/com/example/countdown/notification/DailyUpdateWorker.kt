package com.example.countdown.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.countdown.data.CountdownRepository
import com.example.countdown.widget.refreshAllWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 每日更新任务：
 *  1. 读取 Room 中所有事件
 *  2. 重新计算剩余天数并刷新通知
 *  3. 让桌面小组件一起刷新
 *
 * 返回值遵循 WorkManager 约定：成功 Result.success()，异常 Result.retry()。
 */
class DailyUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            CountdownNotifications.createChannels(applicationContext)

            val events = CountdownRepository.getInstance(applicationContext).getAllOnce()
            CountdownNotifications.updateDailyNotification(applicationContext, events)
            refreshAllWidgets(applicationContext)

            Log.d(TAG, "每日倒数通知已刷新，共 ${events.size} 个事件")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "每日倒数通知刷新失败，稍后重试", t)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "DailyUpdateWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
