package com.example.countdown.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 迁移测试：确认 v1 -> v2 升级后**用户原有数据不会丢**，且新增列取到预期默认值。
 *
 * 这类测试是必须的：v1.0.0 已经发布，真实用户手机里存的就是 v1 的数据库。
 * 需要真机或模拟器：
 *     ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CountdownMigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CountdownDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsExistingEventsAndFillsDefaults() {
        // ---------- 1) 按 v1 的 schema 建库，塞一条"老用户"数据 ----------
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO events
                    (id, title, target_date, note, pinned, notify_enabled, created_at, updated_at)
                VALUES
                    (1, '发版纪念日', 20000, '升级前就存在的数据', 1, 1, 1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        // ---------- 2) 跑注册的迁移并让 Room 校验 schema ----------
        val db = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            *CountdownDatabase.MIGRATIONS
        )

        // ---------- 3) 旧数据必须还在，新列取默认值 ----------
        db.query(
            "SELECT id, title, target_date, mode, background_uri, background_dim FROM events"
        ).use { cursor ->
            assertEquals("迁移后应当仍然只有 1 条记录", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("发版纪念日", cursor.getString(1))
            assertEquals(20000L, cursor.getLong(2))
            assertEquals("新增列默认应为倒计日", "COUNTDOWN", cursor.getString(3))
            assertEquals(null, cursor.getString(4))
            assertEquals(0.35f, cursor.getFloat(5), 0.0001f)
        }

        // ---------- 4) 新列可写 ----------
        db.execSQL("UPDATE events SET mode = 'COUNTUP', background_uri = 'file:///bg.jpg' WHERE id = 1")
        db.query("SELECT mode, background_uri FROM events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("COUNTUP", cursor.getString(0))
            assertEquals("file:///bg.jpg", cursor.getString(1))
        }

        // ---------- 5) 索引也建出来了 ----------
        var indexFound = false
        db.query("PRAGMA index_list('events')").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "index_events_target_date") indexFound = true
            }
        }
        assertTrue("target_date 索引应当存在", indexFound)

        db.close()
    }
}
