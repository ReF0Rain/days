package com.example.countdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [CountdownEvent::class],
    version = 1,
    exportSchema = true
)
abstract class CountdownDatabase : RoomDatabase() {

    abstract fun countdownDao(): CountdownDao

    companion object {
        const val DATABASE_NAME = "countdown.db"

        @Volatile
        private var INSTANCE: CountdownDatabase? = null

        /**
         * 所有迁移都注册在这里。
         *
         * 这里刻意不用 fallbackToDestructiveMigration()：那会在版本升级时直接删库重建，
         * 用户保存的倒计日事件会全部丢失。宁可让启动时明确报错，也不要静默丢数据。
         *
         * 以后加字段/改表结构时的步骤（假设升到 version 2）：
         *   1) 把上面的 version 改成 2
         *   2) 在下面加一个迁移对象，例如：
         *
         *        private val MIGRATION_1_2 = object : Migration(1, 2) {
         *            override fun migrate(db: SupportSQLiteDatabase) {
         *                db.execSQL("ALTER TABLE events ADD COLUMN color_tag TEXT")
         *            }
         *        }
         *
         *   3) 把它加进 MIGRATIONS
         *   4) 补一个迁移测试（可参考 app/schemas/ 下导出的 schema JSON），
         *      确认升级后旧数据还在、表结构正确
         */
        private val MIGRATIONS: Array<Migration> = emptyArray()

        fun getInstance(context: Context): CountdownDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): CountdownDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CountdownDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
