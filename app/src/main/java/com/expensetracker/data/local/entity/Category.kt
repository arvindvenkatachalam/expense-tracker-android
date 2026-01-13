package com.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String, // Hex color code
    val icon: String,  // Material icon name or emoji
    val isDefault: Boolean = false,
    val displayOrder: Int = 0 // Order for display in UI
)
