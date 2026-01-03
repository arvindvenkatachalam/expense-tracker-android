package com.expensetracker.presentation.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.MatchType
import com.expensetracker.data.local.entity.Rule
import com.expensetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleWithCategory(
    val rule: Rule,
    val category: Category?
)

data class RulesUiState(
    val rules: List<RuleWithCategory> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {
    
    val uiState: StateFlow<RulesUiState> = combine(
        repository.getAllRules(),
        repository.getAllCategories()
    ) { rules, categories ->
        val rulesWithCategories = rules.map { rule ->
            RuleWithCategory(
                rule = rule,
                category = categories.find { it.id == rule.categoryId }
            )
        }
        
        RulesUiState(
            rules = rulesWithCategories,
            categories = categories,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RulesUiState()
    )
    
    fun addRule(pattern: String, matchType: MatchType, categoryId: Long) {
        viewModelScope.launch {
            val rule = Rule(
                categoryId = categoryId,
                pattern = pattern,
                matchType = matchType,
                priority = 100,
                isActive = true
            )
            repository.insertRule(rule)
        }
    }
    
    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }
    
    fun toggleRuleActive(rule: Rule) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(isActive = !rule.isActive))
        }
    }
}
