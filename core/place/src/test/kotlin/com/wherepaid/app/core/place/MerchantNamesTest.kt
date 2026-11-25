package com.wherepaid.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNamesTest {
    @Test
    fun `비교용 이름은 법인 표시와 공백 괄호를 빼고 대문자로`() {
        assertEquals("케이에프씨코리", MerchantNames.normalize("(주)케이에프씨코리"))
        assertEquals("스타벅스성수역점", MerchantNames.normalize("스타벅스 (성수역점)"))
        assertEquals("CU영등포아크로타워점", MerchantNames.normalize("cu 영등포아크로타워점"))
    }

    @Test
    fun `별칭사전은 앞부분을 바꾸고 지점명은 남긴다`() {
        assertEquals("KFC", MerchantNames.aliasOf("(주)케이에프씨코리"))
        assertEquals("CU영등포아크로타", MerchantNames.aliasOf("씨유 영등포아크로타"))
    }

    @Test
    fun `별칭이 없으면 이름을 그대로 쓴다`() {
        assertEquals("스타벅스 성수역점", MerchantNames.aliasOf("  스타벅스 성수역점 "))
    }

    @Test
    fun `잘린 가맹점명도 장소명 앞부분과 맞으면 같은 매장`() {
        assertTrue(MerchantNames.matches("씨유 영등포아크로타", "CU영등포아크로타워점"))
        assertTrue(MerchantNames.matches("(주)케이에프씨코리", "KFC 길음역점"))
        assertTrue(MerchantNames.matches("GS25", "GS25 성수역점"))
    }

    @Test
    fun `이름이 다르면 다른 매장`() {
        assertFalse(MerchantNames.matches("씨유 영등포아크로타", "GS25 영등포아크로타워점"))
        assertFalse(MerchantNames.matches("스타벅스 성수역점", "스타벅스 뚝섬역점"))
        assertFalse(MerchantNames.matches("   ", "CU 성수점"))
    }

    @Test
    fun `결제대행사 가맹점명은 PG로 본다`() {
        assertTrue(MerchantNames.isPaymentGateway("KCP"))
        assertTrue(MerchantNames.isPaymentGateway("NHN KCP"))
        assertTrue(MerchantNames.isPaymentGateway("구글페이먼트코리아"))
        assertTrue(MerchantNames.isPaymentGateway("넷플릭스(자동결제)"))
        assertFalse(MerchantNames.isPaymentGateway("스타벅스 성수역점"))
    }

    @Test
    fun `검색어는 전체 이름 다음 브랜드`() {
        assertEquals(listOf("CU영등포아크로타", "CU"), MerchantNames.searchQueries("씨유 영등포아크로타"))
        assertEquals(listOf("스타벅스 성수역점", "스타벅스"), MerchantNames.searchQueries("스타벅스 성수역점"))
        assertEquals(listOf("KFC"), MerchantNames.searchQueries("(주)케이에프씨코리"))
        assertEquals(listOf("GS25"), MerchantNames.searchQueries("GS25"))
    }
}
