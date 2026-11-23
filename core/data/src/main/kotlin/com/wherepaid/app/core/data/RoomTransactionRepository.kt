package com.wherepaid.app.core.data

import android.content.Context
import androidx.room.withTransaction
import com.wherepaid.app.core.data.local.CancellationEntity
import com.wherepaid.app.core.data.local.CandidateEntity
import com.wherepaid.app.core.data.local.DesignationEntity
import com.wherepaid.app.core.data.local.LearnedPlaceEntity
import com.wherepaid.app.core.data.local.WherePaidDatabase
import com.wherepaid.app.core.data.local.toEntity
import com.wherepaid.app.core.data.local.toModel
import com.wherepaid.app.core.model.CancellationRules
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.PlaceTransitions
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.UserDesignation
import com.wherepaid.app.core.model.endMillisExclusive
import com.wherepaid.app.core.model.startMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * 기기 DB(Room)에 저장하는 [TransactionRepository]. 이 모듈이 밖으로 내놓는 것은 이 함수 하나다.
 * 프로세스에서 한 번만 호출한다 (:app의 AppContainer).
 * 데이터는 Android 자동 백업으로 사용자 본인 Google 계정에 보존된다 (분석서 NFR-02).
 */
fun createTransactionRepository(context: Context): TransactionRepository =
    RoomTransactionRepository(WherePaidDatabase.create(context))

/** 장소 확인 후보 상태. [기억 안 나요]로 넘긴 매칭실패함은 읽은 뒤 [Transaction.needsPlace]로 거른다. */
private val NEEDS_PLACE = listOf(PlaceStatus.AMBIGUOUS, PlaceStatus.UNMATCHED, PlaceStatus.PENDING_SEARCH).map { it.name }

internal class RoomTransactionRepository(private val db: WherePaidDatabase) : TransactionRepository {
    private val transactions = db.transactionDao()
    private val places = db.placeDao()

    override fun observeMonth(month: YearMonth): Flow<List<Transaction>> =
        transactions.observeBetween(month.startMillis(), month.endMillisExclusive()).map { list -> list.map { it.toModel() } }

    override fun observeNeedsPlace(): Flow<List<Transaction>> =
        transactions.observeByStatus(NEEDS_PLACE).map { list -> list.map { it.toModel() }.filter { it.needsPlace } }

    override fun observe(id: String): Flow<Transaction?> = transactions.observe(id).map { it?.toModel() }

    override suspend fun get(id: String): Transaction? = transactions.get(id)?.toModel()

    override suspend fun add(transaction: Transaction): Boolean = db.withTransaction {
        val key = transaction.dedupeKey
        if (key != null && transactions.existsDedupeKey(key)) return@withTransaction false
        save(transaction)
        transaction.cancellations.forEach {
            transactions.insertCancellation(CancellationEntity(transactionId = transaction.id, amount = it.amount, canceledAt = it.canceledAt))
        }
        true
    }

    override suspend fun addCancellation(merchant: String, amount: Long, canceledAt: Long): Boolean = db.withTransaction {
        val payments = transactions.getByMerchant(merchant).map { it.toModel() }
        val target = CancellationRules.findTarget(payments, merchant, amount) ?: return@withTransaction false
        transactions.insertCancellation(CancellationEntity(transactionId = target.id, amount = amount, canceledAt = canceledAt))
        true
    }

    override suspend fun updateMatch(
        id: String,
        status: PlaceStatus,
        place: Place?,
        candidates: List<Place>,
        unmatchedReason: UnmatchedReason?,
    ) = modify(id) { it.copy(status = status, place = place, candidates = candidates, unmatchedReason = unmatchedReason) }

    override suspend fun pendingSearch(): List<Transaction> =
        transactions.getByStatus(listOf(PlaceStatus.PENDING_SEARCH.name)).map { it.toModel() }

    override suspend fun learnedPlace(merchant: String): Place? = places.learnedPlace(merchant)?.toModel()

    override suspend fun designation(merchant: String): UserDesignation? =
        places.designation(merchant)?.let { UserDesignation.valueOf(it) }

    override suspend fun choosePlace(id: String, place: Place) = db.withTransaction {
        val tx = get(id) ?: return@withTransaction
        save(PlaceTransitions.choosePlace(tx, place))
        places.upsertLearned(LearnedPlaceEntity(tx.merchantRaw, place.id))
    }

    override suspend fun markNotHere(id: String) = modify(id, PlaceTransitions::markNotHere)

    override suspend fun markOnline(id: String) = db.withTransaction {
        val tx = get(id) ?: return@withTransaction
        save(PlaceTransitions.markOnline(tx))
        places.upsertDesignation(DesignationEntity(tx.merchantRaw, UserDesignation.ONLINE.name))
    }

    override suspend fun markOffline(id: String) = db.withTransaction {
        val tx = get(id) ?: return@withTransaction
        save(PlaceTransitions.markOffline(tx))
        places.upsertDesignation(DesignationEntity(tx.merchantRaw, UserDesignation.OFFLINE.name))
    }

    override suspend fun delete(id: String) = transactions.delete(id)

    override suspend fun markForgotten(id: String) = modify(id, PlaceTransitions::markForgotten)

    /** clearAllTables는 블로킹 호출이고 트랜잭션 안에서 부를 수 없어 IO 스레드에서 따로 부른다. */
    override suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private suspend fun modify(id: String, change: (Transaction) -> Transaction) = db.withTransaction {
        val tx = get(id) ?: return@withTransaction
        save(change(tx))
    }

    /** 결제 행과 매장·후보를 저장한다. 취소 줄은 건드리지 않는다. */
    private suspend fun save(tx: Transaction) {
        places.upsert(listOfNotNull(tx.place).map { it.toEntity() } + tx.candidates.map { it.toEntity() })
        transactions.upsert(tx.toEntity())
        transactions.clearCandidates(tx.id)
        transactions.insertCandidates(tx.candidates.map { CandidateEntity(tx.id, it.id) })
    }
}
