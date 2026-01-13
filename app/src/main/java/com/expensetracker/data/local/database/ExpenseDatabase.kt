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
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add displayOrder column to categories table
                database.execSQL("ALTER TABLE categories ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
                
                // Update displayOrder for existing categories based on desired order
                // Food=1, Bills=2, Snacks=3, Entertainment=4, Shopping=5, Transport=6, Health=7, Donation=8, Others=9
                database.execSQL("UPDATE categories SET displayOrder = 1 WHERE name = 'Food'")
                database.execSQL("UPDATE categories SET displayOrder = 2 WHERE name = 'Bills'")
                database.execSQL("UPDATE categories SET displayOrder = 3 WHERE name = 'Snacks'")
                database.execSQL("UPDATE categories SET displayOrder = 4 WHERE name = 'Entertainment'")
                database.execSQL("UPDATE categories SET displayOrder = 5 WHERE name = 'Shopping'")
                database.execSQL("UPDATE categories SET displayOrder = 6 WHERE name = 'Transport'")
                database.execSQL("UPDATE categories SET displayOrder = 7 WHERE name = 'Health'")
                database.execSQL("UPDATE categories SET displayOrder = 8 WHERE name = 'Donation'")
                database.execSQL("UPDATE categories SET displayOrder = 9 WHERE name = 'Others'")
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
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Check and add missing categories/rules for existing databases
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        ensureCategoriesAndRules(database.categoryDao(), database.ruleDao())
                    }
                }
            }
        }
        
        suspend fun populateDatabase(categoryDao: CategoryDao, ruleDao: RuleDao) {
            // Insert default categories
            val categories = listOf(
                Category(id = 1, name = "Food", color = "#FF9800", icon = "🍔", isDefault = true, displayOrder = 1),
                Category(id = 2, name = "Bills", color = "#F44336", icon = "💡", isDefault = true, displayOrder = 2),
                Category(id = 3, name = "Snacks", color = "#FFEB3B", icon = "🍿", isDefault = true, displayOrder = 3),
                Category(id = 4, name = "Entertainment", color = "#E91E63", icon = "🎬", isDefault = true, displayOrder = 4),
                Category(id = 5, name = "Shopping", color = "#9C27B0", icon = "🛍️", isDefault = true, displayOrder = 5),
                Category(id = 6, name = "Transport", color = "#2196F3", icon = "🚗", isDefault = true, displayOrder = 6),
                Category(id = 7, name = "Health", color = "#4CAF50", icon = "⚕️", isDefault = true, displayOrder = 7),
                Category(id = 8, name = "Donation", color = "#00BCD4", icon = "❤️", isDefault = true, displayOrder = 8),
                Category(id = 9, name = "Others", color = "#9E9E9E", icon = "📦", isDefault = true, displayOrder = 9)
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
                
                // Bills
                Rule(categoryId = 2, pattern = "ELECTRICITY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "WATER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "INTERNET", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "MOBILE", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "RECHARGE", matchType = MatchType.CONTAINS, priority = 100),
                
                // Snacks
                Rule(categoryId = 3, pattern = "SNACKS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "CHIPS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "BISCUIT", matchType = MatchType.CONTAINS, priority = 100),
                
                // Entertainment
                Rule(categoryId = 4, pattern = "NETFLIX", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "PRIME", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "HOTSTAR", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "SPOTIFY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "BOOKMYSHOW", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "MOVIE", matchType = MatchType.CONTAINS, priority = 90),
                
                // Shopping
                Rule(categoryId = 5, pattern = "AMAZON", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "FLIPKART", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "MYNTRA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "AJIO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "MEESHO", matchType = MatchType.CONTAINS, priority = 100),
                
                // Transport
                Rule(categoryId = 6, pattern = "UBER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "OLA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "RAPIDO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "PETROL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "FUEL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "PARKING", matchType = MatchType.CONTAINS, priority = 100),
                
                // Health
                Rule(categoryId = 7, pattern = "PHARMACY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "HOSPITAL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "CLINIC", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "APOLLO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "MEDPLUS", matchType = MatchType.CONTAINS, priority = 100),
                
                // Donation
                Rule(categoryId = 8, pattern = "DONATION", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 8, pattern = "CHARITY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 8, pattern = "NGO", matchType = MatchType.CONTAINS, priority = 100)
            )
            ruleDao.insertRules(rules)
        }
        
        suspend fun ensureCategoriesAndRules(categoryDao: CategoryDao, ruleDao: RuleDao) {
            // Get existing categories
            val existingCategories = categoryDao.getAllCategoriesDirect()
            val existingCategoryIds = existingCategories.map { it.id }.toSet()
            
            // Define all categories that should exist
            val allCategories = listOf(
                Category(id = 1, name = "Food", color = "#FF9800", icon = "🍔", isDefault = true, displayOrder = 1),
                Category(id = 2, name = "Bills", color = "#F44336", icon = "💡", isDefault = true, displayOrder = 2),
                Category(id = 3, name = "Snacks", color = "#FFEB3B", icon = "🍿", isDefault = true, displayOrder = 3),
                Category(id = 4, name = "Entertainment", color = "#E91E63", icon = "🎬", isDefault = true, displayOrder = 4),
                Category(id = 5, name = "Shopping", color = "#9C27B0", icon = "🛍️", isDefault = true, displayOrder = 5),
                Category(id = 6, name = "Transport", color = "#2196F3", icon = "🚗", isDefault = true, displayOrder = 6),
                Category(id = 7, name = "Health", color = "#4CAF50", icon = "⚕️", isDefault = true, displayOrder = 7),
                Category(id = 8, name = "Donation", color = "#00BCD4", icon = "❤️", isDefault = true, displayOrder = 8),
                Category(id = 9, name = "Others", color = "#9E9E9E", icon = "📦", isDefault = true, displayOrder = 9)
            )
            
            // Insert missing categories
            val missingCategories = allCategories.filter { it.id !in existingCategoryIds }
            if (missingCategories.isNotEmpty()) {
                categoryDao.insertCategories(missingCategories)
            }
            
            // Get existing rules
            val existingRules = ruleDao.getAllRulesDirect()
            val existingRulePatterns = existingRules.map { "${it.categoryId}_${it.pattern}" }.toSet()
            
            // Define all default rules
            val allRules = listOf(
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
                
                // Bills
                Rule(categoryId = 2, pattern = "ELECTRICITY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "WATER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "INTERNET", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "MOBILE", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 2, pattern = "RECHARGE", matchType = MatchType.CONTAINS, priority = 100),
                
                // Snacks
                Rule(categoryId = 3, pattern = "SNACKS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "CHIPS", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 3, pattern = "BISCUIT", matchType = MatchType.CONTAINS, priority = 100),
                
                // Entertainment
                Rule(categoryId = 4, pattern = "NETFLIX", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "PRIME", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "HOTSTAR", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "SPOTIFY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "BOOKMYSHOW", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 4, pattern = "MOVIE", matchType = MatchType.CONTAINS, priority = 90),
                
                // Shopping
                Rule(categoryId = 5, pattern = "AMAZON", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "FLIPKART", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "MYNTRA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "AJIO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 5, pattern = "MEESHO", matchType = MatchType.CONTAINS, priority = 100),
                
                // Transport
                Rule(categoryId = 6, pattern = "UBER", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "OLA", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "RAPIDO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "PETROL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "FUEL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 6, pattern = "PARKING", matchType = MatchType.CONTAINS, priority = 100),
                
                // Health
                Rule(categoryId = 7, pattern = "PHARMACY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "HOSPITAL", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "CLINIC", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "APOLLO", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 7, pattern = "MEDPLUS", matchType = MatchType.CONTAINS, priority = 100),
                
                // Donation
                Rule(categoryId = 8, pattern = "DONATION", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 8, pattern = "CHARITY", matchType = MatchType.CONTAINS, priority = 100),
                Rule(categoryId = 8, pattern = "NGO", matchType = MatchType.CONTAINS, priority = 100)
            )
            
            // Insert missing rules
            val missingRules = allRules.filter { "${it.categoryId}_${it.pattern}" !in existingRulePatterns }
            if (missingRules.isNotEmpty()) {
                ruleDao.insertRules(missingRules)
            }
        }
    }
}
