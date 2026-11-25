package com.wherepaid.app.feature.map.detail

import com.wherepaid.app.core.designsystem.format.formatDateWithWeekday
import com.wherepaid.app.core.designsystem.format.formatShortDateTime
import com.wherepaid.app.core.designsystem.format.formatWon
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/** S-03 핀 상세 시트가 그리는 것. */
sealed interface PinDetailUiState {
    data object Loading : PinDetailUiState

    /** 이 매장에 그 달 결제가 더 없다 (모두 삭제·장소 변경). 시트를 닫는다. */
    data object Gone : PinDetailUiState

    data class Shown(
        /** "스타벅스 성수역점" */
        val placeName: String,
        /** "카페 · 10월에 9번 방문" */
        val subtitle: String,
        /** "61,500원" 취소 반영 합계 */
        val total: String,
        /** 결제와 취소 줄, 최근 순 */
        val rows: List<PinDetailRow>,
    ) : PinDetailUiState
}

/** 핀 상세의 한 줄. 취소 줄은 원래 결제 아래가 아니라 시각 순서대로 섞인다 (시안 4:40). */
data class PinDetailRow(
    /** 줄마다 다른 키 */
    val key: String,
    /** 이 줄의 [⋯] 메뉴가 다루는 결제 (취소 줄이면 원래 결제) */
    val transactionId: String,
    /** "10.11 (토) 14:32" */
    val dateTime: String,
    /** "KB국민체크" / "KB국민체크 · 결제 취소" */
    val detail: String,
    /** "6,800원" / "-4,500원" */
    val amount: String,
    val isCancellation: Boolean,
    /** 삭제 확인(D-01)의 회색 칸: "스타벅스 성수역점 · 6,800원 · 10.11 14:32" */
    val deleteSummary: String,
)

/**
 * 고른 달의 결제 중 이 매장([placeId])의 지도 핀 결제를 시트 상태로 바꾼다.
 * 방문 수는 결제 건수 (취소 줄은 세지 않는다).
 */
fun toPinDetailUiState(
    placeId: String,
    month: YearMonth,
    monthTransactions: List<Transaction>,
    today: LocalDate,
): PinDetailUiState {
    val payments = monthTransactions.filter { it.status == PlaceStatus.MAPPED && it.place?.id == placeId }
    if (payments.isEmpty()) return PinDetailUiState.Gone
    val place = payments.first().place!!

    val rows = payments.flatMap { tx ->
        val summary = listOf(place.name, formatWon(tx.amount), formatShortDateTime(tx.paidAt, today)).joinToString(" · ")
        val payment = TimedRow(
            at = tx.paidAt,
            row = PinDetailRow(
                key = tx.id,
                transactionId = tx.id,
                dateTime = formatDateWithWeekday(tx.paidAt),
                detail = tx.cardName.orEmpty(),
                amount = formatWon(tx.amount),
                isCancellation = false,
                deleteSummary = summary,
            ),
        )
        val cancellations = tx.cancellations.mapIndexed { index, cancel ->
            TimedRow(
                at = cancel.canceledAt,
                row = PinDetailRow(
                    key = "${tx.id}-cancel-$index",
                    transactionId = tx.id,
                    dateTime = formatDateWithWeekday(cancel.canceledAt),
                    detail = listOfNotNull(tx.cardName, "결제 취소").joinToString(" · "),
                    amount = formatWon(-cancel.amount),
                    isCancellation = true,
                    deleteSummary = summary,
                ),
            )
        }
        listOf(payment) + cancellations
    }.sortedByDescending { it.at }.map { it.row }

    val subtitle = listOfNotNull(categoryLabel(place.category), "${month.monthValue}월에 ${payments.size}번 방문").joinToString(" · ")
    return PinDetailUiState.Shown(
        placeName = place.name,
        subtitle = subtitle,
        total = formatWon(payments.sumOf { it.netAmount }),
        rows = rows,
    )
}

/** 카카오 카테고리의 마지막 칸: "음식점 > 카페" → "카페". */
fun categoryLabel(category: String?): String? =
    category?.substringAfterLast('>')?.trim()?.takeIf { it.isNotEmpty() }

private class TimedRow(val at: Long, val row: PinDetailRow)
