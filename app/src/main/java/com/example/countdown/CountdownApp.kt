package com.example.countdown

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.countdown.data.CountdownRepository
import com.example.countdown.notification.CountdownNotifications
import com.example.countdown.notification.DailyUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CountdownApp : Application(), Configuration.Provider {

    /** 应用级协程作用域：只用于启动期的轻量维护任务，不需要随界面销毁 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 通知渠道（Android 8.0+ 必须）
        CountdownNotifications.createChannels(this)

        // 注册每日更新任务：WorkManager 会持久化，重启后依然有效
        DailyUpdateScheduler.schedule(this)

        cleanUpLegacyDuplicates()
    }

    /**
     * 清理历史遗留的重复事件。
     *
     * 早期版本的保存按钮没有防重入，连点会插入多条完全相同的记录，
     * 用户看到的就是"列表里同一个事件出现两次"。写入侧现在已加防护，
     * 但已经产生的数据需要在启动时清一次。
     *
     * 放在后台协程里做，不阻塞启动；失败也不影响使用（只是重复数据留着）。
     */
    private fun cleanUpLegacyDuplicates() {
        appScope.launch {
            try {
                val removed = CountdownRepository.getInstance(this@CountdownApp).removeDuplicates()
                if (removed > 0) {
                    Log.i(TAG, "已清理 $removed 条重复事件")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "清理重复事件失败", t)
            }
        }
    }

    /**
     * 自定义 WorkManager 配置（对应 AndroidManifest 中移除的默认初始化）。
     * 使用默认 WorkerFactory 即可，这里保留扩展点。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        private const val TAG = "CountdownApp"
    }
}
