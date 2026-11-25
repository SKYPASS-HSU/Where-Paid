package com.wherepaid.app.core.place

/**
 * 가맹점명 다루기 (분석서 FR-02, 5장 데이터). 순수 함수만 둔다.
 *
 * 토스 알림의 가맹점명은 법인명("(주)케이에프씨코리")이거나 길이 제한으로 잘려("씨유 영등포아크로타") 카카오 장소명과 다르다.
 * 그래서 별칭사전으로 바꾼 뒤, 공백·괄호를 뺀 이름이 장소명에 들어 있으면 같은 매장으로 본다.
 *
 * [ALIASES]와 [PAYMENT_GATEWAYS]는 인계 문서의 예시로 시작했다. 팀원들의 토스 거래내역에서 모아 계속 채운다
 * (실제 거래내역 자체는 커밋하지 않고 이름만 옮긴다).
 */
object MerchantNames {
    /**
     * 별칭사전: 알림의 가맹점명 앞부분 → 카카오에서 쓰는 이름. 앞부분이 같으면 바꾸고 나머지(지점명)는 그대로 둔다.
     * 예: "씨유 영등포아크로타" → "CU영등포아크로타", "(주)케이에프씨코리" → "KFC".
     */
    val ALIASES: Map<String, String> = mapOf(
        "(주)케이에프씨코리아" to "KFC",
        "(주)케이에프씨코리" to "KFC",
        "씨유" to "CU",
        "지에스25" to "GS25",
    )

    /**
     * 결제대행사(PG) 가맹점명. 매장이 아니라 온라인 결제라서 장소를 검색하지 않는다 (판정 순서표 2단계).
     * 공백·괄호를 뺀 가맹점명에 이 중 하나가 들어 있으면 PG로 본다.
     */
    val PAYMENT_GATEWAYS: List<String> = listOf(
        "KCP",
        "토스페이먼츠",
        "구글페이먼트코리아",
        "네이버파이낸셜",
        "네이버페이",
        "KG이니시스",
        "나이스페이먼츠",
        "자동결제",
    )

    private val CORPORATE_MARKS = listOf("(주)", "㈜", "(유)", "주식회사")
    private val REMOVED = Regex("""[\s()\[\]{}<>·.,\-_/]""")

    /** 비교용 이름: 법인 표시·공백·괄호·구두점을 빼고 영문은 대문자로. */
    fun normalize(name: String): String {
        var s = name
        CORPORATE_MARKS.forEach { s = s.replace(it, "") }
        return s.replace(REMOVED, "").uppercase()
    }

    /** 별칭사전을 적용한 이름 (검색어). 해당 없으면 원래 이름을 다듬어 돌려준다. */
    fun aliasOf(merchant: String): String {
        val trimmed = merchant.trim()
        val normalized = normalize(trimmed)
        val hit = ALIASES.entries
            .sortedByDescending { normalize(it.key).length }
            .firstOrNull { (from, _) -> normalize(from).let { it.isNotEmpty() && normalized.startsWith(it) } }
            ?: return trimmed
        val rest = normalized.removePrefix(normalize(hit.key))
        return hit.value + rest
    }

    /** 결제대행사 가맹점명인지 (온라인 결제). */
    fun isPaymentGateway(merchant: String): Boolean {
        val normalized = normalize(merchant)
        return PAYMENT_GATEWAYS.any { normalized.contains(normalize(it)) }
    }

    /**
     * 이름 일치 규칙: 별칭 변환한 가맹점명(공백·괄호 제외)이 장소명에 들어 있으면 같은 매장.
     * 알림의 가맹점명이 잘려도 앞부분이라 이 규칙으로 맞는다. 예: "CU영등포아크로타" ⊂ "CU영등포아크로타워점".
     */
    fun matches(merchant: String, placeName: String): Boolean {
        val key = normalize(aliasOf(merchant))
        return key.isNotEmpty() && normalize(placeName).contains(key)
    }

    /**
     * 카카오에 보낼 검색어 후보 (앞에서부터 시도). 전체 이름으로 못 찾으면 첫 단어(브랜드)로 다시 찾는다.
     * 잘린 지점명("영등포아크로타")은 카카오 키워드 검색에 안 걸리기 때문이다. 결과는 어느 쪽이든 [matches]로 거른다.
     */
    fun searchQueries(merchant: String): List<String> {
        val alias = aliasOf(merchant)
        val brand = merchant.trim().split(Regex("""\s+""")).firstOrNull()?.let(::aliasOf)
        return listOfNotNull(alias, brand).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
