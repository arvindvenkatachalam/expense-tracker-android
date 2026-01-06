package com.expensetracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.expensetracker.MainActivity
import com.expensetracker.R

object NotificationHelper {
    
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID_MONITORING = "transaction_monitoring"
    private const val CHANNEL_ID_TRANSACTIONS = "new_transactions"
    private const val CHANNEL_ID_OTHERS = "transaction_needs_categorization"
    private const val NOTIFICATION_ID_SERVICE = 1
    
    fun createNotificationChannels(context: Context) {
        try {
            android.util.Log.d(TAG, "Creating notification channels...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Channel for foreground service
                val monitoringChannel = NotificationChannel(
                    CHANNEL_ID_MONITORING,
                    "Transaction Monitoring",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when the app is monitoring for transactions"
                }
                
                // Channel for transaction notifications
                val transactionChannel = NotificationChannel(
                    CHANNEL_ID_TRANSACTIONS,
                    "New Transactions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies you when new transactions are detected"
                }
                
                // Channel for transactions needing categorization (higher priority)
                val othersChannel = NotificationChannel(
                    CHANNEL_ID_OTHERS,
                    "Transactions to Categorize",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Transactions that need manual categorization"
                    enableVibration(true)
                }
                
                notificationManager.createNotificationChannel(monitoringChannel)
                notificationManager.createNotificationChannel(transactionChannel)
                notificationManager.createNotificationChannel(othersChannel)
                android.util.Log.d(TAG, "Notification channels created successfully (3 channels)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating notification channels", e)
        }
    }
    
    fun createServiceNotification(context: Context): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_MONITORING)
            .setContentTitle("Expense Tracker")
            .setContentText("Monitoring transactions")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    fun showTransactionNotification(
        context: Context,
        merchant: String,
        amount: Double,
        category: String
    ) {
        try {
            android.util.Log.d(TAG, "Showing transaction notification for $merchant, amount: $amount, category: $category")
            
            val intent = Intent(context, MainActivity::class.java)
            // Use timestamp-based ID to prevent overwrites
            val notifId = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,  // Use unique request code
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT  // Standardized flags
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
                .setContentTitle("Transaction Detected")
                .setContentText("₹$amount at $merchant")
                .setSubText("Categorized as $category")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notifId, notification)
            android.util.Log.d(TAG, "Transaction notification shown successfully with ID: $notifId")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing transaction notification", e)
        }
    }
    
    fun showOthersCategoryNotification(
        context: Context,
        merchant: String,
        amount: Double
    ) {
        try {
            android.util.Log.d(TAG, "Showing Others category notification for $merchant, amount: $amount")
            
            // Intent to open Classify screen
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "classify")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            // Use timestamp-based ID to prevent overwrites
            val notifId = System.currentTimeMillis().toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,  // Use unique request code matching notification ID
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_OTHERS)
                .setContentTitle("Transaction Needs Categorization")
                .setContentText("₹$amount at $merchant")
                .setSubText("Tap to categorize")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            
            android.util.Log.d(TAG, "About to call notificationManager.notify() with ID: $notifId")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notifId, notification)
            android.util.Log.d(TAG, "Others notification shown successfully with ID: $notifId")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing Others category notification", e)
        }
    }
}
