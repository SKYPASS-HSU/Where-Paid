package com.wherepaid.app.feature.placecheck.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.PlaceResolver
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * S-06 매장 찾기. 매칭실패함의 [찾기]와 핀 상세의 [장소 변경]에서 연다.
 * 등록하면 지도 핀 + 학습기록 (분석서 2-3), [온라인 결제예요]면 온라인 목록 + 온라인 지정.
 */
@OptIn(FlowPreview::class)
class FindPlaceViewModel(
    private val repository: TransactionRepository,
    private val resolver: PlaceResolver,
    private val transactionId: String,
    today: () -> LocalDate = { LocalDate.now(KOREA_ZONE) },
    private val searchDelayMs: Long = 300,
) : ViewModel() {
    private val _state = MutableStateFlow(FindPlaceUiState())
    val state: StateFlow<FindPlaceUiState> = _state.asStateFlow()

    private var transaction: Transaction? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val tx = repository.get(transactionId)
            if (tx == null) {
                _state.update { it.copy(finished = true) }
                return@launch
            }
            transaction = tx
            // TODO(②): 분석서 UC-02 1a는 "별칭 변환 이름"을 미리 채운다. 별칭사전이 :core:place에 생기면 그 이름을 쓴다.
            _state.update { it.copy(summary = summaryOf(tx, today()), query = tx.merchantRaw) }
            search(immediately = true)
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        search(immediately = false)
    }

    /** 키보드의 검색, 오프라인 화면의 [다시 시도]. */
    fun searchNow() = search(immediately = true)

    fun select(placeId: String) = _state.update { it.copy(selectedId = placeId) }

    fun register() {
        val place = _state.value.selected?.place ?: return
        viewModelScope.launch {
            repository.choosePlace(transactionId, place)
            _state.update { it.copy(finished = true) }
        }
    }

    fun markOnline() {
        viewModelScope.launch {
            repository.markOnline(transactionId)
            _state.update { it.copy(finished = true) }
        }
    }

    /** [기억 안 나요] → 장소 확인 목록에서 빼고 닫는다 (총액에는 남음). */
    fun markForgotten() {
        viewModelScope.launch {
            repository.markForgotten(transactionId)
            _state.update { it.copy(finished = true) }
        }
    }

    /** 뒤로 가기. 고른 매장이 있으면 D-02로 한 번 묻는다. */
    fun onBack() = _state.update {
        if (it.selected != null) it.copy(showLeaveDialog = true) else it.copy(finished = true)
    }

    fun keepChoosing() = _state.update { it.copy(showLeaveDialog = false) }

    fun leave() = _state.update { it.copy(showLeaveDialog = false, finished = true) }

    private fun search(immediately: Boolean) {
        searchJob?.cancel()
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(results = SearchResults.Idle, selectedId = null) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediately) kotlinx.coroutines.delay(searchDelayMs)
            _state.update { it.copy(results = SearchResults.Loading, selectedId = null) }
            val results = try {
                val places = resolver.search(query, transaction?.location)
                if (places.isEmpty()) SearchResults.Empty(query) else SearchResults.Found(toFoundPlaces(places, transaction?.location))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchResults.Offline
            }
            _state.update { it.copy(results = results) }
        }
    }
}
