package com.expensetracker.presentation.theme

import androidx.compose.ui.graphics.Color

// Material 3 Theme Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Category Colors
val CategoryFood = Color(0xFFFF9800)
val CategoryTransport = Color(0xFF2196F3)
val CategoryShopping = Color(0xFF9C27B0)
val CategoryBills = Color(0xFFF44336)
val CategoryEntertainment = Color(0xFFE91E63)
val CategoryHealth = Color(0xFF4CAF50)
val CategoryOthers = Color(0xFF9E9E9E)
val CategoryDonation = Color(0xFF00BCD4)
val CategorySnacks = Color(0xFFFFEB3B)

fun getCategoryColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        CategoryOthers
    }
}
