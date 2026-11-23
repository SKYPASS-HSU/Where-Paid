package com.wherepaid.app.core.model.fake

import com.wherepaid.app.core.model.Cancellation
import com.wherepaid.app.core.model.KOREA_ZONE
import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.Region
import com.wherepaid.app.core.model.Transaction
import com.wherepaid.app.core.model.UnmatchedReason
import java.time.YearMonth

/**
 * 화면 개발과 테스트용 가짜 데이터. 피그마 시안의 예시를 옮겼고, 좌표·주소·장소 ID는 지어낸 것이다.
 * 실제 결제 기록이나 팀원의 위치를 여기에 넣지 않는다.
 *
 * 다섯 가지 장소 상태(지도 핀, 느낌표 핀, 매칭실패함, 매칭 대기, 온라인)와 취소가 모두 들어 있다.
 */
object SampleData {
    /** 성수역 근처. 가짜 위치의 기준점. */
    val SEONGSU = LatLng(37.5446, 127.0559)

    private val SEOUL_SEONGDONG = Region("서울특별시", "성동구", "성수동")
    private val SEOUL_SEONGBUK = Region("서울특별시", "성북구", "삼선동")
    private val SEOUL_JONGNO = Region("서울특별시", "종로구", "종로")

    private fun place(id: String, name: String, lat: Double, lng: Double, category: String, address: String) =
        Place("fake-$id", name, LatLng(lat, lng), category, address)

    val starbucksSeongsu = place("starbucks", "스타벅스 성수역점", 37.5447, 127.0561, "음식점 > 카페", "서울 성동구 샘플로 1")
    val cuHansung = place("cu", "CU 한성대점", 37.5885, 127.0060, "편의점", "서울 성북구 샘플로 2")
    val oliveyoungSeongsu = place("oliveyoung", "올리브영 성수점", 37.5440, 127.0570, "가정,생활 > 화장품", "서울 성동구 샘플로 3")
    val kyoboGwanghwamun = place("kyobo", "교보문고 광화문점", 37.5711, 126.9779, "문화,예술 > 서점", "서울 종로구 샘플로 4")
    val kfcSungshin = place("kfc", "KFC 성신여대점", 37.5925, 127.0167, "음식점 > 패스트푸드", "서울 성북구 샘플로 5")

    /** 매장 찾기(S-06) 검색 결과용. 시안의 "KFC" 검색 예시. */
    val kfcOthers = listOf(
        place("kfc-gireum", "KFC 길음역점", 37.6031, 127.0250, "음식점 > 패스트푸드", "서울 성북구 샘플로 6"),
        place("kfc-hyehwa", "KFC 혜화점", 37.5822, 127.0019, "음식점 > 패스트푸드", "서울 종로구 샘플로 7"),
    )

    /** 느낌표 핀 "GS25 3,400원"의 후보 3곳. */
    val gs25Candidates = listOf(
        place("gs25-hansung", "GS25 한성대점", 37.5884, 127.0064, "편의점", "서울 성북구 샘플로 10"),
        place("gs25-samseon", "GS25 삼선교점", 37.5880, 127.0072, "편의점", "서울 성북구 샘플로 11"),
        place("gs25-seongbuk", "GS25 성북로점", 37.5893, 127.0050, "편의점", "서울 성북구 샘플로 12"),
    )

    val ediyaCandidates = listOf(
        place("ediya-1", "이디야커피 성수점", 37.5443, 127.0555, "음식점 > 카페", "서울 성동구 샘플로 20"),
        place("ediya-2", "이디야커피 뚝섬점", 37.5470, 127.0475, "음식점 > 카페", "서울 성동구 샘플로 21"),
    )

    val places: List<Place> =
        listOf(starbucksSeongsu, cuHansung, oliveyoungSeongsu, kyoboGwanghwamun, kfcSungshin) +
            kfcOthers + gs25Candidates + ediyaCandidates

    /**
     * 가짜 별칭사전 (법인명 → 브랜드명). 실제 별칭사전은 ②가 :core:place에 만든다 (분석서 5장).
     * 매장 찾기 화면의 검색어를 미리 채울 때와 가짜 검색에서 쓴다.
     */
    val aliases: Map<String, String> = mapOf("(주)케이에프씨코리" to "KFC", "씨유" to "CU")

    /**
     * [month]의 가짜 결제. 기본은 이번 달이라 앱을 켜면 지도 홈에 바로 보인다.
     * 날짜는 1~11일에 몰아 두어 어느 날에 실행해도 달 안에 들어간다.
     */
    fun transactions(month: YearMonth = YearMonth.now(KOREA_ZONE)): List<Transaction> {
        fun at(day: Int, hour: Int, minute: Int): Long =
            month.atDay(day).atTime(hour, minute).atZone(KOREA_ZONE).toInstant().toEpochMilli()

        var seq = 0
        fun tx(
            merchant: String,
            amount: Long,
            card: String,
            paidAt: Long,
            status: PlaceStatus,
            place: Place? = null,
            candidates: List<Place> = emptyList(),
            reason: UnmatchedReason? = null,
            region: Region? = place?.let { regionOf(it) },
            cancellations: List<Cancellation> = emptyList(),
            hasLocation: Boolean = status != PlaceStatus.ONLINE && reason != UnmatchedReason.NO_LOCATION,
        ): Transaction {
            seq++
            val location = when {
                !hasLocation -> null
                place != null -> place.latLng
                // 느낌표 핀: 첫 후보에서 남쪽으로 약 40m (시안의 "40m · 120m · 210m"처럼 거리가 보이게)
                candidates.isNotEmpty() -> candidates.first().latLng.let { LatLng(it.latitude - 0.00035, it.longitude) }
                else -> SEONGSU
            }
            return Transaction(
                id = "fake-tx-$seq", amount = amount, merchantRaw = merchant, cardName = card, payType = "일시불",
                paidAt = paidAt, dedupeKey = "fake-$seq", location = location,
                locationAccuracyM = location?.let { 12.5f }, region = region, status = status, place = place,
                candidates = candidates, unmatchedReason = reason, cancellations = cancellations,
            )
        }

        val mapped = PlaceStatus.MAPPED
        return listOf(
            // 지도 핀: 스타벅스 성수역점 (취소 1건 포함)
            tx("스타벅스 성수역점", 6_800, "KB국민체크", at(11, 14, 32), mapped, starbucksSeongsu),
            tx("스타벅스 성수역점", 5_300, "하나체크", at(9, 9, 5), mapped, starbucksSeongsu),
            tx(
                "스타벅스 성수역점", 4_500, "KB국민체크", at(8, 18, 31), mapped, starbucksSeongsu,
                cancellations = listOf(Cancellation(4_500, at(8, 18, 40))),
            ),
            tx("스타벅스 성수역점", 7_200, "KB국민체크", at(6, 13, 12), mapped, starbucksSeongsu),
            tx("스타벅스 성수역점", 12_000, "하나체크", at(2, 10, 30), mapped, starbucksSeongsu),
            tx("씨유 한성대", 3_400, "KB국민체크", at(10, 8, 20), mapped, cuHansung),
            tx("씨유 한성대", 2_300, "하나체크", at(4, 21, 3), mapped, cuHansung),
            tx("올리브영 성수점", 24_000, "하나체크", at(7, 17, 45), mapped, oliveyoungSeongsu),
            tx("교보문고 광화문점", 21_000, "KB국민체크", at(5, 15, 0), mapped, kyoboGwanghwamun),
            // 느낌표 핀
            tx("GS25", 3_400, "KB국민체크", at(11, 8, 41), PlaceStatus.AMBIGUOUS, candidates = gs25Candidates, region = SEOUL_SEONGBUK),
            tx("이디야커피", 4_200, "하나체크", at(10, 15, 22), PlaceStatus.AMBIGUOUS, candidates = ediyaCandidates, region = SEOUL_SEONGDONG),
            // 매칭실패함
            tx("(주)케이에프씨코리", 9_800, "KB국민체크", at(9, 19, 20), PlaceStatus.UNMATCHED, reason = UnmatchedReason.NO_LOCATION),
            tx("홍길동상회", 12_000, "하나체크", at(7, 12, 5), PlaceStatus.UNMATCHED, reason = UnmatchedReason.NO_RESULT, region = SEOUL_JONGNO),
            tx("KCP", 15_000, "KB국민체크", at(4, 20, 11), PlaceStatus.UNMATCHED, reason = UnmatchedReason.MARKED_OFFLINE, region = SEOUL_SEONGDONG),
            // 매칭 대기
            tx("씨유 영등포아크로타", 3_400, "KB국민체크", at(11, 7, 50), PlaceStatus.PENDING_SEARCH, region = Region("서울특별시", "영등포구")),
            // 온라인
            tx("쿠팡", 32_400, "하나체크", at(10, 22, 14), PlaceStatus.ONLINE),
            tx("토스페이먼츠", 15_000, "KB국민체크", at(7, 11, 30), PlaceStatus.ONLINE),
            tx("배달의민족", 15_100, "KB국민체크", at(5, 19, 47), PlaceStatus.ONLINE),
            tx("Adobe", 26_400, "하나체크", at(3, 9, 0), PlaceStatus.ONLINE),
        )
    }

    private fun regionOf(place: Place): Region = when (place) {
        starbucksSeongsu, oliveyoungSeongsu -> SEOUL_SEONGDONG
        kyoboGwanghwamun -> SEOUL_JONGNO
        else -> SEOUL_SEONGBUK
    }
}
