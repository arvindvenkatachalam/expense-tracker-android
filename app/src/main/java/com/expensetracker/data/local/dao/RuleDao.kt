package com.expensetracker.data.local.dao

import androidx.room.*
import com.expensetracker.data.local.entity.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    
    @Query("SELECT * FROM rules WHERE isActive = 1 ORDER BY priority DESC")
    fun getActiveRules(): Flow<List<Rule>>
    
    @Query("SELECT * FROM rules ORDER BY priority DESC")
    fun getAllRules(): Flow<List<Rule>>
    
    @Query("SELECT * FROM rules WHERE categoryId = :categoryId ORDER BY priority DESC")
    fun getRulesByCategory(categoryId: Long): Flow<List<Rule>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: Rule): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<Rule>)
    
    @Update
    suspend fun updateRule(rule: Rule)
    
    @Update
    suspend fun updateRules(rules: List<Rule>)
    
    @Delete
    suspend fun deleteRule(rule: Rule)
    
    @Query("SELECT * FROM rules ORDER BY priority DESC")
    suspend fun getAllRulesDirect(): List<Rule>
    
    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()
}
