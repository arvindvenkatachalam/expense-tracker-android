package com.expensetracker

import android.app.Application
import com.expensetracker.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpenseTrackerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels for Android O and above
        NotificationHelper.createNotificationChannels(this)
    }
}
