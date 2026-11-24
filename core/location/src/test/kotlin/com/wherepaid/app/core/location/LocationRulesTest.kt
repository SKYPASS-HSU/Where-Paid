package com.wherepaid.app.core.location

import com.wherepaid.app.core.model.LatLng
import com.wherepaid.app.core.model.LocationFailure
import com.wherepaid.app.core.model.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationRulesTest {
    @Test
    fun `권한이 없으면 측정하지 않고 권한 없음`() {
        assertEquals(LocationFailure.NO_PERMISSION, LocationRules.precheckFailure(hasLocationPermission = false, locationEnabled = true))
        // 위치도 꺼져 있으면 권한 문제를 먼저 알린다
        assertEquals(LocationFailure.NO_PERMISSION, LocationRules.precheckFailure(hasLocationPermission = false, locationEnabled = false))
    }

    @Test
    fun `위치 서비스가 꺼져 있으면 측정하지 않고 위치 꺼짐`() {
        assertEquals(LocationFailure.LOCATION_DISABLED, LocationRules.precheckFailure(hasLocationPermission = true, locationEnabled = false))
    }

    @Test
    fun `권한과 위치가 모두 있으면 측정한다`() {
        assertNull(LocationRules.precheckFailure(hasLocationPermission = true, locationEnabled = true))
    }

    @Test
    fun `좌표를 받으면 정확도와 함께 성공`() {
        val result = LocationRules.resultOf(LocationFix(37.5446, 127.0557, 12.5f), hasBackgroundPermission = true)
        assertEquals(LocationResult.Success(LatLng(37.5446, 127.0557), 12.5f), result)
    }

    @Test
    fun `정확도가 없는 좌표도 성공으로 넘긴다`() {
        val result = LocationRules.resultOf(LocationFix(37.5, 127.0, null), hasBackgroundPermission = false)
        assertEquals(LocationResult.Success(LatLng(37.5, 127.0), null), result)
    }

    @Test
    fun `좌표가 없으면 시간 초과`() {
        assertEquals(
            LocationResult.Failure(LocationFailure.TIMEOUT),
            LocationRules.resultOf(null, hasBackgroundPermission = true),
        )
    }

    @Test
    fun `백그라운드 권한 없이 좌표가 없으면 권한 없음`() {
        assertEquals(
            LocationResult.Failure(LocationFailure.NO_PERMISSION),
            LocationRules.resultOf(null, hasBackgroundPermission = false),
        )
    }
}
