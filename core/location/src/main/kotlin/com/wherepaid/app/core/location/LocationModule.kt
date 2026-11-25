package com.wherepaid.app.core.location

/**
 * ② 위치 모듈 설정값. 근거는 검증 앱 인계 문서 §2와 분석서 FR-02.
 *
 * - [FusedLocationProvider]: [com.wherepaid.app.core.model.LocationProvider] 구현 (결제 알림마다 1회 측정)
 * - [LocationPermissions]: 권한 상태 확인과 설정 화면 이동
 * - [LocationAccessMonitor]: 지도 홈의 "위치를 항상 허용해 주세요" 배너 값
 */
object LocationConfig {
    /** 위치 측정 제한 시간. 분석서 FR-02 기준 10초. 넘기면 [com.wherepaid.app.core.model.LocationFailure.TIMEOUT]. */
    const val TIMEOUT_MS: Long = 10_000L

    /** FLP가 제한 시간에 맞춰 null을 돌려주지 못할 때를 대비한 코루틴 쪽 여유 시간. */
    internal const val GRACE_MS: Long = 500L
}
