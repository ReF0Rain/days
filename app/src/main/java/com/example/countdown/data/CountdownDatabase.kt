package com.example.countdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                // 版本升级时在这里添加 Migration，例如：
                // .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
