package com.wherepaid.app.core.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "위치를 항상 허용"인지 알려 준다 (지도 홈 배너, 분석서 NFR-04).
 * Android는 권한이 바뀌어도 알려 주지 않으므로, 설정 화면에서 돌아올 때(Activity onResume) [refresh]를 부른다.
 */
class LocationAccessMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val state = MutableStateFlow(LocationPermissions.isAlwaysAllowed(appContext))

    val alwaysAllowed: StateFlow<Boolean> = state.asStateFlow()

    fun refresh() {
        state.value = LocationPermissions.isAlwaysAllowed(appContext)
    }
}
