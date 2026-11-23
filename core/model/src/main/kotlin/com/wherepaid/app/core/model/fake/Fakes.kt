package com.wherepaid.app.core.model.fake

import com.wherepaid.app.core.model.Cancellation
import com.wherepaid.app.core.model.CancellationRules
import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.LocationProvider
import com.wherepaid.app.core.model.LocationResult
import com.wherepaid.app.core.model.ParsedPayment
import com.wherepaid.app.core.model.PaymentSource
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceResolver
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.PlaceTransitions
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.UserDesignation
import com.wherepaid.app.core.model.distanceMeters
import com.wherepaid.app.core.model.endMillisExclusive
import com.wherepaid.app.core.model.startMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/**
 * 가짜 구현. 실제 구현이 없어도 화면과 테스트를 만들 수 있게 한다.
 * 데이터는 [SampleData]에 있고 모두 메모리에만 있다 (앱을 다시 켜면 초기화).
 */

/** [emit]으로 결제를 직접 흘려보낸다. */
class FakePaymentSource : PaymentSource {
    private val flow = MutableSharedFlow<ParsedPayment>(extraBufferCapacity = 16)
    override val payments: Flow<ParsedPayment> = flow

    suspend fun emit(payment: ParsedPayment) = flow.emit(payment)
}

/** 항상 [result]를 돌려준다. */
class FakeLocationProvider(
    var result: LocationResult = LocationResult.Success(SampleData.SEONGSU, accuracyM = 12.5f),
) : LocationProvider {
    override suspend fun capture(): LocationResult = result
}

/**
 * 이름이 들어 있는 샘플 장소를 돌려준다. [offline]이면 네트워크 없음처럼 예외를 던진다.
 */
class FakePlaceResolver(
    private val places: List<Place> = SampleData.places,
    var offline: Boolean = false,
) : PlaceResolver {
    override suspend fun resolve(merchant: String, near: LatLng?): List<Place> =
        search(merchant, near)

    override suspend fun search(query: String, near: LatLng?): List<Place> {
        if (offline) throw java.io.IOException("오프라인 (가짜)")
        val key = (SampleData.aliases[query.trim()] ?: query).replace(" ", "")
        return places
            .filter { key.isNotEmpty() && it.name.replace(" ", "").contains(key, ignoreCase = true) }
            .sortedBy { place -> near?.let { distanceMeters(it, place.latLng) } ?: 0.0 }
    }
}

class FakeTransactionRepository(
    transactions: List<Transaction> = SampleData.transactions(),
    learned: Map<String, Place> = emptyMap(),
) : TransactionRepository {
    private val transactions = MutableStateFlow(transactions.associateBy { it.id })
    private val learned = MutableStateFlow(learned)
    private val designations = MutableStateFlow<Map<String, UserDesignation>>(emptyMap())

    private val all get() = transactions.value.values

    override fun observeMonth(month: YearMonth): Flow<List<Transaction>> = transactions.map { map ->
        map.values
            .filter { it.paidAt >= month.startMillis() && it.paidAt < month.endMillisExclusive() }
            .sortedByDescending { it.paidAt }
    }

    override fun observeNeedsPlace(): Flow<List<Transaction>> =
        transactions.map { map -> map.values.filter { it.needsPlace }.sortedByDescending { it.paidAt } }

    override fun observe(id: String): Flow<Transaction?> = transactions.map { it[id] }

    override suspend fun get(id: String): Transaction? = transactions.value[id]

    override suspend fun add(transaction: Transaction): Boolean {
        val key = transaction.dedupeKey
        if (key != null && all.any { it.dedupeKey == key }) return false
        put(transaction)
        return true
    }

    override suspend fun addCancellation(merchant: String, amount: Long, canceledAt: Long): Boolean {
        val target = CancellationRules.findTarget(all.toList(), merchant, amount) ?: return false
        put(target.copy(cancellations = target.cancellations + Cancellation(amount, canceledAt)))
        return true
    }

    override suspend fun updateMatch(
        id: String,
        status: PlaceStatus,
        place: Place?,
        candidates: List<Place>,
        unmatchedReason: UnmatchedReason?,
    ) = modify(id) { it.copy(status = status, place = place, candidates = candidates, unmatchedReason = unmatchedReason) }

    override suspend fun pendingSearch(): List<Transaction> = all.filter { it.status == PlaceStatus.PENDING_SEARCH }

    override suspend fun learnedPlace(merchant: String): Place? = learned.value[merchant]

    override suspend fun designation(merchant: String): UserDesignation? = designations.value[merchant]

    override suspend fun choosePlace(id: String, place: Place) {
        val tx = get(id) ?: return
        put(PlaceTransitions.choosePlace(tx, place))
        learned.update { it + (tx.merchantRaw to place) }
    }

    override suspend fun markNotHere(id: String) = modify(id, PlaceTransitions::markNotHere)

    override suspend fun markOnline(id: String) {
        val tx = get(id) ?: return
        put(PlaceTransitions.markOnline(tx))
        designations.update { it + (tx.merchantRaw to UserDesignation.ONLINE) }
    }

    override suspend fun markOffline(id: String) {
        val tx = get(id) ?: return
        put(PlaceTransitions.markOffline(tx))
        designations.update { it + (tx.merchantRaw to UserDesignation.OFFLINE) }
    }

    override suspend fun delete(id: String) {
        transactions.update { it - id }
    }

    override suspend fun markForgotten(id: String) = modify(id, PlaceTransitions::markForgotten)

    override suspend fun clearAll() {
        transactions.value = emptyMap()
        learned.value = emptyMap()
        designations.value = emptyMap()
    }

    private fun put(tx: Transaction) = transactions.update { it + (tx.id to tx) }

    private fun modify(id: String, change: (Transaction) -> Transaction) =
        transactions.update { map -> map[id]?.let { map + (id to change(it)) } ?: map }
}
