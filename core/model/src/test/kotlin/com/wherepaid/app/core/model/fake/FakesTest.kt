package com.wherepaid.app.core.model.fake

import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.LocationFailure
import com.wherepaid.app.core.model.LocationResult
import com.wherepaid.app.core.model.MonthSummary
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.UserDesignation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class FakesTest {
    private val month = YearMonth.of(2026, 10)
    private fun repository() = FakeTransactionRepository(SampleData.transactions(month))
    private fun idOf(merchant: String) = SampleData.transactions(month).first { it.merchantRaw == merchant }.id

    @Test
    fun `샘플 데이터에 다섯 가지 상태가 모두 있다`() {
        val statuses = SampleData.transactions(month).map { it.status }.toSet()
        assertEquals(PlaceStatus.entries.toSet(), statuses)
    }

    @Test
    fun `달 조회는 그 달 결제만 최근 순으로 준다`() = runTest {
        val repository = repository()
        val october = repository.observeMonth(month).first()

        assertEquals(SampleData.transactions(month).size, october.size)
        assertEquals(october.sortedByDescending { it.paidAt }, october)
        assertTrue(repository.observeMonth(month.plusMonths(1)).first().isEmpty())
    }

    @Test
    fun `후보를 고르면 지도 핀이 되고 학습기록이 남는다`() = runTest {
        val repository = repository()
        val id = idOf("GS25")
        val chosen = SampleData.gs25Candidates[1]

        repository.choosePlace(id, chosen)

        val tx = repository.get(id)!!
        assertEquals(PlaceStatus.MAPPED, tx.status)
        assertEquals(chosen, tx.place)
        assertTrue(tx.candidates.isEmpty())
        assertEquals(chosen, repository.learnedPlace("GS25"))
        assertFalse(repository.observeNeedsPlace().first().any { it.id == id })
    }

    @Test
    fun `여기 아니에요는 매칭실패함으로 보낸다`() = runTest {
        val repository = repository()
        val id = idOf("이디야커피")

        repository.markNotHere(id)

        val tx = repository.get(id)!!
        assertEquals(PlaceStatus.UNMATCHED, tx.status)
        assertEquals(UnmatchedReason.NOT_HERE, tx.unmatchedReason)
    }

    @Test
    fun `온라인 지정과 오프라인 지정은 상태와 사용자 지정을 함께 바꾼다`() = runTest {
        val repository = repository()
        val id = idOf("홍길동상회")

        repository.markOnline(id)
        assertEquals(PlaceStatus.ONLINE, repository.get(id)!!.status)
        assertEquals(UserDesignation.ONLINE, repository.designation("홍길동상회"))

        repository.markOffline(id)
        assertEquals(PlaceStatus.UNMATCHED, repository.get(id)!!.status)
        assertEquals(UnmatchedReason.MARKED_OFFLINE, repository.get(id)!!.unmatchedReason)
        assertEquals(UserDesignation.OFFLINE, repository.designation("홍길동상회"))
    }

    @Test
    fun `삭제하면 총액에서 빠지고 학습기록은 남는다`() = runTest {
        val repository = repository()
        val id = idOf("GS25")
        repository.choosePlace(id, SampleData.gs25Candidates[0])
        val before = MonthSummary.of(repository.observeMonth(month).first()).offlineTotal

        repository.delete(id)

        val after = MonthSummary.of(repository.observeMonth(month).first()).offlineTotal
        assertEquals(before - 3_400, after)
        assertEquals(SampleData.gs25Candidates[0], repository.learnedPlace("GS25"))
    }

    @Test
    fun `기억 안 나요는 장소 확인에서 빼고 총액에는 남긴다`() = runTest {
        val repository = repository()
        val id = idOf("GS25")
        val before = MonthSummary.of(repository.observeMonth(month).first())

        repository.markForgotten(id)

        val tx = repository.get(id)!!
        assertEquals(PlaceStatus.UNMATCHED, tx.status)
        assertEquals(UnmatchedReason.FORGOTTEN, tx.unmatchedReason)
        assertFalse(tx.needsPlace)
        assertFalse(repository.observeNeedsPlace().first().any { it.id == id })
        val after = MonthSummary.of(repository.observeMonth(month).first())
        assertEquals(before.offlineTotal, after.offlineTotal)
        assertEquals(before.needsPlaceCount - 1, after.needsPlaceCount)
        assertNull(repository.learnedPlace("GS25"))
        assertNull(repository.designation("GS25"))
    }

    @Test
    fun `기억 안 나요로 넘긴 결제도 나중에 장소를 고를 수 있다`() = runTest {
        val repository = repository()
        val id = idOf("GS25")
        repository.markForgotten(id)

        repository.choosePlace(id, SampleData.gs25Candidates[0])

        assertEquals(PlaceStatus.MAPPED, repository.get(id)!!.status)
        assertNull(repository.get(id)!!.unmatchedReason)
    }

    @Test
    fun `초기화하면 결제와 학습기록과 사용자 지정이 모두 사라진다`() = runTest {
        val repository = repository()
        repository.choosePlace(idOf("GS25"), SampleData.gs25Candidates[0])
        repository.markOnline(idOf("홍길동상회"))

        repository.clearAll()

        assertTrue(repository.observeMonth(month).first().isEmpty())
        assertTrue(repository.observeNeedsPlace().first().isEmpty())
        assertNull(repository.learnedPlace("GS25"))
        assertNull(repository.designation("홍길동상회"))
    }

    @Test
    fun `같은 알림 재전달은 저장하지 않는다`() = runTest {
        val repository = repository()
        val existing = SampleData.transactions(month).first()

        assertFalse(repository.add(existing.copy(id = "new-id")))
        assertTrue(repository.add(existing.copy(id = "new-id", dedupeKey = "another")))
    }

    @Test
    fun `취소는 원래 결제에 붙고 붙일 결제가 없으면 기록하지 않는다`() = runTest {
        val repository = repository()
        val at = month.atDay(12).atStartOfDay(KOREA_ZONE).toInstant().toEpochMilli()

        assertTrue(repository.addCancellation("올리브영 성수점", 24_000, at))
        assertEquals(0L, repository.get(idOf("올리브영 성수점"))!!.netAmount)
        assertFalse(repository.addCancellation("없는가게", 1_000, at))
    }

    @Test
    fun `장소 검색은 이름이 맞는 장소를, 오프라인이면 예외를 준다`() = runTest {
        val resolver = FakePlaceResolver()
        assertEquals(3, resolver.search("GS25", SampleData.SEONGSU).size)
        assertTrue(resolver.search("없는가게", null).isEmpty())

        resolver.offline = true
        val failed = runCatching { resolver.search("GS25", null) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `위치 제공자는 설정한 결과를 돌려준다`() = runTest {
        val provider = FakeLocationProvider()
        assertTrue(provider.capture() is LocationResult.Success)

        provider.result = LocationResult.Failure(LocationFailure.TIMEOUT)
        assertEquals(LocationResult.Failure(LocationFailure.TIMEOUT), provider.capture())
    }

    @Test
    fun `학습기록이 없으면 null`() = runTest {
        assertNull(repository().learnedPlace("GS25"))
    }
}
