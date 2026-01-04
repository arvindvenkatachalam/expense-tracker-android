package com.expensetracker.presentation.classify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClassifyUiState(
    val uncategorizedTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ClassifyViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClassifyUiState())
    val uiState: StateFlow<ClassifyUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                transactionDao.getUncategorizedTransactions(),
                categoryDao.getAllCategories()
            ) { transactions, allCategories ->
                // Filter out "Others" category from the list
                val categoriesForClassification = allCategories.filter { 
                    !it.name.equals("Others", ignoreCase = true) 
                }
                ClassifyUiState(
                    uncategorizedTransactions = transactions,
                    categories = categoriesForClassification,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun categorizeTransaction(transaction: Transaction, category: Category) {
        viewModelScope.launch {
            val updatedTransaction = transaction.copy(
                categoryId = category.id,
                isManuallyEdited = true
            )
            transactionDao.updateTransaction(updatedTransaction)
        }
    }
}
