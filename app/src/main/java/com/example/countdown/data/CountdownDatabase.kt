package com.example.countdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CountdownEvent::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CountdownDatabase : RoomDatabase() {

    abstract fun countdownDao(): CountdownDao

    companion object {
        const val DATABASE_NAME = "countdown.db"

        @Volatile
        private var INSTANCE: CountdownDatabase? = null

        /**
         * v1 -> v2：加入正计日与自定义背景图。
         *
         * 关键点：用 ALTER TABLE ADD COLUMN 而不是重建表，这样已发布版本里用户
         * 保存的事件会原样保留。新增列都带默认值，旧数据读出来就是“倒计日 + 无背景”。
         *
         * 列类型/默认值必须与 CountdownEvent 的 @ColumnInfo(defaultValue = ...) 完全一致，
         * 否则 Room 打开数据库时的 schema 校验会失败。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE events ADD COLUMN mode TEXT NOT NULL DEFAULT 'COUNTDOWN'"
                )
                // 注意：这里不能写 "DEFAULT NULL" —— sqlite 会把默认值记成字符串 'NULL'，
                // 而 CountdownEvent.backgroundUri 没有声明 defaultValue，
                // 两边的 schema 就对不上，Room 打开数据库时会校验失败。
                db.execSQL(
                    "ALTER TABLE events ADD COLUMN background_uri TEXT"
                )
                db.execSQL(
                    "ALTER TABLE events ADD COLUMN background_dim REAL NOT NULL DEFAULT 0.35"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_events_target_date ON events(target_date)"
                )
            }
        }

        /**
         * 所有迁移都注册在这里。
         * internal 而非 private：androidTest 里的迁移测试需要直接引用它
         * （见 CountdownMigrationTest）。
         */
        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
