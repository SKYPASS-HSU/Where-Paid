package com.wherepaid.app.core.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * 위치 권한 상태 확인과 설정 화면 이동. 권한을 묻는 화면(S-01 온보딩, 지도 홈 배너)은 ③이 만들고 이 함수들을 쓴다.
 *
 * 요청 순서 (인계 문서 §4): [FOREGROUND] → (Android 10+) [BACKGROUND].
 * Android 11 이상은 앱 안 권한 창에 "항상 허용"이 없어서 [appSettingsIntent]로 보내야 한다 ([backgroundNeedsSettings]).
 */
object LocationPermissions {
    /** 처음 요청할 권한. 둘을 함께 요청해야 사용자가 "정확한 위치"를 고를 수 있다. */
    val FOREGROUND: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** "항상 허용". [FOREGROUND]를 받은 뒤에만 요청할 수 있다. Android 9 이하에는 없다. */
    const val BACKGROUND: String = "android.permission.ACCESS_BACKGROUND_LOCATION"

    /** 위치 권한이 하나라도 있는지 (대략적 위치 포함). */
    fun hasForeground(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "정확한 위치"를 허용했는지. 대략적 위치만 있으면 오차가 커서 매장을 찾기 어렵다. */
    fun hasPrecise(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** 앱이 꺼져 있어도 위치를 잴 수 있는지. Android 9 이하는 위치 권한만 있으면 된다. */
    fun hasBackground(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, BACKGROUND)
        } else {
            hasForeground(context)
        }

    /** 지도 홈 배너 기준: 정확한 위치 + 항상 허용. 하나라도 빠지면 "위치를 항상 허용해 주세요". */
    fun isAlwaysAllowed(context: Context): Boolean = hasPrecise(context) && hasBackground(context)

    /** 기기의 위치 서비스(GPS 스위치)가 켜져 있는지. */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Android 11 이상이면 "항상 허용"을 앱 설정 화면에서 골라야 한다. */
    fun backgroundNeedsSettings(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 이 앱의 설정 화면 (권한 → 위치 → 항상 허용). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** 기기 위치 서비스 설정 화면 (위치가 꺼져 있을 때). */
    fun locationSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
