package com.expensetracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized date selection state manager shared across all screens.
 * Ensures consistent date/month selection across Dashboard, Classify, and Category Analysis.
 */
@Singleton
class DateSelectionManager @Inject constructor() {
    
    private val currentCalendar = Calendar.getInstance()
    
    private val _selectedYear = MutableStateFlow(currentCalendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    
    private val _selectedMonth = MutableStateFlow(currentCalendar.get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    
    private val _selectedDate = MutableStateFlow<Int?>(currentCalendar.get(Calendar.DAY_OF_MONTH))
    val selectedDate: StateFlow<Int?> = _selectedDate.asStateFlow()
    
    /**
     * Select a specific date within the current month, or null to show entire month
     */
    fun selectDate(date: Int?) {
        _selectedDate.value = date
    }
    
    /**
     * Navigate to the previous month and reset date selection to show entire month
     */
    fun goToPreviousMonth() {
        val calendar = Calendar.getInstance()
        calendar.set(_selectedYear.value, _selectedMonth.value, 1)
        calendar.add(Calendar.MONTH, -1)
        _selectedYear.value = calendar.get(Calendar.YEAR)
        _selectedMonth.value = calendar.get(Calendar.MONTH)
        _selectedDate.value = null // Reset to show entire month
    }
    
    /**
     * Navigate to the next month and reset date selection to show entire month
     */
    fun goToNextMonth() {
        val calendar = Calendar.getInstance()
        calendar.set(_selectedYear.value, _selectedMonth.value, 1)
        calendar.add(Calendar.MONTH, 1)
        _selectedYear.value = calendar.get(Calendar.YEAR)
        _selectedMonth.value = calendar.get(Calendar.MONTH)
        _selectedDate.value = null // Reset to show entire month
    }
}
