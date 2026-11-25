package com.wherepaid.app.feature.map.home

import com.wherepaid.app.core.designsystem.format.formatManwon
import com.wherepaid.app.core.designsystem.format.formatWon
import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.MonthSummary
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.Transaction
import java.time.YearMonth
import kotlin.math.sqrt

/** S-02 지도 홈이 그리는 것. [toMapHomeUiState]로 만든다. */
data class MapHomeUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    /** "312,400원" 온라인을 뺀 그 달 소비 (느낌표 핀·매칭실패함·매칭 대기 포함, 취소 반영) */
    val offlineTotal: String = "",
    /** "88,900원". 온라인 결제가 없으면 null */
    val onlineTotal: String? = null,
    val pins: List<MapPin> = emptyList(),
    /** "장소 확인이 필요한 결제" 칩 숫자. 0이면 칩을 숨긴다. */
    val needsPlaceCount: Int = 0,
    val banner: MapBannerKind? = null,
    /** 리포트 도착 배너의 달 */
    val arrivedReport: YearMonth? = null,
    /** 그 달 결제가 하나도 없음 → 빈 상태 카드 (시안 60:7) */
    val isEmpty: Boolean = false,
    /** 빈 상태 카드의 "✓ 자동 기록 켜짐" */
    val autoRecordEnabled: Boolean = true,
)

/** 지도 위 핀. [latLng]가 핀 원의 중심. */
sealed interface MapPin {
    val latLng: LatLng

    /** 지도 핀: 매장 하나, 크기는 그 달 합계에 비례. */
    data class Store(
        val placeId: String,
        override val latLng: LatLng,
        /** 이름표: "스타벅스" */
        val name: String,
        /** 원 안: "6.2만" */
        val amount: String,
        val diameterDp: Float,
        val total: Long,
    ) : MapPin

    /** 느낌표 핀: 후보가 2곳 이상인 결제. 결제 위치에 찍는다. */
    data class Ambiguous(val transactionId: String, override val latLng: LatLng) : MapPin
}

/** 배너는 한 번에 하나만, 이 순서로 (위가 우선). */
enum class MapBannerKind { AutoRecordOff, LocationNotAlways, ReportArrived, Offline }

/** 배너를 고르는 데 필요한 바깥 상태. */
data class MapSignals(
    val autoRecordEnabled: Boolean = true,
    val locationAlwaysAllowed: Boolean = true,
    val arrivedReport: YearMonth? = null,
    val isOnline: Boolean = true,
)

fun toMapHomeUiState(
    month: YearMonth,
    monthTransactions: List<Transaction>,
    needsPlaceCount: Int,
    signals: MapSignals,
): MapHomeUiState {
    val summary = MonthSummary.of(monthTransactions)
    val maxTotal = summary.places.maxOfOrNull { it.total } ?: 0L
    val storePins = summary.places
        .filter { it.total > 0 } // 전액 취소된 매장은 핀을 그리지 않는다
        .map { total ->
            MapPin.Store(
                placeId = total.place.id,
                latLng = total.place.latLng,
                name = brandName(total.place.name),
                amount = formatManwon(total.total),
                diameterDp = pinDiameterDp(total.total, maxTotal),
                total = total.total,
            )
        }
    val ambiguousPins = monthTransactions
        .filter { it.status == PlaceStatus.AMBIGUOUS && it.location != null }
        .map { MapPin.Ambiguous(it.id, it.location!!) }
    return MapHomeUiState(
        month = month,
        loading = false,
        offlineTotal = formatWon(summary.offlineTotal),
        onlineTotal = summary.onlineTotal.takeIf { it > 0 }?.let(::formatWon),
        pins = storePins + ambiguousPins,
        needsPlaceCount = needsPlaceCount,
        banner = bannerFor(signals),
        arrivedReport = signals.arrivedReport,
        isEmpty = monthTransactions.isEmpty(),
        autoRecordEnabled = signals.autoRecordEnabled,
    )
}

fun bannerFor(signals: MapSignals): MapBannerKind? = when {
    !signals.autoRecordEnabled -> MapBannerKind.AutoRecordOff
    !signals.locationAlwaysAllowed -> MapBannerKind.LocationNotAlways
    signals.arrivedReport != null -> MapBannerKind.ReportArrived
    !signals.isOnline -> MapBannerKind.Offline
    else -> null
}

const val PIN_MIN_DIAMETER_DP = 40f
const val PIN_MAX_DIAMETER_DP = 72f

/**
 * 매장 핀 지름 (분석서 FR-02 "핀 크기는 그 매장의 그달 합계에 비례").
 * 원의 **넓이**가 금액에 비례하도록 지름은 제곱근으로 키우고, 가장 큰 매장이 72, 아무리 작아도 40.
 */
fun pinDiameterDp(total: Long, maxTotal: Long): Float {
    if (maxTotal <= 0 || total <= 0) return PIN_MIN_DIAMETER_DP
    val ratio = (total.toDouble() / maxTotal).coerceIn(0.0, 1.0)
    return (PIN_MAX_DIAMETER_DP * sqrt(ratio)).toFloat().coerceIn(PIN_MIN_DIAMETER_DP, PIN_MAX_DIAMETER_DP)
}

/** 핀 이름표는 브랜드만: "스타벅스 성수역점" → "스타벅스", "CU 한성대점" → "CU". */
fun brandName(placeName: String): String = placeName.trim().substringBefore(' ').ifEmpty { placeName }
