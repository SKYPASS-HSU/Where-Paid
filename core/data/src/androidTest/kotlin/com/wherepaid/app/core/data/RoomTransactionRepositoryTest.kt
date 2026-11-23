package com.wherepaid.app.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wherepaid.app.core.data.local.WherePaidDatabase
import com.wherepaid.app.core.model.MonthSummary
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.UserDesignation
import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Room 구현이 가짜 구현과 같은 규칙(분석서 2-3 상태 이동표, FR-01 취소)으로 동작하는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class RoomTransactionRepositoryTest {
    private val month = YearMonth.of(2026, 10)
    private val sample = SampleData.transactions(month)
    private lateinit var db: WherePaidDatabase
    private lateinit var repository: RoomTransactionRepository

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, WherePaidDatabase::class.java).build()
        repository = RoomTransactionRepository(db)
        sample.forEach { assertTrue(repository.add(it)) }
    }

    @After
    fun tearDown() = db.close()

    private fun idOf(merchant: String) = sample.first { it.merchantRaw == merchant }.id

    @Test
    fun savedTransactionsReadBackLikeTheFake() = runTest {
        val fromRoom = repository.observeMonth(month).first()
        val fromFake = FakeTransactionRepository(sample).observeMonth(month).first()

        assertEquals(fromFake, fromRoom)
        assertEquals(MonthSummary.of(fromFake), MonthSummary.of(fromRoom))
        assertEquals(6, repository.observeNeedsPlace().first().size)
    }

    @Test
    fun forgottenLeavesPlaceCheckButStaysInTotal() = runTest {
        val id = idOf("GS25")
        val before = MonthSummary.of(repository.observeMonth(month).first())

        repository.markForgotten(id)

        assertEquals(UnmatchedReason.FORGOTTEN, repository.get(id)!!.unmatchedReason)
        assertEquals(5, repository.observeNeedsPlace().first().size)
        assertEquals(before.offlineTotal, MonthSummary.of(repository.observeMonth(month).first()).offlineTotal)
        assertNull(repository.learnedPlace("GS25"))
    }

    @Test
    fun clearAllRemovesEverything() = runTest {
        repository.choosePlace(idOf("GS25"), SampleData.gs25Candidates[0])
        repository.markOnline(idOf("쿠팡"))

        repository.clearAll()

        assertTrue(repository.observeMonth(month).first().isEmpty())
        assertTrue(repository.observeNeedsPlace().first().isEmpty())
        assertNull(repository.learnedPlace("GS25"))
        assertNull(repository.designation("쿠팡"))
        // 초기화 뒤에도 같은 알림을 다시 저장할 수 있다
        assertTrue(repository.add(sample.first()))
    }

    @Test
    fun duplicateNotificationIsIgnored() = runTest {
        assertFalse(repository.add(sample.first().copy(id = "other")))
    }

    @Test
    fun choosingCandidateMapsAndLearns() = runTest {
        val id = idOf("GS25")
        val chosen = SampleData.gs25Candidates[2]

        repository.choosePlace(id, chosen)

        val tx = repository.get(id)!!
        assertEquals(PlaceStatus.MAPPED, tx.status)
        assertEquals(chosen, tx.place)
        assertTrue(tx.candidates.isEmpty())
        assertEquals(chosen, repository.learnedPlace("GS25"))
    }

    @Test
    fun onlineAndOfflineDesignations() = runTest {
        val id = idOf("쿠팡")

        repository.markOffline(id)
        assertEquals(UnmatchedReason.MARKED_OFFLINE, repository.get(id)!!.unmatchedReason)
        assertEquals(UserDesignation.OFFLINE, repository.designation("쿠팡"))

        repository.markOnline(id)
        assertEquals(PlaceStatus.ONLINE, repository.get(id)!!.status)
        assertEquals(UserDesignation.ONLINE, repository.designation("쿠팡"))
    }

    @Test
    fun cancellationAttachesToOriginalPayment() = runTest {
        assertTrue(repository.addCancellation("올리브영 성수점", 24_000, sample.first().paidAt))
        assertEquals(0L, repository.get(idOf("올리브영 성수점"))!!.netAmount)
        assertFalse(repository.addCancellation("없는가게", 1_000, sample.first().paidAt))
    }

    @Test
    fun deleteKeepsLearnedPlace() = runTest {
        val id = idOf("GS25")
        repository.choosePlace(id, SampleData.gs25Candidates[0])

        repository.delete(id)

        assertNull(repository.get(id))
        assertEquals(SampleData.gs25Candidates[0], repository.learnedPlace("GS25"))
    }
}
