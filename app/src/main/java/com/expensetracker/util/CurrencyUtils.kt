package com.expensetracker.util

import java.text.NumberFormat
import java.util.*

object CurrencyUtils {
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    
    init {
        currencyFormat.maximumFractionDigits = 2
        currencyFormat.minimumFractionDigits = 2
    }
    
    fun formatAmount(amount: Double): String {
        return currencyFormat.format(amount)
    }
    
    fun formatAmountWithoutSymbol(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }
}
