package com.wherepaid.app.core.model

import com.wherepaid.app.core.model.fake.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class RulesTest {
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)

    private fun payment(id: String, amount: Long, paidAt: Long, canceled: Long = 0) = sample.first().copy(
        id = id, merchantRaw = "샘플가게", amount = amount, paidAt = paidAt,
        cancellations = if (canceled > 0) listOf(Cancellation(canceled, paidAt + 1)) else emptyList(),
    )

    @Test
    fun `취소는 금액이 같은 결제를 우선한다`() {
        val payments = listOf(payment("a", 5_000, 3), payment("b", 3_000, 2), payment("c", 9_000, 1))
        assertEquals("b", CancellationRules.findTarget(payments, "샘플가게", 3_000)?.id)
    }

    @Test
    fun `금액이 같은 결제가 없으면 남은 금액이 충분한 가장 최근 결제`() {
        val payments = listOf(payment("old", 9_000, 1), payment("new", 5_000, 2))
        assertEquals("new", CancellationRules.findTarget(payments, "샘플가게", 4_000)?.id)
    }

    @Test
    fun `이미 취소된 금액을 빼고 남은 금액이 모자라면 대상이 아니다`() {
        val payments = listOf(payment("a", 5_000, 1, canceled = 5_000))
        assertNull(CancellationRules.findTarget(payments, "샘플가게", 5_000))
    }

    @Test
    fun `월 합계는 취소를 반영하고 온라인을 따로 센다`() {
        val summary = MonthSummary.of(sample)
        val online = sample.filter { it.status == PlaceStatus.ONLINE }.sumOf { it.amount }
        val offline = sample.filter { it.status != PlaceStatus.ONLINE }.sumOf { it.amount } - 4_500

        assertEquals(online, summary.onlineTotal)
        assertEquals(offline, summary.offlineTotal)
        assertEquals(6, summary.needsPlaceCount)
        assertEquals(SampleData.starbucksSeongsu, summary.places.first().place)
        // 6,800 + 5,300 + (4,500 − 취소 4,500) + 7,200 + 12,000
        assertEquals(31_300L, summary.places.first().total)
        assertEquals(5, summary.places.first().count)
    }

    @Test
    fun `상태 이동은 후보와 이유를 정리한다`() {
        val ambiguous = sample.first { it.status == PlaceStatus.AMBIGUOUS }
        val mapped = PlaceTransitions.choosePlace(ambiguous, ambiguous.candidates.first())

        assertEquals(PlaceStatus.MAPPED, mapped.status)
        assertTrue(mapped.candidates.isEmpty())
        assertNull(mapped.unmatchedReason)
        assertEquals(UnmatchedReason.MARKED_OFFLINE, PlaceTransitions.markOffline(mapped).unmatchedReason)
    }

    @Test
    fun `거리 계산`() {
        val d = distanceMeters(LatLng(37.5665, 126.9780), LatLng(37.5665, 126.9790))
        assertTrue(d in 85.0..95.0)
    }

    @Test
    fun `카카오 지번 주소에서 동을 뽑는다`() {
        assertEquals("성수동", Neighborhoods.fromAddress("서울 성동구 성수동2가 280"))
        assertEquals("역삼동", Neighborhoods.fromAddress("서울 강남구 역삼1동 10-3"))
        assertEquals("정자동", Neighborhoods.fromAddress("경기 성남시 분당구 정자동 1"))
        assertEquals("종로", Neighborhoods.fromAddress("서울 종로구 종로1가 1"))
        assertNull(Neighborhoods.fromAddress("서울 성동구 아차산로 100"))
        assertNull(Neighborhoods.fromAddress("서울 강남구 테헤란로4길 6"))
        assertNull(Neighborhoods.fromAddress(""))
        assertNull(Neighborhoods.fromAddress(null))
    }

    @Test
    fun `가장 많이 쓴 동은 지도 핀 결제의 취소 반영 합계로 고른다`() {
        val seongsu = Region("서울특별시", "성동구", "성수동")
        val samseon = Region("서울특별시", "성북구", "삼선동")
        fun mapped(id: String, amount: Long, region: Region?, canceled: Long = 0) =
            payment(id, amount, 1, canceled).copy(status = PlaceStatus.MAPPED, region = region)

        val txs = listOf(
            mapped("a", 10_000, seongsu, canceled = 6_000), // 성수동 4,000
            mapped("b", 5_000, samseon), // 삼선동 5,000
            mapped("c", 9_000, null), // 동 모름
            payment("d", 50_000, 1).copy(status = PlaceStatus.ONLINE, region = seongsu), // 지도 핀 아님
        )

        assertEquals("삼선동", Neighborhoods.top(txs))
        assertNull(Neighborhoods.top(txs.filter { it.region == null }))
    }
}
