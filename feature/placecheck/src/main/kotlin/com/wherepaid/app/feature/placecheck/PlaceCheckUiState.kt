package com.wherepaid.app.feature.placecheck

import com.wherepaid.app.core.designsystem.format.formatShortDateTime
import com.wherepaid.app.core.designsystem.format.formatWon
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.UnmatchedReason
import java.time.LocalDate

/** S-05 장소 확인 화면이 그리는 것. [toPlaceCheckUiState]로 만든다. */
data class PlaceCheckUiState(
    val loading: Boolean = true,
    /** "후보 중에 골라 주세요" (느낌표 핀). */
    val candidates: List<PlaceCheckItem> = emptyList(),
    /** "직접 찾아 주세요" (매칭 대기가 먼저, 그다음 매칭실패함). */
    val manual: List<PlaceCheckItem> = emptyList(),
    /** 오프라인이고 매칭 대기 결제가 있을 때 아래쪽 안내. */
    val showOfflineNote: Boolean = false,
) {
    val total: Int get() = candidates.size + manual.size
    val isEmpty: Boolean get() = !loading && total == 0
}

enum class PlaceCheckKind { Candidates, Unmatched, PendingSearch }

data class PlaceCheckItem(
    val id: String,
    val kind: PlaceCheckKind,
    val merchant: String,
    /** "3,400원" */
    val amount: String,
    /** "오늘 08:41 · 근처 후보 3곳" */
    val detail: String,
)

/**
 * 저장소의 장소 확인 대상([Transaction.needsPlace])을 화면 상태로 바꾼다.
 * 분석서 1-6: 느낌표 핀 → "후보 중에 골라 주세요", 매칭실패함·매칭 대기 → "직접 찾아 주세요".
 */
fun toPlaceCheckUiState(
    transactions: List<Transaction>,
    isOnline: Boolean,
    today: LocalDate,
): PlaceCheckUiState {
    val needsPlace = transactions.filter { it.needsPlace }.sortedByDescending { it.paidAt }
    val candidates = needsPlace.filter { it.status == PlaceStatus.AMBIGUOUS }.map { it.toItem(today) }
    val pending = needsPlace.filter { it.status == PlaceStatus.PENDING_SEARCH }.map { it.toItem(today) }
    val unmatched = needsPlace.filter { it.status == PlaceStatus.UNMATCHED }.map { it.toItem(today) }
    return PlaceCheckUiState(
        loading = false,
        candidates = candidates,
        manual = pending + unmatched,
        showOfflineNote = !isOnline && pending.isNotEmpty(),
    )
}

private fun Transaction.toItem(today: LocalDate): PlaceCheckItem {
    val time = formatShortDateTime(paidAt, today)
    return when (status) {
        PlaceStatus.AMBIGUOUS -> item(PlaceCheckKind.Candidates, "$time · 근처 후보 ${candidates.size}곳")
        PlaceStatus.PENDING_SEARCH -> item(PlaceCheckKind.PendingSearch, "검색 대기 · 인터넷이 연결되면 자동으로 다시 찾아요")
        else -> item(PlaceCheckKind.Unmatched, "$time · ${unmatchedReason.label()}")
    }
}

private fun Transaction.item(kind: PlaceCheckKind, detail: String) =
    PlaceCheckItem(id = id, kind = kind, merchant = merchantRaw, amount = formatWon(netAmount), detail = detail)

/** 매칭실패함 보조 문구. 시안에 있는 것은 NO_LOCATION, NO_RESULT, MARKED_OFFLINE 세 가지. */
private fun UnmatchedReason?.label(): String = when (this) {
    UnmatchedReason.NO_LOCATION -> "위치를 못 받았어요"
    UnmatchedReason.UNTRUSTED_LOCATION -> "위치가 정확하지 않았어요"
    UnmatchedReason.NO_RESULT -> "근처에서 못 찾았어요"
    UnmatchedReason.NOT_HERE -> "후보 중에 없었어요"
    UnmatchedReason.MARKED_OFFLINE -> "오프라인 결제로 옮김"
    UnmatchedReason.FORGOTTEN -> "기억 안 나요" // needsPlace에서 빠져 목록에는 나오지 않는다
    null -> "장소를 못 찾았어요"
}
