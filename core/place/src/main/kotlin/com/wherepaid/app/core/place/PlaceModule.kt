package com.wherepaid.app.core.place

import com.wherepaid.app.core.model.PlaceResolver

/** 카카오 REST 키가 주입됐는지 (local.properties의 kakao.restApiKey). */
val isKakaoRestApiKeyConfigured: Boolean
    get() = BuildConfig.KAKAO_REST_API_KEY.isNotEmpty()

/** REST 키가 있으면 카카오 로컬 API 구현, 없으면 null (AppContainer가 가짜를 쓴다). */
fun createPlaceResolver(): PlaceResolver? =
    BuildConfig.KAKAO_REST_API_KEY.takeIf { it.isNotEmpty() }?.let { KakaoPlaceResolver(it) }
