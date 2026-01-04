package com.expensetracker.data.local.dao

import androidx.room.*
import com.expensetracker.data.local.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsDirect(): List<Transaction>
    
    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsByCategoryAndTimeRange(
        categoryId: Long,
        startTime: Long,
        endTime: Long
    ): Flow<List<Transaction>>
    
    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime AND transactionType = 'DEBIT'")
    fun getTotalExpensesByTimeRange(startTime: Long, endTime: Long): Flow<Double?>
    
    @Query("SELECT SUM(amount) FROM transactions WHERE categoryId = :categoryId AND timestamp BETWEEN :startTime AND :endTime AND transactionType = 'DEBIT'")
    fun getTotalExpensesByCategoryAndTimeRange(
        categoryId: Long,
        startTime: Long,
        endTime: Long
    ): Flow<Double?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long
    
    @Update
    suspend fun updateTransaction(transaction: Transaction)
    
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
    
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
    
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE c.name = 'Others'
        ORDER BY t.timestamp DESC
    """)
    fun getUncategorizedTransactions(): Flow<List<Transaction>>
}
