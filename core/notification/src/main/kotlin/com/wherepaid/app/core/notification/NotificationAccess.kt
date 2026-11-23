package com.wherepaid.app.core.notification

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val component =
            ComponentName(
                context,
                PaymentNotificationListener::class.java,
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager =
                context.getSystemService(NotificationManager::class.java)

            return manager.isNotificationListenerAccessGranted(component)
        }

        // Android 8.0(API 26) fallback
        val enabledListeners =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false

        return enabledListeners
            .split(":")
            .mapNotNull {
                ComponentName.unflattenFromString(it)
            }
            .any {
                it == component
            }
    }
}