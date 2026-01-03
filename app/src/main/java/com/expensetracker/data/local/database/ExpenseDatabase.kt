package com.expensetracker.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.RuleDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.MatchType
import com.expensetracker.data.local.entity.Rule
import com.expensetracker.data.local.entity.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Transaction::class, Category::class, Rule::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    
    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null
        
        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.categoryDao(), database.ruleDao())
                    }
                }
            }
        }
        
        suspend fun populateDatabase(categoryDao: CategoryDao, ruleDao: RuleDao) {
            // Insert default categories
            val categories = listOf(
                Category(id = 1, name = "Food", color = "#FF9800", icon = "🍔", isDefault = true),
                Category(id = 2, name = "Transport", color = "#2196F3", icon = "🚗", isDefault = true),
                Category(id = 3, name = "Shopping", color = "#9C27B0", icon = "🛍️", isDefault = true),
                Category(id = 4, name = "Bills", color = "#F44336", icon = "💡", isDefault = true),
                Category(id = 5, name = "Entertainment", color = "#E91E63", icon = "🎬", isDefault = true),
                Category(id = 6, name = "Health", color = "#4CAF50", icon = "⚕️", isDefault = true),
                Category(id = 7, name = "Others", color = "#9E9E9E", icon = "📦", isDefault = true)
            )
            categoryDao.insertCategories(categories)
            
            // Insert default rules
            val rules = listOf(
                // Food
                Rule(categoryId = 1, pattern = "ZOMATO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "SWIGGY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "DOMINOS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "MCDONALDS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "KFC", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "PIZZA HUT", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "STARBUCKS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 1, pattern = "CAFE", matchType = MatchType.CONTAINS, priority = 90),
                Rule(categoryId = 1, pattern = "RESTAURANT", matchType = MatchType.CONTAINS, priority = 90),
                
                // Transport
                Rule(categoryId = 2, pattern = "UBER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "OLA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "RAPIDO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "PETROL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "FUEL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "PARKING", matchType = MatchType.CONTAINS, priority = 100),
                
                // Shopping
                Rule(categoryId = 3, pattern = "AMAZON", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "FLIPKART", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "MYNTRA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "AJIO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "MEESHO", matchType = MatchType.CONTAINS, priority = 100),
                
                // Bills
                Rule(categoryId = 4, pattern = "ELECTRICITY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "WATER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "INTERNET", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "MOBILE", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "RECHARGE", matchType = MatchType.CONTAINS, priority = 100),
                
                // Entertainment
                Rule(categoryId = 5, pattern = "NETFLIX", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "PRIME", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "HOTSTAR", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "SPOTIFY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "BOOKMYSHOW", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "MOVIE", matchType = MatchType.CONTAINS, priority = 90),
                
                // Health
                Rule(categoryId = 6, pattern = "PHARMACY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "HOSPITAL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "CLINIC", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "APOLLO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "MEDPLUS", matchType = MatchType.CONTAINS, priority = 100)
            )
            ruleDao.insertRules(rules)
        }
    }
}
