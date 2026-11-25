package com.wherepaid.app.core.place

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 키워드 장소 검색 한 번. [KakaoPlaceResolver]가 쓰고, 테스트에서는 가짜로 바꾼다. */
internal interface KeywordSearch {
    /**
     * [query]로 장소를 찾는다. [near]가 있으면 가까운 순, [radiusM]이 있으면 그 반경 안만.
     * 인터넷 없음·요청 실패는 [IOException].
     */
    suspend fun search(query: String, near: LatLng?, radiusM: Int?): List<Place>
}

/**
 * 카카오 로컬 API 키워드 검색 (https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword).
 * 보내는 것은 검색어와 좌표뿐이다 (NFR-03). 요청·응답을 로그로 남기지 않는다.
 */
internal class KakaoLocalApi(
    private val restApiKey: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : KeywordSearch {

    override suspend fun search(query: String, near: LatLng?, radiusM: Int?): List<Place> = withContext(io) {
        val connection = URL(keywordUrl(query, near, radiusM)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "KakaoAK $restApiKey")
            val code = connection.responseCode
            if (code != 200) {
                // 401·403(키 문제), 429(한도), 5xx. 호출한 쪽은 매칭 대기로 두고 나중에 다시 찾는다.
                throw IOException("카카오 로컬 API 응답 $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseKeywordResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000

        /** 한 번에 받는 최대 개수 (카카오 최대 15). */
        const val PAGE_SIZE = 15

        fun keywordUrl(query: String, near: LatLng?, radiusM: Int?): String = buildString {
            append(ENDPOINT)
            append("?query=").append(URLEncoder.encode(query, "UTF-8"))
            append("&size=").append(PAGE_SIZE)
            if (near != null) {
                // 카카오는 x = 경도, y = 위도
                append("&x=").append(near.longitude)
                append("&y=").append(near.latitude)
                append("&sort=distance")
                if (radiusM != null) append("&radius=").append(radiusM)
            }
        }

        /**
         * 응답 본문 → [Place] 목록. 주소는 지번 주소(`address_name`)를 쓴다. 리포트의 동 이름을 여기서 뽑기 때문이다
         * ([com.wherepaid.app.core.model.Neighborhoods.fromAddress]). 지번이 없으면 도로명.
         * 형식이 이상하면 [IOException] (호출한 쪽에서는 요청 실패와 같다).
         */
        fun parseKeywordResponse(body: String): List<Place> = try {
            val documents = JSONObject(body).getJSONArray("documents")
            (0 until documents.length()).mapNotNull { i ->
                val doc = documents.getJSONObject(i)
                val lng = doc.optString("x").toDoubleOrNull()
                val lat = doc.optString("y").toDoubleOrNull()
                val id = doc.optString("id")
                val name = doc.optString("place_name")
                if (lat == null || lng == null || id.isEmpty() || name.isEmpty()) return@mapNotNull null
                Place(
                    id = id,
                    name = name,
                    latLng = LatLng(lat, lng),
                    category = doc.optString("category_name").ifBlank { null },
                    address = doc.optString("address_name").ifBlank { doc.optString("road_address_name") }.ifBlank { null },
                )
            }
        } catch (e: JSONException) {
            throw IOException("카카오 로컬 API 응답 형식 오류", e)
        }
    }
}
