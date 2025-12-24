package com.expensetracker.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.RuleDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.MatchType
import com.expensetracker.data.local.entity.Rule
import com.expensetracker.domain.usecase.CategorizationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleDao: RuleDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val categorizationEngine: CategorizationEngine
) : ViewModel() {
    
    companion object {
        private const val TAG = "RulesViewModel"
    }
    
    val rules: StateFlow<List<Rule>> = ruleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun addRule(rule: Rule, recategorize: Boolean = false) = viewModelScope.launch {
        try {
            val id = ruleDao.insertRule(rule)
            Log.d(TAG, "Rule added with ID: $id")
            
            // Recategorize matching transactions if requested
            if (recategorize) {
                recategorizeMatchingTransactions(rule)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding rule", e)
        }
    }
    
    fun updateRule(rule: Rule, oldRule: Rule? = null) = viewModelScope.launch {
        try {
            ruleDao.updateRule(rule)
            Log.d(TAG, "Rule updated: ${rule.pattern}")
            
            // If category changed, recategorize all matching transactions
            if (oldRule != null && oldRule.categoryId != rule.categoryId) {
                recategorizeMatchingTransactions(rule)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating rule", e)
        }
    }
    
    fun deleteRule(rule: Rule) = viewModelScope.launch {
        try {
            ruleDao.deleteRule(rule)
            Log.d(TAG, "Rule deleted: ${rule.pattern}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting rule", e)
        }
    }
    
    suspend fun testPattern(merchant: String, pattern: String, matchType: MatchType): Boolean {
        return try {
            categorizationEngine.testRule(merchant, pattern, matchType)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing pattern", e)
            false
        }
    }
    
    suspend fun countMatchingTransactions(pattern: String, matchType: MatchType): Int {
        return try {
            Log.d(TAG, "Starting countMatchingTransactions for pattern: $pattern, matchType: $matchType")
            
            val allTransactions = withTimeoutOrNull(5000) {
                transactionDao.getAllTransactions().first()
            }
            
            if (allTransactions == null) {
                Log.e(TAG, "Timeout waiting for transactions from database")
                return 0
            }
            
            Log.d(TAG, "Total transactions in database: ${allTransactions.size}")
            allTransactions.forEachIndexed { index, tx ->
                Log.d(TAG, "Transaction $index: merchant='${tx.merchant}', category=${tx.categoryId}")
            }
            
            val matchingCount = allTransactions.count { transaction ->
                val matches = categorizationEngine.testRule(transaction.merchant, pattern, matchType)
                Log.d(TAG, "Testing '${transaction.merchant}' against pattern '$pattern' ($matchType): $matches")
                matches
            }
            
            Log.d(TAG, "Found $matchingCount matching transactions for pattern '$pattern' ($matchType)")
            matchingCount
        } catch (e: Exception) {
            Log.e(TAG, "Error counting matching transactions", e)
            e.printStackTrace()
            0
        }
    }
    
    private suspend fun recategorizeMatchingTransactions(rule: Rule) {
        try {
            // Get all transactions
            val allTransactions = transactionDao.getAllTransactions().first()
            
            // Find transactions that match this rule's pattern
            val matchingTransactions = allTransactions.filter { transaction ->
                categorizationEngine.testRule(transaction.merchant, rule.pattern, rule.matchType)
            }
            
            // Update category for matching transactions
            matchingTransactions.forEach { transaction ->
                val updated = transaction.copy(
                    categoryId = rule.categoryId,
                    isManuallyEdited = false  // Reset manual edit flag
                )
                transactionDao.updateTransaction(updated)
            }
            
            Log.d(TAG, "Recategorized ${matchingTransactions.size} transactions for rule: ${rule.pattern}")
        } catch (e: Exception) {
            Log.e(TAG, "Error recategorizing transactions", e)
        }
    }
}
