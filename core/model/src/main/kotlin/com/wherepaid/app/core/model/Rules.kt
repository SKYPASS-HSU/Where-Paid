package com.wherepaid.app.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 분석서의 규칙 중 저장소 구현(가짜, Room)과 화면이 같이 쓰는 것. 순수 함수만 둔다. */

/** 날짜 계산 기준 시간대. */
val KOREA_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

fun YearMonth.startMillis(): Long = atDay(1).atStartOfDay(KOREA_ZONE).toInstant().toEpochMilli()

fun YearMonth.endMillisExclusive(): Long = plusMonths(1).startMillis()

fun yearMonthOf(epochMillis: Long): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(KOREA_ZONE))

object CancellationRules {
    /**
     * 취소를 붙일 결제 (분석서 FR-01).
     * 같은 가맹점명 결제 중 남은 금액(결제액 − 이미 붙은 취소액)이 취소 금액 이상인 것만 대상이고,
     * 결제액이 취소 금액과 같은 결제를 우선, 없으면 가장 최근 결제. 대상이 없으면 null.
     */
    fun findTarget(payments: List<Transaction>, merchant: String, amount: Long): Transaction? {
        val eligible = payments
            .filter { it.merchantRaw == merchant && it.netAmount >= amount }
            .sortedByDescending { it.paidAt }
        return eligible.firstOrNull { it.amount == amount } ?: eligible.firstOrNull()
    }
}

/**
 * 사용자 동작에 따른 결제 상태 이동 (분석서 2-3). 학습기록·사용자 지정 저장은 저장소가 따로 한다.
 */
object PlaceTransitions {
    fun choosePlace(tx: Transaction, place: Place): Transaction =
        tx.copy(status = PlaceStatus.MAPPED, place = place, candidates = emptyList(), unmatchedReason = null)

    fun markNotHere(tx: Transaction): Transaction =
        tx.copy(status = PlaceStatus.UNMATCHED, place = null, candidates = emptyList(), unmatchedReason = UnmatchedReason.NOT_HERE)

    fun markOnline(tx: Transaction): Transaction =
        tx.copy(status = PlaceStatus.ONLINE, place = null, candidates = emptyList(), unmatchedReason = null)

    /** [기억 안 나요]. 매칭실패함으로 두되 장소 확인 목록에서 뺀다 ([Transaction.needsPlace]). */
    fun markForgotten(tx: Transaction): Transaction =
        tx.copy(status = PlaceStatus.UNMATCHED, place = null, candidates = emptyList(), unmatchedReason = UnmatchedReason.FORGOTTEN)

    fun markOffline(tx: Transaction): Transaction =
        tx.copy(status = PlaceStatus.UNMATCHED, place = null, candidates = emptyList(), unmatchedReason = UnmatchedReason.MARKED_OFFLINE)
}

/** 지도 홈 상단 카드와 핀 크기에 쓰는 한 달 합계 (모두 취소 반영 금액). */
data class MonthSummary(
    /** 온라인을 뺀 모든 결제. 느낌표 핀·매칭실패함·매칭 대기 포함. */
    val offlineTotal: Long,
    val onlineTotal: Long,
    /** 매장별 합계. 지도 핀 하나 = 항목 하나. 금액 큰 순. */
    val places: List<PlaceTotal>,
    /** 장소 확인이 필요한 결제 수. */
    val needsPlaceCount: Int,
) {
    companion object {
        fun of(transactions: List<Transaction>): MonthSummary {
            val online = transactions.filter { it.status == PlaceStatus.ONLINE }
            val offline = transactions - online.toSet()
            val places = transactions
                .filter { it.status == PlaceStatus.MAPPED && it.place != null }
                .groupBy { it.place!!.id }
                .map { (_, list) -> PlaceTotal(list.first().place!!, list.sumOf { it.netAmount }, list.size) }
                .sortedByDescending { it.total }
            return MonthSummary(
                offlineTotal = offline.sumOf { it.netAmount },
                onlineTotal = online.sumOf { it.netAmount },
                places = places,
                needsPlaceCount = transactions.count { it.needsPlace },
            )
        }
    }
}

data class PlaceTotal(val place: Place, val total: Long, val count: Int)

/** 두 좌표 사이 거리(m). 하버사인 공식. */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}
