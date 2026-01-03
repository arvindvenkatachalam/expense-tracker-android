package com.expensetracker.di

import android.content.Context
import com.expensetracker.data.local.dao.CategoryDao
import com.expensetracker.data.local.dao.RuleDao
import com.expensetracker.data.local.dao.TransactionDao
import com.expensetracker.data.local.database.ExpenseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideExpenseDatabase(@ApplicationContext context: Context): ExpenseDatabase {
        return ExpenseDatabase.getDatabase(context)
    }
    
    @Provides
    fun provideTransactionDao(database: ExpenseDatabase): TransactionDao {
        return database.transactionDao()
    }
    
    @Provides
    fun provideCategoryDao(database: ExpenseDatabase): CategoryDao {
        return database.categoryDao()
    }
    
    @Provides
    fun provideRuleDao(database: ExpenseDatabase): RuleDao {
        return database.ruleDao()
    }
}
