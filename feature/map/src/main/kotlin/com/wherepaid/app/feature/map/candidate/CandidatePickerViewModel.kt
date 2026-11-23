package com.wherepaid.app.feature.map.candidate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * S-04 후보 고르기. 버튼마다 분석서 2-3 상태 이동표의 느낌표 핀 줄을 따른다.
 * 저장한 뒤 결제가 더 이상 느낌표 핀이 아니므로 상태가 [CandidatePickerUiState.Done]이 되고 시트가 닫힌다.
 */
class CandidatePickerViewModel(
    private val repository: TransactionRepository,
    private val transactionId: String,
    today: () -> LocalDate = { LocalDate.now(KOREA_ZONE) },
) : ViewModel() {
    val state: StateFlow<CandidatePickerUiState> = repository.observe(transactionId)
        .map { toCandidatePickerUiState(it, today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CandidatePickerUiState.Loading)

    /** 후보 선택 → 지도 핀 + 학습기록 저장. */
    fun choose(place: Place) = viewModelScope.launch { repository.choosePlace(transactionId, place) }

    /** [온라인 결제예요] → 온라인 목록 + 온라인 지정. */
    fun markOnline() = viewModelScope.launch { repository.markOnline(transactionId) }

    /** [여기 아니에요] → 매칭실패함. */
    fun markNotHere() = viewModelScope.launch { repository.markNotHere(transactionId) }

    /** [기억 안 나요] → 장소 확인 목록에서 뺀다 (총액에는 남음). */
    fun markForgotten() = viewModelScope.launch { repository.markForgotten(transactionId) }
}
