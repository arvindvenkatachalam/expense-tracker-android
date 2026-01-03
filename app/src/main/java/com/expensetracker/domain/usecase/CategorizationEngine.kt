package com.expensetracker.domain.usecase

import android.util.Log
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.RuleDao
import com.expensetracker.data.local.entity.MatchType
import com.expensetracker.data.local.entity.Rule
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategorizationEngine @Inject constructor(
    private val ruleDao: RuleDao,
    private val categoryDao: CategoryDao
) {
    
    companion object {
        private const val TAG = "CategorizationEngine"
        private const val DEFAULT_CATEGORY_ID = 7L // "Others" category
    }
    
    suspend fun categorize(merchant: String): Long {
        try {
            Log.d(TAG, "Categorizing merchant: $merchant")
            
            // Get all active rules sorted by priority
            val rules = ruleDao.getActiveRules().first()
            
            // Find first matching rule
            for (rule in rules) {
                if (matchesRule(merchant, rule)) {
                    Log.d(TAG, "Matched rule: ${rule.pattern} -> category ${rule.categoryId}")
                    return rule.categoryId
                }
            }
            
            Log.d(TAG, "No matching rule found, using default category")
            return DEFAULT_CATEGORY_ID
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during categorization", e)
            return DEFAULT_CATEGORY_ID
        }
    }
    
    private fun matchesRule(merchant: String, rule: Rule): Boolean {
        val merchantUpper = merchant.uppercase()
        val patternUpper = rule.pattern.uppercase()
        
        return when (rule.matchType) {
            MatchType.CONTAINS -> merchantUpper.contains(patternUpper)
            MatchType.STARTS_WITH -> merchantUpper.startsWith(patternUpper)
            MatchType.ENDS_WITH -> merchantUpper.endsWith(patternUpper)
            MatchType.EXACT -> merchantUpper == patternUpper
            MatchType.REGEX -> {
                try {
                    val regex = Regex(rule.pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(merchant)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid regex pattern: ${rule.pattern}", e)
                    false
                }
            }
        }
    }
    
    suspend fun testRule(merchant: String, pattern: String, matchType: MatchType): Boolean {
        val testRule = Rule(
            id = 0,
            categoryId = 0,
            pattern = pattern,
            matchType = matchType,
            priority = 0,
            isActive = true
        )
        return matchesRule(merchant, testRule)
    }
}
