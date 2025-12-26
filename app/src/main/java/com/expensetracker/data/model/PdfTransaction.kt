package com.expensetracker.data.model

/**
 * Represents a transaction extracted from a PDF bank statement
 * This is a temporary model used during the import process before converting to Transaction entity
 */
data class PdfTransaction(
    val date: String,                    // Original date string from PDF (e.g., "01/12/2024")
    val timestamp: Long,                 // Parsed timestamp in milliseconds
    val description: String,             // Transaction description/merchant name
    val debit: Double?,                  // Debit amount (null if credit transaction)
    val credit: Double?,                 // Credit amount (null if debit transaction)
    val balance: Double,                 // Account balance after this transaction
    val isSelected: Boolean = true,      // For manual review - user can deselect
    val suggestedCategoryId: Long? = null, // Auto-categorized based on rules
    val isDuplicate: Boolean = false     // Flag for potential duplicate transactions
) {
    /**
     * Get the transaction amount (debit or credit)
     */
    val amount: Double
        get() = debit ?: credit ?: 0.0
    
    /**
     * Get the transaction type
     */
    val type: TransactionType
        get() = if (debit != null) TransactionType.DEBIT else TransactionType.CREDIT
    
    /**
     * Check if this is a debit (expense) transaction
     */
    val isDebit: Boolean
        get() = debit != null
}

enum class TransactionType {
    DEBIT,
    CREDIT
}
