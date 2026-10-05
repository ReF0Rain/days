package com.example.countdown.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    /** 按目标日期升序（即剩余天数从少到多）返回全部事件 */
    @Query("SELECT * FROM events ORDER BY target_date ASC, id ASC")
    fun observeAll(): Flow<List<CountdownEvent>>

    /** 供 Worker / 小组件一次性读取 */
    @Query("SELECT * FROM events ORDER BY target_date ASC, id ASC")
    suspend fun getAllOnce(): List<CountdownEvent>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CountdownEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CountdownEvent): Long

    @Update
    suspend fun update(event: CountdownEvent)

    @Delete
    suspend fun delete(event: CountdownEvent)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    /**
     * 找出重复事件的 id：同标题 + 同日期 + 同模式视为重复，每组保留 id 最小的那条。
     *
     * 用途：早期版本的保存按钮没有防重入，连点会插入多条完全相同的记录，
     * 用户看到的就是"列表里同一个事件出现两次"。启动时清一次。
     */
    @Query(
        """
        SELECT id FROM events
        WHERE id NOT IN (
            SELECT MIN(id) FROM events GROUP BY title, target_date, mode
        )
        """
    )
    suspend fun findDuplicateIds(): List<Long>

    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int
}
