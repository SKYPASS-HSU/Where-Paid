package com.wherepaid.app.core.notification

/**
 * 토스 결제 알림 파서.
 *
 * 검증 앱에서 사용한 VERSION 5 규칙을 정식 앱으로 이식한 버전.
 *
 * 이 클래스는 알림 문자열을 해석하는 역할만 한다.
 * 알림 원문(title, text, extras 등)은 저장하거나 로그에 남기지 않는다.
 *
 * 확인된 형식:
 *
 * 1) 카드 알림
 * title   = "카드 알림 받기"
 * text    = "26,400원 결제"
 * subText = "하나체크 | Adobe(일시불)"
 *
 * 2) 타 카드사
 * title = "20,000원 결제"
 * text  = "하나체크 | 네이버페이(일시불)"
 *
 * 3) 결제 취소
 * title = "20,000원 결제 취소"
 * text  = "하나체크 | 네이버페이(일시불)"
 *
 * 4) 토스페이
 * title = "17,000원 결제 완료"
 * text  = "토스페이머니 · 구글페이먼트코리아 유한회사"
 *
 * 5) 포인트 적립
 * title = "포인트 적립"
 * text  = "토스페이 결제 혜택으로 14원 받았어요"
 *
 * 6) 결제 실패
 * title = "2,500원 결제 실패"
 * text  = "우리체크 | 맘스터치(일시불)"
 *
 * 7) 토스뱅크 체크카드
 * title   = "10,000원 결제"
 * subText = "토스뱅크"
 * text    = "토스뱅크 체크카드 | 가맹점\n잔액 740,300원"
 */
object TossNotificationParser {

    /**
     * 검증 앱 파서 규칙 버전.
     *
     * 파싱 규칙을 변경하면 버전을 올린다.
     */
    const val VERSION = 5

    // ---------------------------------------------------------------------
    // 규칙
    // ---------------------------------------------------------------------

    /**
     * 금액
     *
     * "26,400원" -> 26400
     */
    private val AMOUNT =
        Regex("""(\d[\d,]*)\s*원""")

    /**
     * 실제 결제 줄.
     *
     * 예:
     * "17,000원 결제 완료"
     * "26,400원 결제"
     * "5,000원 승인"
     *
     * 이 형식이 있어야 일반 결제로 판단한다.
     */
    private val PAYMENT_LINE =
        Regex("""(\d[\d,]*)\s*원\s*(결제|승인|사용)""")

    /**
     * 이 단어가 포함된 줄의 금액은 결제 금액으로 사용하지 않는다.
     *
     * 예:
     * "잔액 740,300원"
     */
    private val NON_PAYMENT_AMOUNT_KEYWORDS =
        listOf(
            "잔액",
            "누적",
            "한도",
        )

    /**
     * 결제가 실제로 이루어지지 않은 경우.
     *
     * 취소보다 먼저 판별한다.
     */
    private val FAILED_KEYWORDS =
        listOf(
            "실패",
            "거절",
            "잔액부족",
            "잔액 부족",
            "한도초과",
            "한도 초과",
            "승인되지 않",
        )

    /**
     * 결제 취소/환불 판별.
     */
    private val CANCEL_KEYWORDS =
        listOf(
            "취소",
            "환불",
        )

    /**
     * 포인트/혜택 관련 알림.
     *
     * 단, PAYMENT_LINE이 함께 있으면 실제 결제를 우선한다.
     *
     * 예:
     * "보너스 포인트 적립 가능"
     * → 실제 결제 알림 안에 포함될 수 있음
     */
    private val POINT_KEYWORDS =
        listOf(
            "적립",
            "캐시백",
            "받았어요",
            "혜택으로",
        )

    /**
     * 카드/결제수단과 가맹점을 구분하는 문자.
     *
     * 예:
     * "하나체크 | Adobe"
     * "토스페이머니 · 구글페이먼트코리아"
     *
     * 화면에서는 비슷하게 보이는 여러 종류의 가운뎃점을 모두 허용한다.
     *
     * | ｜ · • ∙ ⋅ ・ ･ ‧ ㆍ
     */
    private val CARD_MERCHANT_SEPARATOR =
        Regex(
            """\s*[|\uFF5C\u00B7\u2022\u2219\u22C5\u30FB\uFF65\u2027\u318D]\s*"""
        )

    /**
     * 가맹점명 뒤의 결제 방식.
     *
     * 예:
     * "(일시불)"
     * "(할부 3개월)"
     * "(3개월)"
     */
    private val PAY_TYPE_SUFFIX =
        Regex(
            """\(\s*(일시불|할부[^()]*|\d+\s*개월[^()]*)\s*\)\s*$"""
        )

    // ---------------------------------------------------------------------
    // 파싱
    // ---------------------------------------------------------------------

    internal fun parse(
        title: String?,
        text: String?,
        subText: String?,
        bigText: String?,
    ): TossParsedPayment {

        /*
         * Android 버전이나 알림 형태에 따라
         * 같은 내용이 title/text/subText/bigText 중
         * 어디에 들어오는지가 달라질 수 있다.
         *
         * 따라서 모든 필드의 모든 줄을 합쳐서 분석한다.
         */
        val lines =
            listOfNotNull(
                title,
                text,
                subText,
                bigText,
            )
                .flatMap { it.lines() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        /*
         * 결제 금액이 들어 있는 줄을 우선 찾는다.
         */
        val paymentLine =
            lines.firstOrNull {
                PAYMENT_LINE.containsMatchIn(it)
            }

        /*
         * 알림 종류 판별.
         */
        val kind =
            classify(
                lines = lines,
                hasPaymentLine = paymentLine != null,
            )

        /*
         * 금액 추출.
         *
         * 1순위:
         *   PAYMENT_LINE에서 금액 추출
         *
         * 2순위:
         *   "잔액", "한도" 등이 없는 줄에서 금액 추출
         */
        val amount =
            paymentLine?.let(::parseAmount)
                ?: lines
                    .filter { line ->
                        NON_PAYMENT_AMOUNT_KEYWORDS.none {
                            it in line
                        }
                    }
                    .firstNotNullOfOrNull(::parseAmount)

        /*
         * 결제/취소/실패일 때만
         * 카드/결제수단 + 가맹점 정보를 분석한다.
         */
        val cardMerchant =
            if (
                kind == TossPaymentKind.PAYMENT ||
                kind == TossPaymentKind.CANCEL ||
                kind == TossPaymentKind.FAILED
            ) {
                lines
                    .filter {
                        !AMOUNT.containsMatchIn(it)
                    }
                    .firstNotNullOfOrNull(
                        ::parseCardMerchant
                    )
            } else {
                null
            }

        return TossParsedPayment(
            kind = kind,
            amount = amount,
            isCancel =
                when (kind) {
                    TossPaymentKind.CANCEL -> true
                    TossPaymentKind.PAYMENT -> false
                    else -> null
                },
            cardMerchant = cardMerchant,
        )
    }

    /**
     * PAYMENT / CANCEL / FAILED / POINT / OTHER 판별.
     */
    internal fun classify(
        lines: List<String>,
        hasPaymentLine: Boolean,
    ): TossPaymentKind =
        when {

            /*
             * 결제 실패를 가장 먼저 본다.
             */
            lines.any { line ->
                FAILED_KEYWORDS.any {
                    it in line
                }
            } &&
                    lines.any {
                        AMOUNT.containsMatchIn(it)
                    } -> TossPaymentKind.FAILED

            /*
             * 취소/환불.
             */
            lines.any { line ->
                CANCEL_KEYWORDS.any {
                    it in line
                }
            } &&
                    lines.any {
                        AMOUNT.containsMatchIn(it)
                    } -> TossPaymentKind.CANCEL

            /*
             * 정상 결제.
             */
            hasPaymentLine ->
                TossPaymentKind.PAYMENT

            /*
             * 포인트/혜택 알림.
             */
            lines.any { line ->
                POINT_KEYWORDS.any {
                    it in line
                }
            } -> TossPaymentKind.POINT

            /*
             * 그 외 토스 알림.
             */
            else ->
                TossPaymentKind.OTHER
        }

    /**
     * 문자열에서 원화 금액 하나를 추출한다.
     *
     * "26,400원"
     * → 26400
     */
    internal fun parseAmount(
        s: String,
    ): Long? {

        val match =
            AMOUNT.find(s)
                ?: return null

        return match
            .groupValues[1]
            .replace(",", "")
            .toLongOrNull()
    }

    /**
     * 카드/결제수단과 가맹점을 분리한다.
     *
     * 예:
     *
     * "하나체크 | Adobe(일시불)"
     *
     * →
     *
     * cardName = "하나체크"
     * merchant = "Adobe"
     * payType  = "일시불"
     *
     * 구분자가 없으면 null.
     */
    internal fun parseCardMerchant(
        s: String?,
    ): TossCardMerchant? {

        if (s.isNullOrBlank()) {
            return null
        }

        val parts =
            s.split(
                CARD_MERCHANT_SEPARATOR,
                limit = 2,
            )

        if (parts.size < 2) {
            return null
        }

        val card =
            parts[0]
                .trim()
                .ifEmpty { null }

        var rest =
            parts[1].trim()

        var payType: String? = null

        PAY_TYPE_SUFFIX
            .find(rest)
            ?.let { match ->

                payType =
                    match.groupValues[1]
                        .trim()

                rest =
                    rest
                        .substring(
                            0,
                            match.range.first,
                        )
                        .trim()
            }

        return TossCardMerchant(
            cardName = card,
            merchant = rest.ifEmpty { null },
            payType = payType,
        )
    }
}

/**
 * 토스 파서 내부에서 사용하는 알림 종류.
 *
 * 정식 앱의 core.model.PaymentKind와는 별개다.
 *
 * POINT와 OTHER까지 구분한 다음,
 * 이후 PaymentSource 구현에서 필요한 종류만
 * 정식 ParsedPayment로 변환한다.
 */
internal enum class TossPaymentKind {
    PAYMENT,
    CANCEL,
    FAILED,
    POINT,
    OTHER,
}

/**
 * 토스 파서 내부에서 사용하는
 * 카드/결제수단 + 가맹점 분석 결과.
 */
internal data class TossCardMerchant(
    val cardName: String?,
    val merchant: String?,
    val payType: String?,
)

/**
 * 토스 파서 내부 분석 결과.
 *
 * 아직 Android Notification의
 * receivedAt / dedupeKey 정보는 포함하지 않는다.
 *
 * 이후 NotificationListener / PaymentSource가
 * 정식 core.model.ParsedPayment로 변환한다.
 */
internal data class TossParsedPayment(
    val kind: TossPaymentKind,
    val amount: Long?,
    val isCancel: Boolean?,
    private val cardMerchant: TossCardMerchant?,
) {

    val cardName: String?
        get() = cardMerchant?.cardName

    val merchant: String?
        get() = cardMerchant?.merchant

    val payType: String?
        get() = cardMerchant?.payType
}