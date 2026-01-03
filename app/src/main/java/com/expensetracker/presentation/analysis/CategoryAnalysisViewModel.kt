package com.expensetracker.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.repository.ExpenseRepository
import com.expensetracker.presentation.dashboard.CategoryExpense
import com.expensetracker.util.TimePeriod
import com.expensetracker.util.getTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class AnalysisUiState(
    val totalExpenses: Double = 0.0,
    val categoryExpenses: List<CategoryExpense> = emptyList(),
    val selectedPeriod: TimePeriod = TimePeriod.THIS_MONTH,
    val isLoading: Boolean = true
)

@HiltViewModel
class CategoryAnalysisViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    
    private val timeRange = _selectedPeriod.map { it.getTimeRange() }
    
    private val transactions = timeRange.flatMapLatest { (start, end) ->
        repository.getTransactionsByTimeRange(start, end)
    }
    
    private val categories = repository.getAllCategories()
    
    val uiState: StateFlow<AnalysisUiState> = combine(
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
        
        AnalysisUiState(
            totalExpenses = totalExpenses,
            categoryExpenses = categoryExpenses,
            selectedPeriod = period,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisUiState()
    )
    
    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }
}
