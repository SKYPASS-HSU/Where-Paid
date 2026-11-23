package com.wherepaid.app.core.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.wherepaid.app.core.model.Cancellation
import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.Region
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.UnmatchedReason
import com.wherepaid.app.core.model.distanceMeters

// 알림 원문(title, text 등)을 담는 컬럼은 만들지 않는다. 파싱 결과만 저장한다.

@Entity(
    tableName = "transactions",
    indices = [Index("paidAt"), Index("status"), Index("merchantRaw"), Index(value = ["dedupeKey"], unique = true)],
)
internal data class TransactionEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val merchantRaw: String,
    val cardName: String?,
    val payType: String?,
    val paidAt: Long,
    val dedupeKey: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val province: String?,
    val district: String?,
    val neighborhood: String?,
    /** [PlaceStatus.name] */
    val status: String,
    /** MAPPED일 때만 */
    val placeId: String?,
    /** [UnmatchedReason.name] */
    val unmatchedReason: String?,
)

@Entity(tableName = "places")
internal data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String?,
    val address: String?,
)

@Entity(
    tableName = "cancellations",
    foreignKeys = [ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("transactionId")],
)
internal data class CancellationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val amount: Long,
    val canceledAt: Long,
)

/** 느낌표 핀 후보 (결제 ↔ 장소). */
@Entity(
    tableName = "candidates",
    primaryKeys = ["transactionId", "placeId"],
    foreignKeys = [ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("placeId")],
)
internal data class CandidateEntity(val transactionId: String, val placeId: String)

/** 학습기록: 가맹점명 → 사용자가 고른 매장. 결제를 지워도 남는다. */
@Entity(tableName = "learned_places")
internal data class LearnedPlaceEntity(@PrimaryKey val merchant: String, val placeId: String)

/** 사용자 지정: 가맹점명 → ONLINE / OFFLINE. */
@Entity(tableName = "designations")
internal data class DesignationEntity(@PrimaryKey val merchant: String, val designation: String)

/** 결제 + 매장 + 취소 + 후보를 한 번에 읽는 결과. */
internal data class TransactionWithRelations(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "placeId", entityColumn = "id")
    val place: PlaceEntity?,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val cancellations: List<CancellationEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(CandidateEntity::class, parentColumn = "transactionId", entityColumn = "placeId"),
    )
    val candidates: List<PlaceEntity>,
)

internal fun TransactionWithRelations.toModel(): Transaction {
    val tx = transaction
    val location = if (tx.latitude != null && tx.longitude != null) LatLng(tx.latitude, tx.longitude) else null
    return Transaction(
        id = tx.id,
        amount = tx.amount,
        merchantRaw = tx.merchantRaw,
        cardName = tx.cardName,
        payType = tx.payType,
        paidAt = tx.paidAt,
        dedupeKey = tx.dedupeKey,
        location = location,
        locationAccuracyM = tx.locationAccuracyM,
        region = tx.province?.let { Region(it, tx.district, tx.neighborhood) },
        status = PlaceStatus.valueOf(tx.status),
        place = place?.toModel(),
        // 후보는 결제 위치에서 가까운 순 (분석서 UC-02)
        candidates = candidates.map { it.toModel() }
            .sortedBy { candidate -> location?.let { distanceMeters(it, candidate.latLng) } ?: 0.0 },
        unmatchedReason = tx.unmatchedReason?.let { UnmatchedReason.valueOf(it) },
        cancellations = cancellations.sortedBy { it.canceledAt }.map { Cancellation(it.amount, it.canceledAt) },
    )
}

internal fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amount = amount,
    merchantRaw = merchantRaw,
    cardName = cardName,
    payType = payType,
    paidAt = paidAt,
    dedupeKey = dedupeKey,
    latitude = location?.latitude,
    longitude = location?.longitude,
    locationAccuracyM = locationAccuracyM,
    province = region?.province,
    district = region?.district,
    neighborhood = region?.neighborhood,
    status = status.name,
    placeId = place?.id,
    unmatchedReason = unmatchedReason?.name,
)

internal fun PlaceEntity.toModel() = Place(
    id = id,
    name = name,
    latLng = LatLng(latitude, longitude),
    category = category,
    address = address,
)

internal fun Place.toEntity() = PlaceEntity(
    id = id,
    name = name,
    latitude = latLng.latitude,
    longitude = latLng.longitude,
    category = category,
    address = address,
)
