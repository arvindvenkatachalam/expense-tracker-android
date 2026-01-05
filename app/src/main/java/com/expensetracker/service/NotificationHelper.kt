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
    
    private const val CHANNEL_ID_MONITORING = "transaction_monitoring"
    private const val CHANNEL_ID_TRANSACTIONS = "new_transactions"
    private const val NOTIFICATION_ID_SERVICE = 1
    private var notificationIdCounter = 100
    
    fun createNotificationChannels(context: Context) {
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
            
            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(transactionChannel)
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
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
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
        notificationManager.notify(notificationIdCounter++, notification)
    }
    
    fun showOthersCategoryNotification(
        context: Context,
        merchant: String,
        amount: Double
    ) {
        // Intent to open Classify screen
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "classify")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Transaction Needs Categorization")
            .setContentText("₹$amount at $merchant")
            .setSubText("Tap to categorize")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationIdCounter++, notification)
    }
}
