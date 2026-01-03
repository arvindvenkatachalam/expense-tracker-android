package com.expensetracker.data.repository

import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.RuleDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Rule
import com.expensetracker.data.local.entity.Transaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao
) {
    
    // Transaction operations
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId)
    
    fun getTransactionsByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByTimeRange(startTime, endTime)
    
    fun getTotalExpensesByTimeRange(startTime: Long, endTime: Long): Flow<Double?> =
        transactionDao.getTotalExpensesByTimeRange(startTime, endTime)
    
    fun getTotalExpensesByCategoryAndTimeRange(
        categoryId: Long,
        startTime: Long,
        endTime: Long
    ): Flow<Double?> =
        transactionDao.getTotalExpensesByCategoryAndTimeRange(categoryId, startTime, endTime)
    
    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insertTransaction(transaction)
    
    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.updateTransaction(transaction)
    
    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.deleteTransaction(transaction)
    
    suspend fun deleteAllTransactions() =
        transactionDao.deleteAllTransactions()
    
    // Category operations
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()
    
    suspend fun getCategoryById(categoryId: Long): Category? =
        categoryDao.getCategoryById(categoryId)
    
    suspend fun getCategoryByName(name: String): Category? =
        categoryDao.getCategoryByName(name)
    
    suspend fun insertCategory(category: Category): Long =
        categoryDao.insertCategory(category)
    
    suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(category)
    
    suspend fun deleteCategory(category: Category) =
        categoryDao.deleteCategory(category)
    
    // Rule operations
    fun getAllRules(): Flow<List<Rule>> = ruleDao.getAllRules()
    
    fun getActiveRules(): Flow<List<Rule>> = ruleDao.getActiveRules()
    
    fun getRulesByCategory(categoryId: Long): Flow<List<Rule>> =
        ruleDao.getRulesByCategory(categoryId)
    
    suspend fun insertRule(rule: Rule): Long =
        ruleDao.insertRule(rule)
    
    suspend fun updateRule(rule: Rule) =
        ruleDao.updateRule(rule)
    
    suspend fun updateRules(rules: List<Rule>) =
        ruleDao.updateRules(rules)
    
    suspend fun deleteRule(rule: Rule) =
        ruleDao.deleteRule(rule)
    
    suspend fun deleteAllRules() =
        ruleDao.deleteAllRules()
}
