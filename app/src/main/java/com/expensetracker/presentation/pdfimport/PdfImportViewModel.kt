package com.expensetracker.presentation.pdfimport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.model.PdfTransaction
import com.expensetracker.domain.parser.HdfcStatementParser
import com.expensetracker.domain.parser.PdfParsingException
import com.expensetracker.domain.parser.PdfPasswordRequiredException
import com.expensetracker.domain.parser.PdfInvalidPasswordException
import com.expensetracker.domain.usecase.CategorizationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfImportViewModel @Inject constructor(
    private val pdfParser: HdfcStatementParser,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val categorizationEngine: CategorizationEngine
) : ViewModel() {
    
    companion object {
        private const val TAG = "PdfImportViewModel"
    }
    
    private val _state = MutableStateFlow(PdfImportState())
    val state: StateFlow<PdfImportState> = _state.asStateFlow()
    
    // Store PDF URI for password retry
    private var currentPdfUri: Uri? = null
    private var currentContext: Context? = null
    
    /**
     * Parse PDF file and extract transactions
     */
    fun parsePdf(context: Context, uri: Uri, password: String? = null) = viewModelScope.launch {
        try {
            Log.d(TAG, "Starting PDF parsing")
            _state.value = _state.value.copy(isLoading = true, error = null, showPasswordDialog = false, passwordError = null)
            
            // Store context and URI for password retry
            currentContext = context
            currentPdfUri = uri
            
            // Parse PDF
            val transactions = pdfParser.parsePdf(context, uri, password)
            
            if (transactions.isEmpty()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "No transactions found in PDF"
                )
                return@launch
            }
            
            // Apply categorization to each transaction
            val categorizedTransactions = transactions.map { pdfTx ->
                val categoryId = categorizationEngine.categorize(pdfTx.description)
                pdfTx.copy(suggestedCategoryId = categoryId)
            }
            
            // Detect duplicates
            val transactionsWithDuplicates = detectDuplicates(categorizedTransactions)
            
            _state.value = _state.value.copy(
                isLoading = false,
                pdfTransactions = transactionsWithDuplicates,
                selectedCount = transactionsWithDuplicates.count { it.isSelected },
                totalAmount = calculateTotalAmount(transactionsWithDuplicates)
            )
            
            Log.d(TAG, "Successfully parsed ${transactions.size} transactions")
            
        } catch (e: PdfPasswordRequiredException) {
            Log.d(TAG, "PDF requires password")
            _state.value = _state.value.copy(
                isLoading = false,
                showPasswordDialog = true,
                passwordError = null
            )
        } catch (e: PdfInvalidPasswordException) {
            Log.e(TAG, "Invalid password provided")
            _state.value = _state.value.copy(
                isLoading = false,
                showPasswordDialog = true,
                passwordError = e.message ?: "Incorrect password"
            )
        } catch (e: PdfParsingException) {
            Log.e(TAG, "PDF parsing failed", e)
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to parse PDF"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Unexpected error: ${e.message}"
            )
        }
    }
    
    /**
     * Submit password for encrypted PDF
     */
    fun submitPassword(password: String) {
        val context = currentContext
        val uri = currentPdfUri
        
        if (context != null && uri != null) {
            parsePdf(context, uri, password)
        } else {
            Log.e(TAG, "Cannot submit password: context or URI is null")
            _state.value = _state.value.copy(
                showPasswordDialog = false,
                error = "Failed to retry with password"
            )
        }
    }
    
    /**
     * Cancel password dialog
     */
    fun cancelPasswordDialog() {
        _state.value = _state.value.copy(
            showPasswordDialog = false,
            passwordError = null
        )
        currentContext = null
        currentPdfUri = null
    }
    
    
    /**
     * Toggle selection of a transaction
     */
    fun toggleTransaction(index: Int) {
        val transactions = _state.value.pdfTransactions.toMutableList()
        if (index in transactions.indices) {
            transactions[index] = transactions[index].copy(
                isSelected = !transactions[index].isSelected
            )
            _state.value = _state.value.copy(
                pdfTransactions = transactions,
                selectedCount = transactions.count { it.isSelected },
                totalAmount = calculateTotalAmount(transactions.filter { it.isSelected })
            )
        }
    }
    
    /**
     * Select all transactions
     */
    fun selectAll() {
        val transactions = _state.value.pdfTransactions.map { it.copy(isSelected = true) }
        _state.value = _state.value.copy(
            pdfTransactions = transactions,
            selectedCount = transactions.size,
            totalAmount = calculateTotalAmount(transactions)
        )
    }
    
    /**
     * Deselect all transactions
     */
    fun deselectAll() {
        val transactions = _state.value.pdfTransactions.map { it.copy(isSelected = false) }
        _state.value = _state.value.copy(
            pdfTransactions = transactions,
            selectedCount = 0,
            totalAmount = 0.0
        )
    }
    
    /**
     * Import selected transactions to database
     */
    fun importSelected() = viewModelScope.launch {
        try {
            Log.d(TAG, "Starting import of selected transactions")
            _state.value = _state.value.copy(isLoading = true)
            
            val selectedTransactions = _state.value.pdfTransactions.filter { it.isSelected }
            Log.d(TAG, "Selected ${selectedTransactions.size} transactions for import")
            
            selectedTransactions.forEachIndexed { index, pdfTx ->
                Log.d(TAG, "Importing transaction $index: ${pdfTx.description}, " +
                    "amount=${pdfTx.amount}, timestamp=${pdfTx.timestamp}, " +
                    "date=${java.util.Date(pdfTx.timestamp)}, type=${if (pdfTx.isDebit) "DEBIT" else "CREDIT"}")
                
                val transaction = Transaction(
                    amount = pdfTx.amount,
                    merchant = pdfTx.description,
                    categoryId = pdfTx.suggestedCategoryId ?: 7L, // Default to "Others"
                    timestamp = pdfTx.timestamp,
                    smsBody = "Imported from PDF",
                    bankName = "HDFC",
                    accountLast4 = "",
                    transactionType = if (pdfTx.isDebit) "DEBIT" else "CREDIT",
                    isManuallyEdited = false
                )
                
                val rowId = transactionDao.insertTransaction(transaction)
                Log.d(TAG, "Inserted transaction with row ID: $rowId")
            }
            
            // Verify insertion
            val allTransactions = transactionDao.getAllTransactionsDirect()
            Log.d(TAG, "Total transactions in DB after import: ${allTransactions.size}")
            if (allTransactions.isNotEmpty()) {
                Log.d(TAG, "Last 3 transactions in DB:")
                allTransactions.take(3).forEach { tx ->
                    Log.d(TAG, "  - ${tx.merchant}: ${tx.amount} at ${java.util.Date(tx.timestamp)} (${tx.transactionType})")
                }
            }
            
            Log.d(TAG, "Successfully imported ${selectedTransactions.size} transactions")
            
            // Store import details for success message
            _state.value = _state.value.copy(
                isLoading = false,
                importSuccess = true,
                importedCount = selectedTransactions.size,
                importMessage = "Imported ${selectedTransactions.size} transactions. Total in DB: ${allTransactions.size}"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Import failed: ${e.message}"
            )
        }
    }
    
    /**
     * Detect duplicate transactions
     */
    private suspend fun detectDuplicates(transactions: List<PdfTransaction>): List<PdfTransaction> {
        val existingTransactions = transactionDao.getAllTransactionsDirect()
        
        return transactions.map { pdfTx ->
            val isDuplicate = existingTransactions.any { existing ->
                // Check if same date, merchant, and amount
                Math.abs(existing.timestamp - pdfTx.timestamp) < 24 * 60 * 60 * 1000 && // Same day
                existing.merchant.equals(pdfTx.description, ignoreCase = true) &&
                Math.abs(existing.amount - pdfTx.amount) < 0.01 // Same amount
            }
            pdfTx.copy(isDuplicate = isDuplicate)
        }
    }
    
    /**
     * Calculate total amount of transactions
     */
    private fun calculateTotalAmount(transactions: List<PdfTransaction>): Double {
        return transactions.filter { it.isDebit }.sumOf { it.amount }
    }
    
    /**
     * Reset state
     */
    fun reset() {
        _state.value = PdfImportState()
    }
}

/**
 * State for PDF import screen
 */
data class PdfImportState(
    val isLoading: Boolean = false,
    val pdfTransactions: List<PdfTransaction> = emptyList(),
    val selectedCount: Int = 0,
    val totalAmount: Double = 0.0,
    val error: String? = null,
    val importSuccess: Boolean = false,
    val importedCount: Int = 0,
    val importMessage: String? = null,
    val showPasswordDialog: Boolean = false,
    val passwordError: String? = null
)
