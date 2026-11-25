package com.wherepaid.app.core.location

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.LocationFailure
import com.wherepaid.app.core.model.LocationResult

/** FLP가 돌려준 좌표 1개. Android의 Location을 단위 테스트 없이 다루려고 따로 둔다. */
internal data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Float?)

/** 측정 전후 판단. Android API를 부르지 않는 순수 함수라 단위 테스트로 확인한다. */
internal object LocationRules {
    /** 측정을 시작하기 전에 실패로 끝낼 이유. 없으면 null (측정한다). */
    fun precheckFailure(hasLocationPermission: Boolean, locationEnabled: Boolean): LocationFailure? = when {
        !hasLocationPermission -> LocationFailure.NO_PERMISSION
        !locationEnabled -> LocationFailure.LOCATION_DISABLED
        else -> null
    }

    /**
     * 측정 결과를 [LocationResult]로 바꾼다.
     * FLP는 제한 시간 안에 새 좌표를 못 얻으면 null을 준다. 백그라운드 권한 없이 앱 밖에서 요청해도 null이라,
     * 그 경우는 시간 초과가 아니라 권한 문제로 본다 (검증 앱에서 확인한 원인).
     */
    fun resultOf(fix: LocationFix?, hasBackgroundPermission: Boolean): LocationResult = when {
        fix != null -> LocationResult.Success(LatLng(fix.latitude, fix.longitude), fix.accuracyM)
        !hasBackgroundPermission -> LocationResult.Failure(LocationFailure.NO_PERMISSION)
        else -> LocationResult.Failure(LocationFailure.TIMEOUT)
    }
}
