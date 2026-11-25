package com.wherepaid.app.feature.map.home

import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MapHomeTest {
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `핀 지름은 넓이가 금액에 비례하고 40~72 사이`() {
        assertEquals(72f, pinDiameterDp(61_500, 61_500), 0.01f)
        assertEquals(72f * kotlin.math.sqrt(0.5f), pinDiameterDp(20_000, 40_000), 0.01f) // 절반 금액 → 넓이 절반 (지름 약 51)
        assertEquals(40f, pinDiameterDp(100, 61_500), 0.01f) // 아무리 작아도 40
        assertEquals(40f, pinDiameterDp(0, 0), 0.01f)
    }

    @Test
    fun `이름표는 브랜드만`() {
        assertEquals("스타벅스", brandName("스타벅스 성수역점"))
        assertEquals("CU", brandName("CU 한성대점"))
        assertEquals("홍길동상회", brandName("홍길동상회"))
    }

    @Test
    fun `매장 핀은 매장별 합계, 느낌표 핀은 결제 위치`() {
        val state = toMapHomeUiState(month, sample, needsPlaceCount = 6, signals = MapSignals())

        val stores = state.pins.filterIsInstance<MapPin.Store>()
        val starbucks = stores.first { it.placeId == SampleData.starbucksSeongsu.id }
        assertEquals("스타벅스", starbucks.name)
        assertEquals("3.1만", starbucks.amount) // 31,300원 (취소 반영)
        assertEquals(72f, starbucks.diameterDp, 0.01f) // 그 달 가장 많이 쓴 매장
        assertEquals(sample.count { it.status == PlaceStatus.AMBIGUOUS }, state.pins.count { it is MapPin.Ambiguous })
        assertEquals(6, state.needsPlaceCount)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `총액은 취소 반영, 온라인이 없으면 null`() {
        val state = toMapHomeUiState(month, sample, 0, MapSignals())
        val online = sample.filter { it.status == PlaceStatus.ONLINE }.sumOf { it.netAmount }
        assertEquals(String.format("%,d원", online), state.onlineTotal)

        val offlineOnly = toMapHomeUiState(month, sample.filter { it.status != PlaceStatus.ONLINE }, 0, MapSignals())
        assertNull(offlineOnly.onlineTotal)
    }

    @Test
    fun `결제가 없으면 빈 상태`() {
        val state = toMapHomeUiState(month, emptyList(), 0, MapSignals())
        assertTrue(state.isEmpty)
        assertTrue(state.pins.isEmpty())
        assertEquals("0원", state.offlineTotal)
    }

    @Test
    fun `배너는 하나만, 자동 기록 꺼짐이 가장 우선`() {
        val all = MapSignals(autoRecordEnabled = false, locationAlwaysAllowed = false, arrivedReport = month, isOnline = false)
        assertEquals(MapBannerKind.AutoRecordOff, bannerFor(all))
        assertEquals(MapBannerKind.LocationNotAlways, bannerFor(all.copy(autoRecordEnabled = true)))
        assertEquals(MapBannerKind.ReportArrived, bannerFor(all.copy(autoRecordEnabled = true, locationAlwaysAllowed = true)))
        assertEquals(MapBannerKind.Offline, bannerFor(MapSignals(isOnline = false)))
        assertNull(bannerFor(MapSignals()))
    }

    @Test
    fun `달을 바꾸면 그 달 결제로 다시 계산한다`() = runTest {
        val vm = MapHomeViewModel(FakeTransactionRepository(sample), MutableStateFlow(MapSignals()), currentMonth = { month })
        val job = launch { vm.state.collect {} }

        assertFalse(vm.state.first { !it.loading }.isEmpty)
        vm.selectMonth(month.minusMonths(1))
        val previous = vm.state.first { it.month == month.minusMonths(1) }
        assertTrue(previous.isEmpty)
        assertEquals(12, vm.selectableMonths.size)
        job.cancel()
    }
}
