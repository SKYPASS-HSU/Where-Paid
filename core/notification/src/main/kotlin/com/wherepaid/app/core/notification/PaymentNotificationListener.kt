package com.wherepaid.app.core.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wherepaid.app.core.model.ParsedPayment
import com.wherepaid.app.core.model.PaymentKind

/**
 * 토스 알림을 수신해 결제/취소만 PaymentSource로 전달한다.
 *
 * 알림 원문(title, text, extras 등)은
 * DB에 저장하지 않고 로그에도 남기지 않는다.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
    ) {
        /*
         * 정식 앱은 토스 경유 알림만 사용한다.
         */
        val isDebugTest =
            sbn.packageName == packageName &&
                    sbn.notification.extras.getBoolean(
                        EXTRA_DEBUG_TEST,
                        false,
                    )

        val isToss =
            sbn.packageName == TOSS_PACKAGE

        if (!isToss && !isDebugTest) {
            return
        }

        /*
         * 토스 만보기 Live Activity 등의 ongoing 알림 제외.
         */
        if (sbn.isOngoing) {
            return
        }

        /*
         * 그룹 요약 알림은 내용이 비어 있는 묶음 표시이므로 제외.
         */
        val isGroupSummary =
            (sbn.notification.flags and
                    Notification.FLAG_GROUP_SUMMARY) != 0

        if (isGroupSummary) {
            return
        }

        val extras = sbn.notification.extras

        val title =
            extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()

        val text =
            extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()

        val subText =
            extras
                .getCharSequence(Notification.EXTRA_SUB_TEXT)
                ?.toString()

        val bigText =
            extras
                .getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()

        /*
         * 먼저 파싱한다.
         *
         * 검증 앱과 달리 모든 토스 알림에서
         * 위치 측정을 시작하지 않는다.
         */
        val parsed =
            runCatching {
                TossNotificationParser.parse(
                    title = title,
                    text = text,
                    subText = subText,
                    bigText = bigText,
                )
            }.getOrNull()
                ?: return

        /*
         * 정식 앱 PaymentSource에서는
         * 실제 결제와 취소만 내보낸다.
         *
         * FAILED / POINT / OTHER는 종료.
         */
        val kind =
            when (parsed.kind) {
                TossPaymentKind.PAYMENT ->
                    PaymentKind.PAYMENT

                TossPaymentKind.CANCEL ->
                    PaymentKind.CANCEL

                TossPaymentKind.FAILED,
                TossPaymentKind.POINT,
                TossPaymentKind.OTHER ->
                    return
            }

        /*
         * 정식 ParsedPayment.amount는 nullable이 아니므로
         * 금액을 얻지 못한 알림은 내보내지 않는다.
         */
        val amount =
            parsed.amount
                ?: return

        val receivedAt =
            System.currentTimeMillis()

        /*
         * 같은 알림의 재전달을 구분하는 키.
         *
         * 같은 가게에서 같은 금액을 다시 결제해도
         * notification key / 게시 시각이 다르므로
         * 서로 다른 결제로 처리된다.
         */
        val dedupeKey =
            "${sbn.key}|${sbn.postTime}"

        val payment =
            ParsedPayment(
                kind = kind,
                amount = amount,
                cardName = parsed.cardName,
                merchant = parsed.merchant,
                payType = parsed.payType,
                receivedAt = receivedAt,
                dedupeKey = dedupeKey,
            )

        NotificationPaymentSource.emit(payment)
    }

    companion object {
        private const val TOSS_PACKAGE =
            "viva.republica.toss"

        internal const val EXTRA_DEBUG_TEST =
            "com.wherepaid.app.DEBUG_TEST_NOTIFICATION"
    }
}