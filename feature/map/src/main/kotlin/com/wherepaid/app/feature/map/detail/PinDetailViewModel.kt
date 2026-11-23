package com.wherepaid.app.feature.map.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * S-03 핀 상세 + D-01 삭제. 지도 홈에서 고른 달 기준으로 한 매장의 결제를 보여 준다.
 * [삭제]는 결제 기록을 지우고 학습기록은 남긴다 (분석서 FR-03, 2-3). [장소 변경]은 S-06 매장 찾기로 넘긴다.
 */
class PinDetailViewModel(
    private val repository: TransactionRepository,
    private val placeId: String,
    private val month: YearMonth,
    today: () -> LocalDate = { LocalDate.now(KOREA_ZONE) },
) : ViewModel() {
    val state: StateFlow<PinDetailUiState> = repository.observeMonth(month)
        .map { toPinDetailUiState(placeId, month, it, today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PinDetailUiState.Loading)

    fun delete(transactionId: String) = viewModelScope.launch { repository.delete(transactionId) }
}
