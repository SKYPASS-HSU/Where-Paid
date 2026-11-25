package com.wherepaid.app.core.place

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KakaoLocalApiTest {
    @Test
    fun `반경 검색 주소에는 좌표 거리순 반경이 들어간다`() {
        val url = KakaoLocalApi.keywordUrl("CU", LatLng(37.5446, 127.0557), 300)
        assertTrue(url.startsWith("https://dapi.kakao.com/v2/local/search/keyword.json?query=CU"))
        assertTrue(url.contains("&x=127.0557&y=37.5446"))
        assertTrue(url.contains("&sort=distance&radius=300"))
    }

    @Test
    fun `검색어는 URL 인코딩하고 위치가 없으면 좌표를 보내지 않는다`() {
        val url = KakaoLocalApi.keywordUrl("스타벅스 성수", null, null)
        assertTrue(url.contains("query=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4+%EC%84%B1%EC%88%98"))
        assertFalse(url.contains("&x="))
        assertFalse(url.contains("radius"))
    }

    @Test
    fun `응답을 장소로 바꾸고 주소는 지번을 쓴다`() {
        val body = """
            {"documents":[
              {"id":"101","place_name":"CU 성수역점","category_name":"가정,생활 > 편의점 > CU",
               "x":"127.0557","y":"37.5446","address_name":"서울 성동구 성수동2가 280","road_address_name":"서울 성동구 아차산로 100","distance":"35"},
              {"id":"102","place_name":"CU 뚝섬점","category_name":"",
               "x":"127.0470","y":"37.5470","address_name":"","road_address_name":"서울 성동구 왕십리로 1"}
            ],"meta":{"total_count":2}}
        """.trimIndent()

        val places = KakaoLocalApi.parseKeywordResponse(body)

        assertEquals(
            listOf(
                Place("101", "CU 성수역점", LatLng(37.5446, 127.0557), "가정,생활 > 편의점 > CU", "서울 성동구 성수동2가 280"),
                Place("102", "CU 뚝섬점", LatLng(37.5470, 127.0470), null, "서울 성동구 왕십리로 1"),
            ),
            places,
        )
    }

    @Test
    fun `좌표가 없는 항목은 버린다`() {
        val body = """{"documents":[{"id":"1","place_name":"이상한 곳","x":"","y":"37.5"}]}"""
        assertEquals(emptyList<Place>(), KakaoLocalApi.parseKeywordResponse(body))
    }

    @Test(expected = IOException::class)
    fun `형식이 이상하면 요청 실패와 같다`() {
        KakaoLocalApi.parseKeywordResponse("<html>error</html>")
    }
}
