package com.wherepaid.app.core.model

import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * 모듈 간 약속. 화면(feature)은 이 인터페이스만 알고, 구현은 :app의 AppContainer가 골라 넣는다.
 * 시그니처를 바꾸면 모든 모듈이 영향을 받으므로 그 변경만 담은 PR로 팀 전체 확인을 받는다.
 */

/** ① 알림 수집·파싱 (:core:notification). 결제·취소로 판별된 알림만 내보낸다. */
interface PaymentSource {
    val payments: Flow<ParsedPayment>
}

/** ② 위치 (:core:location). 호출할 때마다 새로 1회 측정한다. 캐시 위치(lastKnown)는 쓰지 않는다. */
interface LocationProvider {
    suspend fun capture(): LocationResult
}

/** ② 장소 매칭·검색 (:core:place). */
interface PlaceResolver {
    /**
     * 자동 매칭 (분석서 FR-02 판정 순서표 5단계). 별칭사전 변환 후 [near] 반경 300 m에서 이름이 맞는 매장을 찾는다.
     * 결과 0곳이면 빈 목록, 네트워크 없음·요청 실패면 예외를 던진다 (호출한 쪽이 매칭 대기로 처리).
     */
    suspend fun resolve(merchant: String, near: LatLng?): List<Place>

    /** 매장 찾기 화면의 검색 (S-06). [near]에서 가까운 순. 네트워크 없음·요청 실패면 예외. */
    suspend fun search(query: String, near: LatLng?): List<Place>
}

/**
 * ④ 저장 (:core:data). 기기 DB(Room)가 원본이고 Android 자동 백업으로 보존한다. 로그인·앱 서버는 없다.
 * 사용자 동작 함수는 분석서 2-3 결제 상태 이동표를 그대로 따른다.
 */
interface TransactionRepository {
    /** 그 달(결제 시각 기준)의 결제. 최근 결제가 먼저. */
    fun observeMonth(month: YearMonth): Flow<List<Transaction>>

    /** 장소 확인 화면에 나오는 결제 ([Transaction.needsPlace]). 달 구분 없이 최근 결제가 먼저. */
    fun observeNeedsPlace(): Flow<List<Transaction>>

    fun observe(id: String): Flow<Transaction?>

    suspend fun get(id: String): Transaction?

    // --- 기록 (①②의 파이프라인이 호출) ---

    /** 새 결제를 저장한다. 같은 [Transaction.dedupeKey]가 이미 있으면 저장하지 않고 false. */
    suspend fun add(transaction: Transaction): Boolean

    /**
     * 취소 알림을 원래 결제에 붙인다 (분석서 FR-01). 붙일 결제가 없으면 기록하지 않고 false.
     * 규칙은 [CancellationRules.findTarget].
     */
    suspend fun addCancellation(merchant: String, amount: Long, canceledAt: Long): Boolean

    /** 자동 매칭 결과를 반영한다 (매칭 대기 재검색 포함). 사용자 동작이 아니므로 학습기록을 남기지 않는다. */
    suspend fun updateMatch(
        id: String,
        status: PlaceStatus,
        place: Place?,
        candidates: List<Place>,
        unmatchedReason: UnmatchedReason?,
    )

    /** 매칭 대기 결제 (재연결 시 다시 검색할 대상). */
    suspend fun pendingSearch(): List<Transaction>

    /** 학습기록: 사용자가 이 가맹점명으로 고른 매장. */
    suspend fun learnedPlace(merchant: String): Place?

    /** 사용자 지정: 이 가맹점명을 온라인/오프라인으로 정했는지. */
    suspend fun designation(merchant: String): UserDesignation?

    // --- 사용자 동작 (화면이 호출) ---

    /** 후보 선택, 매장 검색 등록, [장소 변경] → 지도 핀 + 학습기록 저장(또는 수정). */
    suspend fun choosePlace(id: String, place: Place)

    /** [여기 아니에요] → 매칭실패함. */
    suspend fun markNotHere(id: String)

    /** [온라인 결제예요] → 온라인 목록 + 온라인 지정. */
    suspend fun markOnline(id: String)

    /** 온라인 목록에서 [오프라인이에요] → 매칭실패함 + 오프라인 지정 (온라인 지정은 해제). */
    suspend fun markOffline(id: String)

    /** [삭제] → 결제 기록 삭제. 학습기록은 유지한다. */
    suspend fun delete(id: String)

    /** 후보 고르기·매장 찾기의 [기억 안 나요] → 장소 확인 목록에서 뺀다. 학습기록·사용자 지정은 남기지 않는다. */
    suspend fun markForgotten(id: String)

    /**
     * 데이터 초기화. 결제·취소·후보·매장·학습기록·사용자 지정을 모두 지운다. 되돌릴 수 없다.
     * 온보딩 완료 여부 같은 앱 설정은 지우지 않는다.
     */
    suspend fun clearAll()
}
