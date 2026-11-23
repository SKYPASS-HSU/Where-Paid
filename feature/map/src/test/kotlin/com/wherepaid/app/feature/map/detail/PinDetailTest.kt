package com.wherepaid.app.feature.map.detail

import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class PinDetailTest {
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)
    private val starbucks = SampleData.starbucksSeongsu.id

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun shown() = toPinDetailUiState(starbucks, month, sample, month.atDay(12)) as PinDetailUiState.Shown

    @Test
    fun `머리글은 매장명, 카테고리, 방문 수, 취소 반영 합계`() {
        val state = shown()
        assertEquals("스타벅스 성수역점", state.placeName)
        assertEquals("카페 · 10월에 5번 방문", state.subtitle) // 취소 줄은 방문으로 세지 않는다
        assertEquals("31,300원", state.total)
    }

    @Test
    fun `취소는 원래 결제와 별개의 줄로, 시각 순서대로 섞인다`() {
        val rows = shown().rows
        assertEquals(6, rows.size) // 결제 5 + 취소 1
        assertEquals(rows.sortedByDescending { it.dateTime }, rows) // 같은 달이라 "MM.dd" 문자열 순서 = 시각 순서

        val cancel = rows.single { it.isCancellation }
        assertEquals("10.08 (목) 18:40", cancel.dateTime)
        assertEquals("KB국민체크 · 결제 취소", cancel.detail)
        assertEquals("-4,500원", cancel.amount)
        // 취소 줄의 메뉴도 원래 결제를 다룬다
        val original = rows.single { !it.isCancellation && it.dateTime == "10.08 (목) 18:31" }
        assertEquals(original.transactionId, cancel.transactionId)
    }

    @Test
    fun `삭제 확인 칸은 매장, 금액, 시각`() {
        val row = shown().rows.first { !it.isCancellation }
        assertTrue(row.deleteSummary.matches(Regex("스타벅스 성수역점 · [0-9,]+원 · \\d\\d\\.\\d\\d \\d\\d:\\d\\d")))
    }

    @Test
    fun `카테고리는 마지막 칸`() {
        assertEquals("카페", categoryLabel("음식점 > 카페"))
        assertEquals("편의점", categoryLabel("편의점"))
        assertNull(categoryLabel(null))
    }

    @Test
    fun `이 매장 결제가 없으면 닫는다`() {
        assertEquals(PinDetailUiState.Gone, toPinDetailUiState("fake-nowhere", month, sample, month.atDay(12)))
    }

    @Test
    fun `삭제하면 줄과 합계가 줄고, 학습기록은 남는다`() = runTest {
        val repository = FakeTransactionRepository(sample)
        repository.choosePlace(sample.first { it.merchantRaw == "GS25" }.id, SampleData.gs25Candidates[0])
        val vm = PinDetailViewModel(repository, SampleData.gs25Candidates[0].id, month, today = { month.atDay(12) })
        val job = launch { vm.state.collect {} }

        val before = vm.state.first { it is PinDetailUiState.Shown } as PinDetailUiState.Shown
        assertEquals("3,400원", before.total)
        vm.delete(before.rows.single().transactionId).join()

        assertEquals(PinDetailUiState.Gone, vm.state.first { it !is PinDetailUiState.Shown })
        assertEquals(SampleData.gs25Candidates[0], repository.learnedPlace("GS25"))
        job.cancel()
    }
}
