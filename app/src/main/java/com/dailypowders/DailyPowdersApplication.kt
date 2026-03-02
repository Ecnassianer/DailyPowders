package com.dailypowders

import android.app.Application
import com.dailypowders.notification.NotificationHelper

class DailyPowdersApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createNotificationChannel()
    }
}
