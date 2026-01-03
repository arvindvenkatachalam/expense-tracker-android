package com.expensetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.expensetracker.data.local.database.ExpenseDatabase
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.data.repository.ExpenseRepository
import com.expensetracker.domain.usecase.CategorizationEngine
import com.expensetracker.service.parser.TransactionParser
import com.expensetracker.service.parser.TransactionType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var repository: ExpenseRepository
    
    @Inject
    lateinit var categorizationEngine: CategorizationEngine
    
    private val parser = TransactionParser()
    
    companion object {
        private const val TAG = "SmsReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "SMS received")
        
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress
            val messageBody = smsMessage.messageBody
            
            Log.d(TAG, "SMS from: $sender")
            
            // Check if it's from a bank
            if (!parser.isBankSms(sender)) {
                Log.d(TAG, "Not a bank SMS, ignoring")
                continue
            }
            
            // Parse the transaction
            val parsedTransaction = parser.parseTransaction(messageBody, sender)
            
            if (parsedTransaction == null) {
                Log.w(TAG, "Failed to parse transaction")
                continue
            }
            
            // Process debit and unknown transactions (unknown might be UPI)
            if (parsedTransaction.transactionType == TransactionType.CREDIT) {
                Log.d(TAG, "Credit transaction, ignoring")
                continue
            }
            
            // Process the transaction in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Categorize the transaction
                    val categoryId = categorizationEngine.categorize(parsedTransaction.merchant)
                    
                    // Create transaction entity
                    val transaction = Transaction(
                        amount = parsedTransaction.amount,
                        merchant = parsedTransaction.merchant,
                        categoryId = categoryId,
                        timestamp = parsedTransaction.timestamp,
                        smsBody = parsedTransaction.rawSms,
                        bankName = parsedTransaction.bankName,
                        accountLast4 = parsedTransaction.accountLast4,
                        transactionType = parsedTransaction.transactionType.name,
                        isManuallyEdited = false
                    )
                    
                    // Insert into database
                    val id = repository.insertTransaction(transaction)
                    Log.d(TAG, "Transaction inserted with ID: $id")
                    
                    // Show notification
                    val category = repository.getCategoryById(categoryId)
                    NotificationHelper.showTransactionNotification(
                        context,
                        parsedTransaction.merchant,
                        parsedTransaction.amount,
                        category?.name ?: "Others"
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing transaction", e)
                }
            }
        }
    }
}
