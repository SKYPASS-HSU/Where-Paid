package com.wherepaid.app.core.place

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceResolver
import com.wherepaid.app.core.model.distanceMeters

/**
 * ② [PlaceResolver] 구현. 카카오 로컬 API 키워드 검색.
 *
 * 판정 순서표(분석서 FR-02)에서 이 클래스가 맡는 것은 5단계(별칭사전 + 반경 300 m 검색)뿐이다.
 * 사용자 지정·학습기록은 저장소에, PG사 판별은 [MerchantNames.isPaymentGateway]에, 위치 신뢰는 판정기에 있다.
 * 결제대행사 가맹점명은 [resolve]를 부르기 전에 걸러야 한다 (부르면 그냥 검색 결과 0곳이 된다).
 */
class KakaoPlaceResolver internal constructor(
    private val api: KeywordSearch,
) : PlaceResolver {

    constructor(restApiKey: String) : this(KakaoLocalApi(restApiKey))

    /**
     * [near] 반경 300 m에서 이름이 맞는 매장 (가까운 순). 0곳이면 빈 목록, 2곳 이상이면 느낌표 핀 후보.
     * 위치가 없으면([near] = null) 근처를 정할 수 없어 검색하지 않고 빈 목록 (호출한 쪽은 이미 "위치를 못 받았어요"로 둔다).
     * 인터넷 없음·요청 실패는 [java.io.IOException].
     */
    override suspend fun resolve(merchant: String, near: LatLng?): List<Place> {
        if (near == null || merchant.isBlank()) return emptyList()
        for (query in MerchantNames.searchQueries(merchant)) {
            val matched = api.search(query, near, RADIUS_M)
                .filter { MerchantNames.matches(merchant, it.name) }
                .distinctBy { it.id }
            if (matched.isNotEmpty()) return matched.sortedBy { distanceMeters(near, it.latLng) }
        }
        return emptyList()
    }

    /**
     * 매장 찾기 화면(S-06)의 검색. 별칭사전을 적용하고, [near]가 있으면 가까운 순. 반경 제한은 없다.
     * 결제대행사 이름(예: "KCP")은 매장이 아니라 검색하지 않고 빈 목록이다. 카카오에 그대로 물으면
     * "케이씨피글로벌"처럼 이름만 비슷한 먼 곳이 나오기 때문이다. 화면은 0곳 상태와 [온라인 결제예요]를 보여 준다.
     */
    override suspend fun search(query: String, near: LatLng?): List<Place> {
        if (query.isBlank() || MerchantNames.isPaymentGateway(query)) return emptyList()
        val places = api.search(MerchantNames.aliasOf(query), near, radiusM = null).distinctBy { it.id }
        return if (near == null) places else places.sortedBy { distanceMeters(near, it.latLng) }
    }

    companion object {
        /** 자동 매칭 검색 반경 (분석서 FR-02). */
        const val RADIUS_M = 300
    }
}
