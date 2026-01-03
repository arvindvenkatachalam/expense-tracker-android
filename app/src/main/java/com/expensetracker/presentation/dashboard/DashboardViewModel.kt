package com.expensetracker.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.repository.ExpenseRepository
import com.expensetracker.util.TimePeriod
import com.expensetracker.util.getTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val selectedPeriod: TimePeriod = TimePeriod.THIS_MONTH,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    private val _refreshTrigger = MutableStateFlow(0L)
    
    private val timeRange = _selectedPeriod.map { it.getTimeRange() }
    
    private val transactions = combine(timeRange, _refreshTrigger) { range, _ ->
        range
    }.flatMapLatest { (start, end) ->
        repository.getTransactionsByTimeRange(start, end)
    }
    
    private val categories = repository.getAllCategories()
    
    val uiState: StateFlow<DashboardUiState> = combine(
        transactions,
        categories,
        _selectedPeriod
    ) { trans, cats, period ->
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
            recentTransactions = debitTransactions.take(5),
            selectedPeriod = period,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
    
    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }
    
    /**
     * Force refresh by incrementing trigger to force Flow re-emission
     */
    fun forceRefresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }
}
