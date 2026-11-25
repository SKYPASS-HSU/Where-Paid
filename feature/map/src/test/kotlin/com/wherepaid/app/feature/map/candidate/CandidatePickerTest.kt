package com.wherepaid.app.feature.map.candidate

import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CandidatePickerTest {
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)
    private val gs25 = sample.first { it.merchantRaw == "GS25" }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `후보는 가까운 순이고 요약은 시안 형식이다`() {
        val state = toCandidatePickerUiState(gs25, today = month.atDay(11)) as CandidatePickerUiState.Picking

        assertEquals("GS25 · 3,400원 · 오늘 08:41", state.summary)
        assertEquals(listOf("GS25 한성대점", "GS25 삼선교점", "GS25 성북로점"), state.candidates.map { it.place.name })
        assertEquals(listOf("39m", "71m", "186m"), state.candidates.map { it.distance })
    }

    @Test
    fun `위치가 없으면 거리를 보여주지 않는다`() {
        val state = toCandidatePickerUiState(gs25.copy(location = null), month.atDay(11)) as CandidatePickerUiState.Picking
        assertNull(state.candidates.first().distance)
    }

    @Test
    fun `느낌표 핀이 아니거나 없는 결제면 닫는다`() {
        assertEquals(CandidatePickerUiState.Done, toCandidatePickerUiState(null, month.atDay(11)))
        val mapped = sample.first { it.status == PlaceStatus.MAPPED }
        assertEquals(CandidatePickerUiState.Done, toCandidatePickerUiState(mapped, month.atDay(11)))
    }

    @Test
    fun `버튼은 상태 이동표대로 저장한다`() = runTest {
        val repository = FakeTransactionRepository(sample)
        val ediya = sample.first { it.merchantRaw == "이디야커피" }

        CandidatePickerViewModel(repository, gs25.id).choose(SampleData.gs25Candidates[1]).join()
        assertEquals(PlaceStatus.MAPPED, repository.get(gs25.id)!!.status)
        assertEquals(SampleData.gs25Candidates[1], repository.learnedPlace("GS25"))

        CandidatePickerViewModel(repository, ediya.id).markNotHere().join()
        assertEquals(UnmatchedReason.NOT_HERE, repository.get(ediya.id)!!.unmatchedReason)

        repository.updateMatch(ediya.id, PlaceStatus.AMBIGUOUS, null, SampleData.ediyaCandidates, null)
        CandidatePickerViewModel(repository, ediya.id).markOnline().join()
        assertEquals(PlaceStatus.ONLINE, repository.get(ediya.id)!!.status)
    }

    @Test
    fun `기억 안 나요는 장소 확인에서 빼고 시트를 닫는다`() = runTest {
        val repository = FakeTransactionRepository(sample)

        CandidatePickerViewModel(repository, gs25.id).markForgotten().join()

        val tx = repository.get(gs25.id)!!
        assertEquals(UnmatchedReason.FORGOTTEN, tx.unmatchedReason)
        assertEquals(CandidatePickerUiState.Done, toCandidatePickerUiState(tx, month.atDay(11)))
    }
}
