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
