package com.expensetracker.data.local.database

import androidx.room.TypeConverter
import com.expensetracker.data.local.entity.MatchType

class Converters {
    
    @TypeConverter
    fun fromMatchType(value: MatchType): String {
        return value.name
    }
    
    @TypeConverter
    fun toMatchType(value: String): MatchType {
        return MatchType.valueOf(value)
    }
}
