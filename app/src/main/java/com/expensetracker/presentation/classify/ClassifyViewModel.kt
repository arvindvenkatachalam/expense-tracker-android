package com.expensetracker.presentation.classify

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.repository.ExpenseRepository
import com.expensetracker.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ClassifyUiState(
    val uncategorizedTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedDate: Int? = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    val isLoading: Boolean = true
)

@HiltViewModel
class ClassifyViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val repository: ExpenseRepository
) : ViewModel() {

    // Current date as default
    private val currentCalendar = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    private val _selectedDate = MutableStateFlow<Int?>(currentCalendar.get(Calendar.DAY_OF_MONTH))
    
    private val _uiState = MutableStateFlow(ClassifyUiState())
    val uiState: StateFlow<ClassifyUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val timeRange = combine(_selectedYear, _selectedMonth, _selectedDate) { year, month, date ->
                if (date != null) {
                    DateUtils.getDateRange(year, month, date)
                } else {
                    DateUtils.getMonthRange(year, month)
                }
            }
            
            combine(
                timeRange.flatMapLatest { (start, end) ->
                    transactionDao.getUncategorizedTransactionsByTimeRange(start, end)
                },
                categoryDao.getAllCategories(),
                _selectedYear,
                _selectedMonth,
                _selectedDate
            ) { transactions, allCategories, year, month, date ->
                // Filter out "Others" category from the list
                val categoriesForClassification = allCategories.filter { 
                    !it.name.equals("Others", ignoreCase = true) 
                }
                ClassifyUiState(
                    uncategorizedTransactions = transactions,
                    categories = categoriesForClassification,
                    selectedYear = year,
                    selectedMonth = month,
                    selectedDate = date,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun categorizeTransaction(transaction: Transaction, category: Category) {
        viewModelScope.launch {
            // Fetch the latest transaction from DB to get current amount
            val freshTransaction = transactionDao.getTransactionById(transaction.id) ?: transaction
            val updatedTransaction = freshTransaction.copy(
                categoryId = category.id,
                isManuallyEdited = true
            )
            repository.updateTransaction(updatedTransaction)
        }
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
    
    fun updateTransactionAmount(transaction: Transaction, newAmount: Double) {
        viewModelScope.launch {
            Log.d("ClassifyViewModel", "Updating transaction ${transaction.id}: ${transaction.merchant} amount from ${transaction.amount} to $newAmount")
            val updatedTransaction = transaction.copy(
                amount = newAmount,
                isManuallyEdited = true
            )
            repository.updateTransaction(updatedTransaction)
            Log.d("ClassifyViewModel", "Transaction updated via repository")
        }
    }
    
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
}
