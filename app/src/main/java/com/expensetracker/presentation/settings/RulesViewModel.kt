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
import kotlinx.coroutines.flow.MutableStateFlow
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
    
    private val _recategorizeMessage = MutableStateFlow<String?>(null)
    val recategorizeMessage: StateFlow<String?> = _recategorizeMessage.asStateFlow()
    
    fun addRule(rule: Rule, recategorize: Boolean = true) = viewModelScope.launch {
        try {
            val id = ruleDao.insertRule(rule)
            Log.d(TAG, "Rule added with ID: $id")
            
            // Recategorize matching transactions if requested
            if (recategorize) {
                val count = recategorizeMatchingTransactions(rule)
                if (count > 0) {
                    _recategorizeMessage.value = "Recategorized $count transaction${if (count == 1) "" else "s"}"
                }
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
            
            // Recategorize all transactions to reapply remaining rules
            recategorizeAllTransactions()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting rule", e)
        }
    }
    
    fun clearRecategorizeMessage() {
        _recategorizeMessage.value = null
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
        Log.d(TAG, "=== COUNT MATCHING TRANSACTIONS START ===")
        Log.d(TAG, "Pattern: '$pattern' (length: ${pattern.length})")
        Log.d(TAG, "MatchType: $matchType")
        
        // First, let's verify the method is being called
        val allTransactions = transactionDao.getAllTransactionsDirect()
        
        Log.d(TAG, "Retrieved ${allTransactions.size} transactions from database")
        
        if (allTransactions.isEmpty()) {
            Log.e(TAG, "ERROR: No transactions found in database!")
            return 0
        }
        
        // Log ALL transactions with their merchants
        allTransactions.forEachIndexed { index, tx ->
            Log.d(TAG, "TX[$index]: merchant='${tx.merchant}' | categoryId=${tx.categoryId} | id=${tx.id}")
        }
        
        // Now test each transaction
        var matchCount = 0
        allTransactions.forEach { transaction ->
            val matches = categorizationEngine.testRule(transaction.merchant, pattern, matchType)
            
            if (matches) {
                matchCount++
                Log.d(TAG, "✓✓✓ MATCH FOUND: '${transaction.merchant}' matches '$pattern' ($matchType)")
            } else {
                Log.d(TAG, "✗ No match: '${transaction.merchant}' vs '$pattern' ($matchType)")
            }
        }
        
        Log.d(TAG, "FINAL COUNT: $matchCount matching transactions found")
        Log.d(TAG, "=== COUNT MATCHING TRANSACTIONS END ===")
        
        matchCount
    } catch (e: Exception) {
        Log.e(TAG, "ERROR in countMatchingTransactions: ${e.message}", e)
        e.printStackTrace()
        0
    }
}
    
    private suspend fun recategorizeMatchingTransactions(rule: Rule): Int {
    try {
        Log.d(TAG, "=== RECATEGORIZE MATCHING TRANSACTIONS START ===")
        Log.d(TAG, "Rule pattern: '${rule.pattern}', matchType: ${rule.matchType}, targetCategory: ${rule.categoryId}")
        
        // Get all transactions using direct query
        val allTransactions = transactionDao.getAllTransactionsDirect()
        Log.d(TAG, "Retrieved ${allTransactions.size} total transactions")
        
        // Find transactions that match this rule's pattern
        val matchingTransactions = allTransactions.filter { transaction ->
            val matches = categorizationEngine.testRule(transaction.merchant, rule.pattern, rule.matchType)
            if (matches) {
                Log.d(TAG, "Found matching transaction: id=${transaction.id}, merchant='${transaction.merchant}', currentCategory=${transaction.categoryId}")
            }
            matches
        }
        
        Log.d(TAG, "Found ${matchingTransactions.size} transactions to recategorize")
        
        // Update category for matching transactions
        var updateCount = 0
        matchingTransactions.forEach { transaction ->
            val updated = transaction.copy(
                categoryId = rule.categoryId,
                isManuallyEdited = false  // Reset manual edit flag
            )
            transactionDao.updateTransaction(updated)
            updateCount++
            Log.d(TAG, "Updated transaction ${transaction.id}: ${transaction.categoryId} -> ${rule.categoryId}")
        }
        
        Log.d(TAG, "Successfully recategorized $updateCount transactions for rule: ${rule.pattern}")
        Log.d(TAG, "=== RECATEGORIZE MATCHING TRANSACTIONS END ===")
        return updateCount
    } catch (e: Exception) {
        Log.e(TAG, "Error recategorizing transactions: ${e.message}", e)
        e.printStackTrace()
        return 0
    }
    }
    
    /**
     * Recategorize all transactions using current rules
     */
    suspend fun recategorizeAllTransactions() {
        try {
            Log.d(TAG, "=== RECATEGORIZE ALL TRANSACTIONS START ===")
            
            // Get all transactions
            val allTransactions = transactionDao.getAllTransactionsDirect()
            Log.d(TAG, "Retrieved ${allTransactions.size} total transactions")
            
            var recategorizedCount = 0
            
            allTransactions.forEach { transaction ->
                // Skip manually edited transactions
                if (!transaction.isManuallyEdited) {
                    val newCategory = categorizationEngine.categorize(transaction.merchant)
                    
                    if (newCategory != transaction.categoryId) {
                        val updated = transaction.copy(categoryId = newCategory)
                        transactionDao.updateTransaction(updated)
                        recategorizedCount++
                        Log.d(TAG, "Recategorized transaction ${transaction.id}: ${transaction.categoryId} -> $newCategory")
                    }
                }
            }
            
            Log.d(TAG, "Successfully recategorized $recategorizedCount transactions")
            Log.d(TAG, "=== RECATEGORIZE ALL TRANSACTIONS END ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error recategorizing all transactions: ${e.message}", e)
            e.printStackTrace()
        }
    }
}
