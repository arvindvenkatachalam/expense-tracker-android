package com.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val pattern: String,        // e.g., "ZOMATO", "SWIGGY"
    val matchType: MatchType,   // CONTAINS, STARTS_WITH, ENDS_WITH, EXACT
    val priority: Int = 0,      // Higher priority rules checked first
    val isActive: Boolean = true
)

enum class MatchType {
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    EXACT,
    REGEX
}
