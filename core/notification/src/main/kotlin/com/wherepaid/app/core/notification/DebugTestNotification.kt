package com.wherepaid.app.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * 실제 결제 없이 NotificationListener + TossNotificationParser
 * 전체 흐름을 테스트하기 위한 개발용 알림.
 */
object DebugTestNotification {

    private const val CHANNEL_ID =
        "wherepaid_debug_payment"

    private const val CHANNEL_NAME =
        "결제 테스트"

    private var nextId = 10000

    fun sendPayment(context: Context) {
        show(
            context = context,
            title = "10,000원 결제",
            text = "테스트카드 | 테스트매장(일시불)",
        )
    }

    fun sendCancel(context: Context) {
        show(
            context = context,
            title = "10,000원 결제 취소",
            text = "테스트카드 | 테스트매장(일시불)",
        )
    }

    fun sendFailed(context: Context) {
        show(
            context = context,
            title = "10,000원 결제 실패",
            text = "테스트카드 | 테스트매장(일시불)",
        )
    }

    fun sendPoint(context: Context) {
        show(
            context = context,
            title = "포인트 적립",
            text = "토스페이 결제 혜택으로 10원 받았어요",
        )
    }

    private fun show(
        context: Context,
        title: String,
        text: String,
    ) {
        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        createChannel(manager)

        val notification =
            Notification.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(text)
                )
                .setAutoCancel(true)
                .setShowWhen(true)
                .addExtras(
                    android.os.Bundle().apply {
                        putBoolean(
                            PaymentNotificationListener
                                .EXTRA_DEBUG_TEST,
                            true,
                        )
                    }
                )
                .build()

        manager.notify(
            nextId++,
            notification,
        )
    }

    private fun createChannel(
        manager: NotificationManager,
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager
                        .IMPORTANCE_DEFAULT,
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }
}