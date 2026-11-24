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

/** 동(洞) 단위 규칙. 리포트 칭호의 동네와 공유 카드의 "동네 이름 표시"가 쓴다. */
object Neighborhoods {
    private val DONG = Regex("""^[가-힣]+?(\d+)?(동|가|읍|면)$""")
    private val STREET = Regex("""(로|길)(\d+(번)?길)?$""")

    /**
     * 카카오 주소에서 동 이름을 뽑는다. 지번 주소만 동이 들어 있다.
     * "서울 성동구 성수동2가 280" → "성수동", "서울 강남구 역삼1동 10" → "역삼동", 도로명 주소 → null.
     */
    fun fromAddress(address: String?): String? {
        val tokens = address?.trim()?.split(Regex("""\s+""")) ?: return null
        for (token in tokens.drop(1)) {
            if (token.first().isDigit() || STREET.containsMatchIn(token)) return null
            if (token.endsWith("구") || token.endsWith("시") || token.endsWith("군")) continue
            if (!DONG.matches(token)) continue
            return token
                .replace(Regex("""\d+가$"""), "") // 성수동2가 → 성수동
                .replace(Regex("""\d+(동|읍|면)$"""), "$1") // 역삼1동 → 역삼동
                .ifEmpty { null }
        }
        return null
    }

    /** 그 달 지도 핀 결제(취소 반영) 합계가 가장 큰 동. 동을 아는 결제가 없으면 null. 같으면 이름순. */
    fun top(transactions: List<Transaction>): String? = transactions
        .filter { it.status == PlaceStatus.MAPPED }
        .mapNotNull { tx -> tx.region?.neighborhood?.let { it to tx.netAmount } }
        .groupBy({ it.first }, { it.second })
        .map { (name, amounts) -> name to amounts.sum() }
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        .firstOrNull()?.first
}

/** "○월 리포트가 도착했어요" 배너 규칙 (FR-04). */
object ReportArrival {
    /**
     * 지난달 리포트가 새로 도착했으면 그 달, 아니면 null.
     * 지난달 결제가 있고, 지난달(또는 그 뒤) 리포트를 아직 안 봤을 때 도착한 것으로 본다.
     */
    fun arrivedMonth(thisMonth: YearMonth, lastMonthHasPayments: Boolean, lastSeen: YearMonth?): YearMonth? {
        val lastMonth = thisMonth.minusMonths(1)
        val seen = lastSeen != null && lastSeen >= lastMonth
        return if (lastMonthHasPayments && !seen) lastMonth else null
    }
}

/** 월간 리포트 요청 규칙 (FR-04). 리포트 화면과 테스트가 같이 쓴다. */
object MonthlyReportRules {
    const val MAX_CATEGORIES = 10
    private const val OTHER = "기타"

    /** 카카오 카테고리의 마지막 칸. "음식점 > 카페" → "카페", 비어 있으면 "기타". 20자로 자른다. */
    fun categoryName(place: Place?): String =
        place?.category?.split(">")?.last()?.trim()?.take(20)?.ifEmpty { null } ?: OTHER

    /** 그 달 결제와 지난달 결제로 요청을 만든다. 지난달 결제가 없으면 [MonthlyReportRequest.previousMonthTotal]은 null. */
    fun request(month: YearMonth, transactions: List<Transaction>, previousMonth: List<Transaction>): MonthlyReportRequest {
        val summary = MonthSummary.of(transactions)
        val categories = transactions
            .filter { it.status == PlaceStatus.MAPPED }
            .groupBy { categoryName(it.place) }
            .map { (name, list) -> CategoryTotal(name, list.sumOf { it.netAmount }, list.size) }
            .sortedWith(compareByDescending<CategoryTotal> { it.total }.thenBy { it.name })
            .take(MAX_CATEGORIES)
        val previous = MonthSummary.of(previousMonth)
        return MonthlyReportRequest(
            month = month,
            offlineTotal = summary.offlineTotal,
            onlineTotal = summary.onlineTotal,
            paymentCount = transactions.size,
            categories = categories,
            previousMonthTotal = if (previousMonth.isEmpty()) null else previous.offlineTotal + previous.onlineTotal,
        )
    }
}

/** 두 좌표 사이 거리(m). 하버사인 공식. */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}
