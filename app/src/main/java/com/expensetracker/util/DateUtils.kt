package com.expensetracker.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    
    fun getTodayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return Pair(startOfDay, endOfDay)
    }
    
    fun getThisWeekRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfWeek = calendar.timeInMillis
        
        return Pair(startOfWeek, endOfWeek)
    }
    
    fun getThisMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonth = calendar.timeInMillis
        
        return Pair(startOfMonth, endOfMonth)
    }
    
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Get all dates in a given month
     */
    fun getDaysInMonth(year: Int, month: Int): List<Int> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..daysInMonth).toList()
    }
    
    /**
     * Format month and year (e.g., "January 2024")
     */
    fun formatMonthYear(year: Int, month: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }
    
    /**
     * Get time range for a specific month
     */
    fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonth = calendar.timeInMillis
        
        return Pair(startOfMonth, endOfMonth)
    }
    
    /**
     * Get time range for a specific date
     */
    fun getDateRange(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return Pair(startOfDay, endOfDay)
    }
}

enum class TimePeriod {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

fun TimePeriod.getTimeRange(): Pair<Long, Long> {
    val range = when (this) {
        TimePeriod.TODAY -> DateUtils.getTodayRange()
        TimePeriod.THIS_WEEK -> DateUtils.getThisWeekRange()
        TimePeriod.THIS_MONTH -> DateUtils.getThisMonthRange()
        TimePeriod.CUSTOM -> Pair(0L, System.currentTimeMillis())
    }
    android.util.Log.d("TimePeriod", "$this range: ${java.util.Date(range.first)} to ${java.util.Date(range.second)}")
    return range
}
