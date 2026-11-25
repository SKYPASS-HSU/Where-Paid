package com.wherepaid.app.core.location

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.wherepaid.app.core.model.LocationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 FLP로 1회 측정한다. 기기/에뮬레이터의 위치 서비스가 켜져 있어야 한다.
 * 에뮬레이터는 Extended controls(⋯) → Location에서 좌표를 정하고 [Set location]을 누른 뒤 돌린다.
 *
 * ./gradlew :core:location:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FusedLocationProviderTest {
    // 테스트에는 보이는 화면이 없어 시스템이 백그라운드 요청으로 본다. 그래서 "항상 허용"까지 준다.
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(LocationPermissions.BACKGROUND)
        }.toTypedArray(),
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun capturesFreshLocationWithinTimeout() = runBlocking {
        val started = System.currentTimeMillis()
        val result = FusedLocationProvider(context).capture()
        val elapsed = System.currentTimeMillis() - started

        assertTrue("위치를 못 받음: $result", result is LocationResult.Success)
        assertTrue("제한 시간을 넘김: ${elapsed}ms", elapsed <= LocationConfig.TIMEOUT_MS + LocationConfig.GRACE_MS + 1_000)
        val success = result as LocationResult.Success
        assertTrue(success.latLng.latitude in -90.0..90.0 && success.latLng.longitude in -180.0..180.0)
    }
}
