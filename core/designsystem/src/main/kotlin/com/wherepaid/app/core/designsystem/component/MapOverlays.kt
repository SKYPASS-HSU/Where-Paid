package com.wherepaid.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wherepaid.app.core.designsystem.theme.WherePaidShapes
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme

/** 지도 위에 떠 있는 요소의 그림자 (시안: 0 6 20 rgba(0,0,0,0.10)). */
fun Modifier.floatingShadow(shape: androidx.compose.ui.graphics.Shape): Modifier = shadow(12.dp, shape, clip = false)

/** 배너 색 (시안 S-02): 어두운 = 자동 기록 꺼짐·위치 권한, 강조 = 리포트 도착, 흐린 = 오프라인. */
enum class BannerTone { Dark, Accent, Muted }

/**
 * 지도 위 알림 배너 (S-02 7:179, 60:143, 60:211, 60:281). 오른쪽 작은 버튼은 없어도 된다.
 * 시안은 배너마다 글자 13/14, 모서리 14/16이 섞여 있어 13·16으로 통일했다.
 */
@Composable
fun MapBanner(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    val shape = RoundedCornerShape(16.dp)
    val background = when (tone) {
        BannerTone.Dark -> colors.inverseSurface
        BannerTone.Accent -> colors.primary
        BannerTone.Muted -> colors.textTertiary
    }
    // 어두운 배너의 버튼은 주황 바탕 흰 글자, 주황 배너의 버튼은 흰 바탕 주황 글자.
    val (actionBackground, actionContent) = when (tone) {
        BannerTone.Accent -> colors.background to colors.primary
        else -> colors.primary to colors.onPrimary
    }
    Row(
        modifier
            .fillMaxWidth()
            .floatingShadow(shape)
            .clip(shape)
            .background(background)
            .then(if (actionText != null) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = type.label13, color = Color.White, modifier = Modifier.weight(1f))
        if (actionText != null) {
            Box(Modifier.clip(WherePaidShapes.small).background(actionBackground).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(actionText, style = type.label13, color = actionContent)
            }
        }
    }
}

/** 지도 아래쪽 어두운 알약: "(5) 장소 확인이 필요한 결제 ›" (S-02 66:50). */
@Composable
fun CountPill(count: Int, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .floatingShadow(shape)
            .clip(shape)
            .background(colors.inverseSurface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(colors.warning), contentAlignment = Alignment.Center) {
            Text(count.toString(), style = type.badge12, color = Color.White)
        }
        Text(text, style = type.label14, color = Color.White)
        Text("›", style = type.chevron16, color = Color.White)
    }
}

/** 지도 줌 버튼 묶음 + / − (S-02 66:187). */
@Composable
fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WherePaidTheme.colors
    val shape = WherePaidShapes.large
    Column(
        modifier.floatingShadow(shape).clip(shape).background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZoomButton("+", "지도 확대", onZoomIn)
        Box(Modifier.width(28.dp).height(1.dp).background(colors.border))
        ZoomButton("−", "지도 축소", onZoomOut)
    }
}

@Composable
private fun ZoomButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = WherePaidTheme.typography.zoom20, color = WherePaidTheme.colors.textPrimary)
    }
}

@Preview(showBackground = true, widthDp = 360, backgroundColor = 0xFFEEF0F3)
@Composable
private fun MapOverlaysPreview() {
    WherePaidTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MapBanner("자동 기록이 꺼져 있어요", BannerTone.Dark, actionText = "켜기")
            MapBanner("결제 장소를 찾으려면 위치를 항상 허용해 주세요", BannerTone.Dark, actionText = "설정")
            MapBanner("10월 리포트가 도착했어요", BannerTone.Accent, actionText = "보기 ›")
            MapBanner("오프라인 · 저장된 지도만 보여요", BannerTone.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CountPill(5, "장소 확인이 필요한 결제", onClick = {})
                ZoomControls({}, {})
            }
        }
    }
}
