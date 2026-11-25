package com.wherepaid.app.core.place

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.Place
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KakaoPlaceResolverTest {
    private val here = LatLng(37.5446, 127.0557)

    private fun place(id: String, name: String, metersNorth: Double) =
        Place(id, name, LatLng(here.latitude + metersNorth / 111_000.0, here.longitude), null, null)

    /** 검색어별로 정해 둔 결과를 돌려주고, 받은 요청을 기록한다. */
    private class FakeSearch(private val results: Map<String, List<Place>>) : KeywordSearch {
        val calls = mutableListOf<Triple<String, LatLng?, Int?>>()
        override suspend fun search(query: String, near: LatLng?, radiusM: Int?): List<Place> {
            calls += Triple(query, near, radiusM)
            return results[query].orEmpty()
        }
    }

    @Test
    fun `반경 300m에서 이름이 맞는 매장만 가까운 순으로`() = runTest {
        val api = FakeSearch(
            mapOf(
                "GS25" to listOf(
                    place("3", "GS25 성수점", 180.0),
                    place("1", "GS25 성수역점", 40.0),
                    place("9", "GS편의점 아님", 10.0),
                    place("2", "GS25 뚝섬점", 70.0),
                ),
            ),
        )
        val result = KakaoPlaceResolver(api).resolve("GS25", here)

        assertEquals(listOf("1", "2", "3"), result.map { it.id })
        assertEquals(Triple("GS25", here, 300), api.calls.single())
    }

    @Test
    fun `잘린 지점명은 브랜드로 다시 찾아 이름으로 거른다`() = runTest {
        val api = FakeSearch(
            mapOf(
                "CU영등포아크로타" to emptyList(),
                "CU" to listOf(place("7", "CU 영등포역점", 120.0), place("8", "CU영등포아크로타워점", 60.0)),
            ),
        )
        val result = KakaoPlaceResolver(api).resolve("씨유 영등포아크로타", here)

        assertEquals(listOf("8"), result.map { it.id })
        assertEquals(listOf("CU영등포아크로타", "CU"), api.calls.map { it.first })
    }

    @Test
    fun `첫 검색어로 찾으면 브랜드로 다시 찾지 않는다`() = runTest {
        val api = FakeSearch(mapOf("스타벅스 성수역점" to listOf(place("5", "스타벅스 성수역점", 20.0))))
        KakaoPlaceResolver(api).resolve("스타벅스 성수역점", here)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `맞는 매장이 없으면 빈 목록`() = runTest {
        val api = FakeSearch(mapOf("스타벅스 성수역점" to listOf(place("6", "스타벅스 뚝섬역점", 50.0))))
        assertTrue(KakaoPlaceResolver(api).resolve("스타벅스 성수역점", here).isEmpty())
    }

    @Test
    fun `위치가 없으면 검색하지 않는다`() = runTest {
        val api = FakeSearch(emptyMap())
        assertTrue(KakaoPlaceResolver(api).resolve("GS25", null).isEmpty())
        assertTrue(api.calls.isEmpty())
    }

    @Test(expected = IOException::class)
    fun `요청이 실패하면 예외를 그대로 알린다`() = runTest {
        val failing = object : KeywordSearch {
            override suspend fun search(query: String, near: LatLng?, radiusM: Int?): List<Place> = throw IOException("오프라인")
        }
        KakaoPlaceResolver(failing).resolve("GS25", here)
    }

    @Test
    fun `매장 찾기 검색은 별칭을 적용하고 반경 없이 가까운 순`() = runTest {
        val api = FakeSearch(mapOf("KFC" to listOf(place("k2", "KFC 혜화점", 900.0), place("k1", "KFC 길음역점", 300.0))))
        val result = KakaoPlaceResolver(api).search("(주)케이에프씨코리", here)

        assertEquals(listOf("k1", "k2"), result.map { it.id })
        assertEquals(Triple("KFC", here, null), api.calls.single())
    }

    @Test
    fun `결제대행사 이름은 매장 찾기에서 검색하지 않는다`() = runTest {
        val api = FakeSearch(mapOf("KCP" to listOf(place("x", "케이씨피글로벌", 11_400.0))))
        assertTrue(KakaoPlaceResolver(api).search("KCP", here).isEmpty())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `빈 검색어는 요청하지 않는다`() = runTest {
        val api = FakeSearch(emptyMap())
        assertTrue(KakaoPlaceResolver(api).search("  ", here).isEmpty())
        assertTrue(api.calls.isEmpty())
    }
}
