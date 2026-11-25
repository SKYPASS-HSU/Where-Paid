package com.wherepaid.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 피그마 시안("어디서돈썼지도 - 화면 시안")에서 뽑은 색. 화면 코드에서 Color(0x...)를 직접 쓰지 말고 이 값을 쓴다.
 * 이름은 색 자체가 아니라 쓰임새로 붙인다.
 */
@Immutable
data class WherePaidColors(
    /** 주 색: 기본 버튼, 지도 핀, 선택된 탭, 강조 금액. */
    val primary: Color = Color(0xFFFF5A36),
    val onPrimary: Color = Color(0xFFFFFFFF),
    /** 주 색의 연한 바탕 (지도 홈 빈 상태 기호). */
    val primaryContainer: Color = Color(0xFFFBEDE9),
    /** 어두운 바탕: 지도 위 배너(자동 기록 꺼짐, 위치 권한), 장소 확인 칩. 글자는 흰색. */
    val inverseSurface: Color = Color(0xFF191F28),

    val textPrimary: Color = Color(0xFF191F28),
    /** 보조 문구, 비활성 탭, 금액 옆 설명. */
    val textSecondary: Color = Color(0xFF6B7684),
    /** 다이얼로그·빈 상태 본문처럼 읽어야 하는 보조 문구. */
    val textTertiary: Color = Color(0xFF4E5968),
    /** 가장 옅은 안내 글자 (지도 카드의 "온라인 결제 88,900원은"). */
    val textHint: Color = Color(0xFF8B95A1),

    val background: Color = Color(0xFFFFFFFF),
    /** 회색 면: 보조 버튼, 칩, 구역 구분 띠, 권한 카드. */
    val surfaceMuted: Color = Color(0xFFF2F4F6),
    /** 선, 시트 손잡이, 테두리. */
    val border: Color = Color(0xFFE5E8EB),
    /** 지도 자리 배경. */
    val mapBackground: Color = Color(0xFFEEF0F3),

    /** 확인이 필요한 것: 느낌표 핀, 거절된 권한. */
    val warning: Color = Color(0xFFFFA41B),
    val warningContainer: Color = Color(0xFFFFF4E0),
    val onWarningContainer: Color = Color(0xFFB26A00),

    /** 삭제, 결제 취소 금액, 오류. */
    val danger: Color = Color(0xFFC9302C),
    /** 오류 기호 바탕 (S-06 오프라인). */
    val dangerContainer: Color = Color(0xFFFCEBEA),

    /** 온라인 결제. */
    val online: Color = Color(0xFF6B5CE7),
    val onlineContainer: Color = Color(0xFFF0EEFF),

    /** 허용됨, 장소 확인 완료. */
    val success: Color = Color(0xFF13804A),
    val successContainer: Color = Color(0xFFE7F4EC),

    /** 오프라인 안내 같은 어두운 토스트. */
    val toast: Color = Color(0xFF4E5968),
    /** 다이얼로그 뒤 어둡게. */
    val scrim: Color = Color(0x73000000),

    /**
     * 지역 색칠 6단계 (피그마 "개발 참고" §3). 0 = 없음, 5 = 가장 많이.
     * 단계 = ceil(금액 / 같은 줌 단계 안 최댓값 × 5)
     */
    val regionScale: List<Color> = listOf(
        Color(0xFFE5E8EB),
        Color(0xFFFFD8C9),
        Color(0xFFFFAE91),
        Color(0xFFFF7F57),
        Color(0xFFF0542E),
        Color(0xFFC2361A),
    ),
)
