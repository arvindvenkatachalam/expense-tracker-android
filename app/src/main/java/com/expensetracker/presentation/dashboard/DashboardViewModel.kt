package com.expensetracker.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.repository.ExpenseRepository
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
    private val repository: ExpenseRepository
) : ViewModel() {
    
    // Current date as default
    private val currentCalendar = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    private val _selectedDate = MutableStateFlow<Int?>(currentCalendar.get(Calendar.DAY_OF_MONTH))
    private val _refreshTrigger = MutableStateFlow(0L)
    
    private val timeRange = combine(_selectedYear, _selectedMonth, _selectedDate) { year, month, date ->
        if (date != null) {
            DateUtils.getDateRange(year, month, date)
        } else {
            DateUtils.getMonthRange(year, month)
        }
    }
    
    private val transactions = combine(timeRange, _refreshTrigger) { range, _ ->
        range
    }.flatMapLatest { (start, end) ->
        repository.getTransactionsByTimeRange(start, end)
    }
    
    private val categories = repository.getAllCategories()
    
    val uiState: StateFlow<DashboardUiState> = combine(
        transactions,
        categories,
        _selectedYear,
        _selectedMonth,
        _selectedDate
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
        _selectedDate.value = date
    }
    
    fun goToPreviousMonth() {
        val calendar = Calendar.getInstance()
        calendar.set(_selectedYear.value, _selectedMonth.value, 1)
        calendar.add(Calendar.MONTH, -1)
        _selectedYear.value = calendar.get(Calendar.YEAR)
        _selectedMonth.value = calendar.get(Calendar.MONTH)
        _selectedDate.value = null // Reset to show entire month
    }
    
    fun goToNextMonth() {
        val calendar = Calendar.getInstance()
        calendar.set(_selectedYear.value, _selectedMonth.value, 1)
        calendar.add(Calendar.MONTH, 1)
        _selectedYear.value = calendar.get(Calendar.YEAR)
        _selectedMonth.value = calendar.get(Calendar.MONTH)
        _selectedDate.value = null // Reset to show entire month
    }
    
    /**
     * Force refresh by incrementing trigger to force Flow re-emission
     */
    fun forceRefresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }
    
    /**
     * Update a transaction's amount
     */
    fun updateTransactionAmount(transaction: Transaction, newAmount: Double) {
        viewModelScope.launch {
            val updatedTransaction = transaction.copy(amount = newAmount)
            repository.updateTransaction(updatedTransaction)
            forceRefresh()
        }
    }
    
    /**
     * Delete a transaction
     */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            forceRefresh()
        }
    }
}
