package com.wherepaid.app.feature.placecheck.find

import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.UserDesignation
import com.wherepaid.app.core.model.fake.FakePlaceResolver
import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class FindPlaceTest {
    private val dispatcher = StandardTestDispatcher()
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)
    private val kfc = sample.first { it.merchantRaw == "(주)케이에프씨코리" }
    private lateinit var repository: FakeTransactionRepository
    private lateinit var resolver: FakePlaceResolver

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTransactionRepository(sample)
        resolver = FakePlaceResolver()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): FindPlaceViewModel =
        FindPlaceViewModel(repository, resolver, kfc.id, today = { month.atDay(12) }).also { advanceUntilIdle() }

    @Test
    fun `가맹점명으로 바로 검색하고 결과에 번호를 붙인다`() = runTest(dispatcher) {
        val state = viewModel().state.value

        assertEquals("(주)케이에프씨코리 · 9,800원 · 10.09 19:20", state.summary)
        assertEquals("(주)케이에프씨코리", state.query)
        val found = state.results as SearchResults.Found
        assertEquals(listOf(1, 2, 3), found.places.map { it.number })
        assertEquals("성북구 샘플로", found.places.first().detail) // 결제 위치가 없어서 거리는 없다
        assertFalse(state.canRegister)
    }

    @Test
    fun `고르고 등록하면 지도 핀과 학습기록이 생기고 화면을 닫는다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.select(SampleData.kfcSungshin.id)
        assertTrue(vm.state.value.canRegister)

        vm.register()
        advanceUntilIdle()

        assertEquals(PlaceStatus.MAPPED, repository.get(kfc.id)!!.status)
        assertEquals(SampleData.kfcSungshin, repository.learnedPlace(kfc.merchantRaw))
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `고른 채로 뒤로 가면 D-02로 묻고, 계속 고르기면 남는다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.select(SampleData.kfcSungshin.id)

        vm.onBack()
        assertTrue(vm.state.value.showLeaveDialog)
        vm.keepChoosing()
        assertFalse(vm.state.value.finished)

        vm.onBack()
        vm.leave()
        assertTrue(vm.state.value.finished)
        assertEquals(PlaceStatus.UNMATCHED, repository.get(kfc.id)!!.status) // 아무것도 저장하지 않음
    }

    @Test
    fun `고른 것 없이 뒤로 가면 바로 닫는다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onBack()
        assertFalse(vm.state.value.showLeaveDialog)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `오프라인이면 오류, 다시 시도로 복구, 결과가 없으면 빈 상태`() = runTest(dispatcher) {
        resolver.offline = true
        val vm = viewModel()
        assertEquals(SearchResults.Offline, vm.state.value.results)

        resolver.offline = false
        vm.searchNow()
        advanceUntilIdle()
        assertTrue(vm.state.value.results is SearchResults.Found)

        vm.onQueryChange("없는가게")
        advanceUntilIdle()
        assertEquals(SearchResults.Empty("없는가게"), vm.state.value.results)

        vm.onQueryChange("")
        advanceUntilIdle()
        assertEquals(SearchResults.Idle, vm.state.value.results)
    }

    @Test
    fun `온라인 결제예요는 온라인 지정을 남기고 닫는다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.markOnline()
        advanceUntilIdle()

        assertEquals(PlaceStatus.ONLINE, repository.get(kfc.id)!!.status)
        assertEquals(UserDesignation.ONLINE, repository.designation(kfc.merchantRaw))
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `기억 안 나요는 장소 확인에서 빼고 닫는다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.markForgotten()
        advanceUntilIdle()

        assertEquals(UnmatchedReason.FORGOTTEN, repository.get(kfc.id)!!.unmatchedReason)
        assertFalse(repository.get(kfc.id)!!.needsPlace)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `주소는 시도와 번지를 뺀다`() {
        assertEquals("성북구 동소문로", shortAddress("서울 성북구 동소문로 123"))
        assertEquals("종로구 대학로", shortAddress("서울특별시 종로구 대학로 10-2"))
        assertEquals(null, shortAddress(null))
    }
}
