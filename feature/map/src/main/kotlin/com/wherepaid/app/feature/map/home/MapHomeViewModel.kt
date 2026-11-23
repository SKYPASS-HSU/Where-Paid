package com.wherepaid.app.feature.map.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/**
 * S-02 지도 홈. 고른 달의 결제, 장소 확인 대상 수, 배너 신호를 화면 상태로 합친다.
 * 모두 기기 DB 값이라 오프라인에서도 핀과 총액이 나온다 (분석서 NFR-02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapHomeViewModel(
    repository: TransactionRepository,
    signals: Flow<MapSignals>,
    currentMonth: () -> YearMonth = { YearMonth.now(KOREA_ZONE) },
) : ViewModel() {
    private val month = MutableStateFlow(currentMonth())

    /** 월 선택 목록: 이번 달부터 11개월 전까지. */
    val selectableMonths: List<YearMonth> = (0L until 12L).map { currentMonth().minusMonths(it) }

    val state: StateFlow<MapHomeUiState> = combine(
        month,
        month.flatMapLatest { repository.observeMonth(it) },
        repository.observeNeedsPlace(),
        signals,
    ) { m, transactions, needsPlace, s ->
        toMapHomeUiState(m, transactions, needsPlace.size, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapHomeUiState(month.value))

    fun selectMonth(value: YearMonth) {
        month.value = value
    }
}
