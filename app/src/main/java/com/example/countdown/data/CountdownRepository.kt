package com.example.countdown.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 单例仓库：UI / ViewModel / Worker / 小组件统一通过它访问数据库，
 * 便于以后替换数据源（例如加缓存或同步到云端）。
 */
class CountdownRepository private constructor(private val dao: CountdownDao) {

    fun observeAll(): Flow<List<CountdownEvent>> = dao.observeAll()

    suspend fun getAllOnce(): List<CountdownEvent> = dao.getAllOnce()

    suspend fun getById(id: Long): CountdownEvent? = dao.getById(id)

    suspend fun insert(event: CountdownEvent): Long = dao.insert(event)

    suspend fun update(event: CountdownEvent) = dao.update(event)

    suspend fun delete(event: CountdownEvent) = dao.delete(event)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    /**
     * 清理重复事件（同标题 + 同日期 + 同模式），每组保留 id 最小的那条。
     * 返回被删除的条数。
     *
     * 存在的原因：早期版本的保存按钮没有防重入，连点会插入多条相同记录。
     * 写入侧现在已加防护，但历史产生的重复数据需要在启动时清一次。
     */
    suspend fun removeDuplicates(): Int {
        val ids = dao.findDuplicateIds()
        if (ids.isEmpty()) return 0
        return dao.deleteByIds(ids)
    }

    companion object {
        @Volatile
        private var INSTANCE: CountdownRepository? = null

        fun getInstance(context: Context): CountdownRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CountdownRepository(
                    CountdownDatabase.getInstance(context).countdownDao()
                ).also { INSTANCE = it }
            }
    }
}
