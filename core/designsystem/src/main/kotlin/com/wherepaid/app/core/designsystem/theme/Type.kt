package com.wherepaid.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wherepaid.app.core.designsystem.R

/** 시안의 글꼴. 라이선스: core/designsystem/PRETENDARD_LICENSE.txt (SIL OFL 1.1) */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float = 1.35f) = TextStyle(
    fontFamily = Pretendard,
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * 시안에 나오는 글자 스타일. 이름은 크기_굵기. 색은 넣지 않는다 (쓰는 곳에서 [WherePaidColors]로).
 * 줄 간격은 시안 기본 화면 기준 1.35, 여러 줄 본문(다이얼로그·빈 상태)은 1.5.
 */
@Immutable
data class WherePaidTypography(
    /** 온보딩 제목 */
    val headline26: TextStyle = style(26.sp, FontWeight.Bold),
    /** 큰 금액 (지도 상단 카드, 온라인 결제 총액) */
    val amount28: TextStyle = style(28.sp, FontWeight.Bold),
    /** 화면 안 큰 제목, 시트 제목, 시트의 합계 금액 */
    val title20: TextStyle = style(20.sp, FontWeight.Bold),
    /** 탭 화면 머리글 ("장소 확인"), 다이얼로그·빈 상태 제목 */
    val title18: TextStyle = style(18.sp, FontWeight.Bold, lineHeight = 1.5f),
    /** 뒤로 가기가 있는 머리글 ("온라인 결제") */
    val header17: TextStyle = style(17.sp, FontWeight.SemiBold),
    val button16: TextStyle = style(16.sp, FontWeight.SemiBold),
    val body15Strong: TextStyle = style(15.sp, FontWeight.SemiBold),
    val body15Medium: TextStyle = style(15.sp, FontWeight.Medium),
    val body15: TextStyle = style(15.sp, FontWeight.Normal),
    /** 다이얼로그·빈 상태처럼 여러 줄로 읽는 본문 */
    val paragraph15: TextStyle = style(15.sp, FontWeight.Normal, lineHeight = 1.5f),
    val label14: TextStyle = style(14.sp, FontWeight.SemiBold),
    val body14Medium: TextStyle = style(14.sp, FontWeight.Medium),
    val label13: TextStyle = style(13.sp, FontWeight.SemiBold),
    val caption13Medium: TextStyle = style(13.sp, FontWeight.Medium),
    val caption13: TextStyle = style(13.sp, FontWeight.Normal),
    val label12: TextStyle = style(12.sp, FontWeight.SemiBold),
    /** 번호 배지·번호 핀 (S-06의 1 2 3). 시안은 배지 11.76, 핀 12인데 12로 통일. */
    val badge12: TextStyle = style(12.sp, FontWeight.Bold),
    val caption12Medium: TextStyle = style(12.sp, FontWeight.Medium),
    val caption12: TextStyle = style(12.sp, FontWeight.Normal),
    /** 지도 핀 이름표, 지역 라벨 */
    val label11: TextStyle = style(11.sp, FontWeight.SemiBold, lineHeight = 1.3f),
    /** 지도 줌 버튼 + − */
    val zoom20: TextStyle = style(20.sp, FontWeight.Medium, lineHeight = 1.3f),
    /** 링크·칩 끝의 › */
    val chevron16: TextStyle = style(16.sp, FontWeight.Medium, lineHeight = 1.3f),
    /** 큰 매장 핀 안 금액 (지름 56 이상, "6.2만") */
    val pinAmountLarge: TextStyle = style(15.sp, FontWeight.Bold, lineHeight = 1.3f),
    /** 작은 매장 핀 안 금액 ("1만") */
    val pinAmountSmall: TextStyle = style(12.sp, FontWeight.Bold, lineHeight = 1.3f),
    /** 느낌표 핀의 ! (시안 ExtraBold 17을 Bold로) */
    val pinMark: TextStyle = style(17.sp, FontWeight.Bold, lineHeight = 1.3f),
)
