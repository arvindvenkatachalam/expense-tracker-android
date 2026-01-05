package com.expensetracker.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.repository.ExpenseRepository
import com.expensetracker.util.DateSelectionManager
import com.expensetracker.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CategoryExpense(
    val category: Category,
    val totalAmount: Double,
    val percentage: Float,
    val transactionCount: Int
)

data class DashboardUiState(
    val totalExpenses: Double = 0.0,
    val categoryExpenses: List<CategoryExpense> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedDate: Int? = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val dateSelectionManager: DateSelectionManager
) : ViewModel() {
    
    private val timeRange = combine(
        dateSelectionManager.selectedYear,
        dateSelectionManager.selectedMonth,
        dateSelectionManager.selectedDate
    ) { year, month, date ->
        if (date != null) {
            DateUtils.getDateRange(year, month, date)
        } else {
            DateUtils.getMonthRange(year, month)
        }
    }
    
    private val transactions = timeRange.flatMapLatest { (start, end) ->
        repository.getTransactionsByTimeRange(start, end)
    }
    
    private val categories = repository.getAllCategories()
    
    val uiState: StateFlow<DashboardUiState> = combine(
        transactions,
        categories,
        dateSelectionManager.selectedYear,
        dateSelectionManager.selectedMonth,
        dateSelectionManager.selectedDate
    ) { trans, cats, year, month, date ->
        val debitTransactions = trans.filter { it.transactionType == "DEBIT" }
        val totalExpenses = debitTransactions.sumOf { it.amount }
        
        val categoryExpenses = cats.mapNotNull { category ->
            val categoryTrans = debitTransactions.filter { it.categoryId == category.id }
            val categoryTotal = categoryTrans.sumOf { it.amount }
            
            if (categoryTotal > 0) {
                CategoryExpense(
                    category = category,
                    totalAmount = categoryTotal,
                    percentage = if (totalExpenses > 0) (categoryTotal / totalExpenses * 100).toFloat() else 0f,
                    transactionCount = categoryTrans.size
                )
            } else {
                null
            }
        }.sortedByDescending { it.totalAmount }
        
        DashboardUiState(
            totalExpenses = totalExpenses,
            categoryExpenses = categoryExpenses,
            recentTransactions = debitTransactions,
            selectedYear = year,
            selectedMonth = month,
            selectedDate = date,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
    
    
    fun selectDate(date: Int?) {
        dateSelectionManager.selectDate(date)
    }
    
    fun goToPreviousMonth() {
        dateSelectionManager.goToPreviousMonth()
    }
    
    fun goToNextMonth() {
        dateSelectionManager.goToNextMonth()
    }
    
    /**
     * Update a transaction's amount
     */
    fun updateTransactionAmount(transaction: Transaction, newAmount: Double) {
        viewModelScope.launch {
            val updatedTransaction = transaction.copy(amount = newAmount)
            repository.updateTransaction(updatedTransaction)
        }
    }
    
    /**
     * Delete a transaction
     */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
}
