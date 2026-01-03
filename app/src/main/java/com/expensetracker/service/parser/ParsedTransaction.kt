package com.expensetracker.service.parser

data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val transactionType: TransactionType,
    val timestamp: Long,
    val accountLast4: String?,
    val bankName: String,
    val rawSms: String
)

enum class TransactionType {
    DEBIT,
    CREDIT,
    UNKNOWN
}
