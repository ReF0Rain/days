package com.example.countdown

import android.app.Application
import androidx.work.Configuration
import com.example.countdown.notification.CountdownNotifications
import com.example.countdown.notification.DailyUpdateScheduler

class CountdownApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // 通知渠道（Android 8.0+ 必须）
        CountdownNotifications.createChannels(this)

        // 注册每日更新任务：WorkManager 会持久化，重启后依然有效
        DailyUpdateScheduler.schedule(this)
    }

    /**
     * 自定义 WorkManager 配置（对应 AndroidManifest 中移除的默认初始化）。
     * 使用默认 WorkerFactory 即可，这里保留扩展点。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
