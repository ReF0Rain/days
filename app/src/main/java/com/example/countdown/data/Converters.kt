package com.example.countdown.data

import androidx.room.TypeConverter

/**
 * Room 类型转换器。
 * 枚举统一以字符串入库：可读、可迁移、不依赖序号（改枚举顺序不会破坏旧数据）。
 */
class Converters {

    @TypeConverter
    fun modeToString(mode: CountdownMode?): String? = mode?.name

    @TypeConverter
    fun stringToMode(value: String?): CountdownMode = CountdownMode.fromStorage(value)
}
