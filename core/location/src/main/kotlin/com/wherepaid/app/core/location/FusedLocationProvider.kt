package com.wherepaid.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.wherepaid.app.core.model.LocationFailure
import com.wherepaid.app.core.model.LocationProvider
import com.wherepaid.app.core.model.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 결제 알림 1건마다 위치를 새로 1회 측정한다 (상시 추적 없음).
 *
 * - 고정확도, `maxUpdateAge = 0`: 캐시가 아니라 지금 측정한 좌표만 받는다.
 * - 캐시 위치(`lastLocation`)는 대체값으로도 쓰지 않는다. 검증에서 최대 2.2 km 어긋났다 (인계 문서 §2).
 * - [timeoutMs](기본 10초) 안에 못 얻으면 [LocationFailure.TIMEOUT].
 * - 실패는 예외가 아니라 [LocationResult.Failure]로 돌려준다. 오차가 큰지(100 m 초과)는 여기서 판단하지 않고
 *   정확도를 그대로 넘긴다 (판정 순서표의 위치 신뢰 단계).
 */
class FusedLocationProvider(
    context: Context,
    private val timeoutMs: Long = LocationConfig.TIMEOUT_MS,
) : LocationProvider {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    @SuppressLint("MissingPermission") // precheckFailure에서 권한을 확인한다
    override suspend fun capture(): LocationResult {
        LocationRules.precheckFailure(
            hasLocationPermission = LocationPermissions.hasForeground(appContext),
            locationEnabled = LocationPermissions.isLocationEnabled(appContext),
        )?.let { return LocationResult.Failure(it) }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(timeoutMs)
            .setMaxUpdateAgeMillis(0)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(timeoutMs + LocationConfig.GRACE_MS) {
                client.getCurrentLocation(request, cancellation.token).await(cancellation)
            }
            LocationRules.resultOf(
                fix = location?.let { LocationFix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) },
                hasBackgroundPermission = LocationPermissions.hasBackground(appContext),
            )
        } catch (e: SecurityException) {
            // 확인 직후 사용자가 권한을 끈 경우
            LocationResult.Failure(LocationFailure.NO_PERMISSION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Google Play 서비스 오류(ApiException) 등
            LocationResult.Failure(LocationFailure.UNKNOWN)
        } finally {
            cancellation.cancel()
        }
    }
}
