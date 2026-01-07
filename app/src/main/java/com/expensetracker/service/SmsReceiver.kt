package com.expensetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
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
        
        // Track processed SMS to prevent duplicates
        private val processedSms = mutableSetOf<String>()
        private var lastCleanup = System.currentTimeMillis()
        
        private fun getMessageKey(messages: Array<SmsMessage>): String {
            return messages.joinToString("|") { "${it.originatingAddress}_${it.timestampMillis}_${it.messageBody}" }
        }
        
        private fun cleanupOldEntries() {
            val now = System.currentTimeMillis()
            if (now - lastCleanup > 60000) { // Clean every minute
                processedSms.clear()
                lastCleanup = now
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "========== SMS RECEIVED ==========")
        
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Not an SMS_RECEIVED action, ignoring")
            return
        }
        
        // Extract messages first to check for duplicates
        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val messages = pdus.mapNotNull { pdu ->
            SmsMessage.createFromPdu(pdu as ByteArray, bundle.getString("format"))
        }.toTypedArray()
        
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages found in SMS")
            return
        }
        
        // Check for duplicate
        val messageKey = getMessageKey(messages)
        cleanupOldEntries()
        
        synchronized(processedSms) {
            if (processedSms.contains(messageKey)) {
                Log.d(TAG, "DUPLICATE SMS detected, ignoring")
                return
            }
            processedSms.add(messageKey)
        }
        
        Log.d(TAG, "Processing NEW SMS (not duplicate)")
        
        // Ensure notification channels are created (fallback if app didn't initialize properly)
        NotificationHelper.createNotificationChannels(context)
        
        // Continue with SMS parsing (action already checked above)
        // Reuse 'messages' from deduplication check above
        
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
            
            Log.d(TAG, "Parsed: Amount=${parsedTransaction.amount}, Merchant=${parsedTransaction.merchant}, Type=${parsedTransaction.transactionType}")
            
            // Process debit and unknown transactions (unknown might be UPI)
            if (parsedTransaction.transactionType == TransactionType.CREDIT) {
                Log.d(TAG, "Credit transaction, skipping (no notification)")
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
                    Log.d(TAG, "Transaction inserted with ID: $id, Category ID: $categoryId")
                    
                    // CRITICAL: Show notification IMMEDIATELY while coroutine is still alive
                    // Get category for display (but don't wait if it fails)
                    val category = try {
                        repository.getCategoryById(categoryId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get category", e)
                        null
                    }
                    
                    // Show appropriate notification based on category
                    // Category ID 7 is "Others" - check ID directly for reliability
                    if (categoryId == 7L) {
                        // Show special notification for uncategorized transactions
                        Log.d(TAG, "Showing Others category notification for ${parsedTransaction.merchant}")
                        NotificationHelper.showOthersCategoryNotification(
                            context,
                            parsedTransaction.merchant,
                            parsedTransaction.amount
                        )
                    } else {
                        // Show normal transaction notification
                        Log.d(TAG, "Showing normal notification for ${parsedTransaction.merchant}, category: ${category?.name}")
                        NotificationHelper.showTransactionNotification(
                            context,
                            parsedTransaction.merchant,
                            parsedTransaction.amount,
                            category?.name ?: "Others"
                        )
                    }
                    
                    Log.d(TAG, "Notification call completed")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing transaction", e)
                }
            }
        }
    }
}
