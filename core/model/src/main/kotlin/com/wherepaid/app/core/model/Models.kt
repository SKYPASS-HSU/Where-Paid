package com.wherepaid.app.core.model

/**
 * 모듈 사이를 오가는 모델. 요구사항 분석서(1-6 용어, 2-3 결제 상태 이동표) 기준이다.
 * 필드를 바꾸면 모든 모듈이 영향을 받으므로 그 변경만 담은 PR로 팀 전체 확인을 받는다.
 *
 * 알림 원문(title, text 등)은 어떤 모델에도 넣지 않는다. 파싱 결과만 다룬다.
 */

/** WGS84 좌표. */
data class LatLng(val latitude: Double, val longitude: Double)

/** 토스 알림 종류. 결제 실패는 기록하지 않고, 취소는 원래 결제에 [Cancellation]으로 붙는다. */
enum class PaymentKind { PAYMENT, CANCEL, FAILED }

/** 토스 알림 1건을 파싱한 결과. */
data class ParsedPayment(
    val kind: PaymentKind,
    /** 원 단위 금액. */
    val amount: Long,
    val cardName: String?,
    /** 알림에 적힌 가맹점명 그대로. */
    val merchant: String?,
    /** "일시불", "할부 3개월" 등. */
    val payType: String?,
    /** 알림을 받은 시각 (epoch millis). 토스 알림에 결제 시각이 없어 이 값을 결제 시각으로 쓴다. */
    val receivedAt: Long,
    /** 같은 알림 재전달을 거르는 키: 알림 key + 게시 시각 (분석서 FR-01). */
    val dedupeKey: String,
)

/** 위치 1회 측정 결과. */
sealed interface LocationResult {
    data class Success(val latLng: LatLng, val accuracyM: Float?) : LocationResult
    data class Failure(val reason: LocationFailure) : LocationResult
}

enum class LocationFailure { NO_PERMISSION, LOCATION_DISABLED, TIMEOUT, UNKNOWN }

/** 카카오 로컬 API로 찾은 장소. */
data class Place(
    /** 카카오 장소 ID. */
    val id: String,
    val name: String,
    val latLng: LatLng,
    /** 카카오 카테고리. 예: "음식점 > 카페" */
    val category: String?,
    val address: String?,
)

/** 행정구역. 결제 좌표로 기기 안에서 판별해 저장한다 (지역 색칠, 리포트의 동네 집계). */
data class Region(
    /** 시·도. 예: "서울특별시" */
    val province: String,
    /** 시·군·구. 예: "성동구" */
    val district: String?,
)

/**
 * 결제의 장소 상태 (분석서 2-3 결제 상태 이동표).
 * 화면: MAPPED → 지도 핀, AMBIGUOUS → 느낌표 핀, UNMATCHED·PENDING_SEARCH → 장소 확인(매칭실패함), ONLINE → 온라인 목록
 */
enum class PlaceStatus {
    /** 매장 하나로 확정. [Transaction.place]가 있다. */
    MAPPED,

    /** 이름이 맞는 후보가 2곳 이상. [Transaction.candidates]에서 사용자가 고른다. */
    AMBIGUOUS,

    /** 장소를 정하지 못함. 사용자가 매장 검색으로 등록한다. 이유는 [Transaction.unmatchedReason]. */
    UNMATCHED,

    /** 네트워크가 없거나 검색 요청이 실패해 검색을 미룸. 재연결 시 저장된 좌표로 다시 검색한다. */
    PENDING_SEARCH,

    /** 온라인 결제. 지도에 표시하지 않는다. */
    ONLINE,
}

/** [PlaceStatus.UNMATCHED]가 된 이유. 장소 확인 화면의 보조 문구에 쓴다. */
enum class UnmatchedReason {
    /** 위치 권한 없음, 10초 안에 위치를 못 얻음. "위치를 못 받았어요" */
    NO_LOCATION,

    /** 오차 100 m 초과, 네트워크 재연결 후 2분 이내 수신. */
    UNTRUSTED_LOCATION,

    /** 근처 검색 결과 0곳. "근처에서 못 찾았어요" */
    NO_RESULT,

    /** 후보 중에 없다고 함 [여기 아니에요]. */
    NOT_HERE,

    /** 온라인 목록에서 [오프라인이에요]. "오프라인 결제로 옮김" */
    MARKED_OFFLINE,

    /**
     * 후보 고르기·매장 찾기에서 [기억 안 나요]. 장소 확인 목록과 지도에서 빠지고 총액에는 남는다.
     * 다시 장소를 정하려면 [TransactionRepository.choosePlace]를 그대로 쓴다.
     */
    FORGOTTEN,
}

/** 사용자가 가맹점명 단위로 정한 온라인/오프라인 (분석서 1-6 사용자 지정). */
enum class UserDesignation { ONLINE, OFFLINE }

/** 원래 결제에 붙는 취소·환불 한 줄 ("-4,500원"). */
data class Cancellation(
    /** 원 단위 취소 금액 (양수). */
    val amount: Long,
    /** 취소 알림을 받은 시각 (epoch millis). */
    val canceledAt: Long,
)

/** 저장되는 결제 1건. 결제 실패는 기록하지 않는다. */
data class Transaction(
    val id: String,
    /** 원 단위 결제 금액. 취소는 [cancellations]에 따로 붙는다. */
    val amount: Long,
    /** 알림에 적힌 가맹점명 그대로. 학습기록·사용자 지정의 키로도 쓴다. */
    val merchantRaw: String,
    val cardName: String?,
    val payType: String?,
    /** 결제 시각 (epoch millis). 알림 수신 시각. 이 시각이 속한 달의 합계에 들어간다. */
    val paidAt: Long,
    /** 같은 알림 재전달을 거르는 키 ([ParsedPayment.dedupeKey]). */
    val dedupeKey: String?,
    /** 알림을 받은 순간 측정한 위치. 측정에 실패했으면 null. */
    val location: LatLng?,
    val locationAccuracyM: Float?,
    val region: Region?,
    val status: PlaceStatus,
    /** [PlaceStatus.MAPPED]일 때의 매장. */
    val place: Place?,
    /** [PlaceStatus.AMBIGUOUS]일 때의 후보 매장 (결제 위치에서 가까운 순). */
    val candidates: List<Place>,
    val unmatchedReason: UnmatchedReason?,
    val cancellations: List<Cancellation>,
) {
    /** 취소를 뺀 금액. 합계·핀 크기·리포트는 모두 이 값을 쓴다. */
    val netAmount: Long get() = amount - cancellations.sumOf { it.amount }

    /** 장소 확인 화면에 나오는 결제인지 (느낌표 핀, 매칭실패함, 매칭 대기). [기억 안 나요]로 넘긴 결제는 빠진다. */
    val needsPlace: Boolean
        get() = when (status) {
            PlaceStatus.AMBIGUOUS, PlaceStatus.PENDING_SEARCH -> true
            PlaceStatus.UNMATCHED -> unmatchedReason != UnmatchedReason.FORGOTTEN
            PlaceStatus.MAPPED, PlaceStatus.ONLINE -> false
        }
}
