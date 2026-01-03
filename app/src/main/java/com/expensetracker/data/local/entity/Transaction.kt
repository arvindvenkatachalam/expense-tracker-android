package com.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val categoryId: Long?,
    val timestamp: Long,
    val smsBody: String,
    val bankName: String,
    val accountLast4: String?,
    val transactionType: String, // DEBIT/CREDIT
    val isManuallyEdited: Boolean = false
)
