package com.wherepaid.app.feature.map.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wherepaid.app.core.designsystem.component.BannerTone
import com.wherepaid.app.core.designsystem.component.CountPill
import com.wherepaid.app.core.designsystem.component.MapBanner
import com.wherepaid.app.core.designsystem.component.StatusBadge
import com.wherepaid.app.core.designsystem.component.ZoomControls
import com.wherepaid.app.core.designsystem.component.floatingShadow
import com.wherepaid.app.core.designsystem.theme.WherePaidShapes
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.feature.map.detail.PinDetailRoute
import com.wherepaid.app.core.model.fake.SampleData
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * S-02 지도 홈 (탭 1). 저장소·신호와 연결된 진입점.
 *
 * @param onOpenCandidates 느낌표 핀 → S-04 후보 고르기 (결제 id). 매장 핀의 S-03 핀 상세는 이 화면이 직접 띄운다.
 * @param onChangePlace 핀 상세의 [장소 변경] → S-06 매장 찾기 (결제 id)
 * @param onOpenOnline 카드의 "지도에 없어요 ›" → S-07 온라인 결제
 * @param onOpenPlaceCheck "장소 확인이 필요한 결제" 칩 → 탭 2
 * @param onBannerAction 배너 오른쪽 버튼 (켜기 / 설정 / 보기)
 */
@Composable
fun MapHomeRoute(
    repository: TransactionRepository,
    signals: Flow<MapSignals>,
    onOpenCandidates: (String) -> Unit,
    onChangePlace: (String) -> Unit,
    onOpenOnline: (YearMonth) -> Unit,
    onOpenPlaceCheck: () -> Unit,
    onBannerAction: (MapBannerKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MapHomeViewModel = viewModel { MapHomeViewModel(repository, signals) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = rememberMapController()
    var selectedPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    MapHomeScreen(
        state = state,
        selectableMonths = viewModel.selectableMonths,
        onSelectMonth = viewModel::selectMonth,
        onOpenOnline = { onOpenOnline(state.month) },
        onOpenPlaceCheck = onOpenPlaceCheck,
        onBannerAction = onBannerAction,
        onZoomIn = { controller.zoomIn() },
        onZoomOut = { controller.zoomOut() },
        modifier = modifier,
        map = { mapModifier ->
            KakaoMapLayer(
                pins = state.pins,
                cameraKey = state.month,
                selectedPlaceId = selectedPlaceId,
                controller = controller,
                onPinClick = { pin ->
                    when (pin) {
                        is MapPin.Store -> selectedPlaceId = pin.placeId
                        is MapPin.Ambiguous -> onOpenCandidates(pin.transactionId)
                    }
                },
                modifier = mapModifier,
                contentPaddingTopDp = SUMMARY_CARD_SPACE_DP,
            )
        },
    )

    selectedPlaceId?.let { placeId ->
        PinDetailRoute(
            placeId = placeId,
            month = state.month,
            repository = repository,
            onChangePlace = { transactionId ->
                selectedPlaceId = null
                onChangePlace(transactionId)
            },
            onDismiss = { selectedPlaceId = null },
        )
    }
}

/**
 * S-02 지도 홈. 지도는 [map] 슬롯으로 받는다 (미리보기에서는 회색 자리).
 * 시안 프레임: 기본 66:2, 비어 있음 60:7, 자동 기록 꺼짐 7:116, 위치 사용 중에만 60:79, 리포트 도착 60:147, 오프라인 60:215.
 */
@Composable
fun MapHomeScreen(
    state: MapHomeUiState,
    selectableMonths: List<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenOnline: () -> Unit,
    onOpenPlaceCheck: () -> Unit,
    onBannerAction: (MapBannerKind) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable (Modifier) -> Unit = { MapPlaceholder(it) },
) {
    Box(modifier.fillMaxSize()) {
        map(Modifier.fillMaxSize())

        Column(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(state, selectableMonths, onSelectMonth, onOpenOnline)
            state.banner?.let { MapHomeBanner(it, state.arrivedReport, onBannerAction) }
            if (!state.isEmpty) {
                ZoomControls(onZoomIn, onZoomOut, Modifier.align(Alignment.End))
            }
        }

        if (state.isEmpty) {
            EmptyCard(state.month, state.autoRecordEnabled, Modifier.align(Alignment.Center).padding(horizontal = 32.dp))
        }

        if (state.needsPlaceCount > 0) {
            CountPill(
                count = state.needsPlaceCount,
                text = "장소 확인이 필요한 결제",
                onClick = onOpenPlaceCheck,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    state: MapHomeUiState,
    selectableMonths: List<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenOnline: () -> Unit,
) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    var monthMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .floatingShadow(WherePaidShapes.floatingCard)
            .clip(WherePaidShapes.floatingCard)
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            Row(
                Modifier.clickable(role = Role.DropdownList) { monthMenu = true },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.month.monthValue}월 오프라인 소비", style = type.caption13Medium, color = colors.textSecondary)
                Text("▾", style = type.caption12Medium, color = colors.textSecondary)
            }
            DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                selectableMonths.forEach { month ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${month.year}년 ${month.monthValue}월",
                                style = if (month == state.month) type.body15Strong else type.body15,
                                color = if (month == state.month) colors.primary else colors.textPrimary,
                            )
                        },
                        onClick = {
                            monthMenu = false
                            onSelectMonth(month)
                        },
                    )
                }
            }
        }
        Text(state.offlineTotal.ifEmpty { "0원" }, style = type.amount28, color = colors.textPrimary)
        Row(
            Modifier.padding(top = 6.dp).clickable(role = Role.Button, onClick = onOpenOnline),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val online = state.onlineTotal
            Text(
                buildAnnotatedString {
                    if (online == null) {
                        append("이번 달 온라인 결제도 아직 없어요")
                    } else {
                        append("온라인 결제 ${online}은 ")
                        withStyle(SpanStyle(color = colors.primary)) { append("지도에 없어요") }
                    }
                },
                style = type.caption12,
                color = colors.textHint,
            )
            Text("›", style = type.body14Medium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun MapHomeBanner(kind: MapBannerKind, arrivedReport: YearMonth?, onAction: (MapBannerKind) -> Unit) {
    when (kind) {
        MapBannerKind.AutoRecordOff ->
            MapBanner("자동 기록이 꺼져 있어요", BannerTone.Dark, actionText = "켜기", onAction = { onAction(kind) })
        MapBannerKind.LocationNotAlways ->
            MapBanner("결제 장소를 찾으려면 위치를 항상 허용해 주세요", BannerTone.Dark, actionText = "설정", onAction = { onAction(kind) })
        MapBannerKind.ReportArrived ->
            MapBanner("${arrivedReport?.monthValue ?: ""}월 리포트가 도착했어요", BannerTone.Accent, actionText = "보기 ›", onAction = { onAction(kind) })
        MapBannerKind.Offline ->
            MapBanner("오프라인 · 저장된 지도만 보여요", BannerTone.Muted)
    }
}

/** 시안 60:71 "Empty block". */
@Composable
private fun EmptyCard(month: YearMonth, autoRecordEnabled: Boolean, modifier: Modifier = Modifier) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Column(
        modifier
            .floatingShadow(WherePaidShapes.floatingCard)
            .clip(WherePaidShapes.floatingCard)
            .background(colors.background)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(56.dp)) {
            drawCircle(colors.primaryContainer)
            drawCircle(colors.background, radius = 10.dp.toPx())
            drawCircle(colors.primary, radius = 8.dp.toPx())
        }
        Spacer(Modifier.height(4.dp))
        Text("${month.monthValue}월에 기록된 결제가 없어요", style = type.title18, color = colors.textPrimary)
        Text(
            "토스로 결제하면 자동으로\n지도에 찍혀요",
            style = type.paragraph15,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        if (autoRecordEnabled) {
            StatusBadge("✓ 자동 기록 켜짐", colors.successContainer, colors.success)
        }
    }
}

/** 미리보기와 지도 SDK가 없는 환경용 지도 자리. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(WherePaidTheme.colors.mapBackground))
}

/** 카드와 배너가 차지하는 위쪽 높이 (카메라를 맞출 때 피한다). */
private const val SUMMARY_CARD_SPACE_DP = 160

// --- 시안 상태별 미리보기 ---

private val previewMonth = YearMonth.of(2026, 10)

@Composable
private fun Preview(signals: MapSignals = MapSignals(), empty: Boolean = false) {
    val transactions = if (empty) emptyList() else SampleData.transactions(previewMonth)
    val needs = transactions.count { it.needsPlace }
    val state = toMapHomeUiState(previewMonth, transactions, needs, signals)
    WherePaidTheme {
        MapHomeScreen(state, listOf(previewMonth), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "기본 (66:2)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeDefaultPreview() = Preview()

@Preview(name = "비어 있음 (60:7)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeEmptyPreview() = Preview(empty = true)

@Preview(name = "자동 기록 꺼짐 (7:116)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeAutoRecordOffPreview() = Preview(MapSignals(autoRecordEnabled = false))

@Preview(name = "위치 사용 중에만 (60:79)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeLocationPreview() = Preview(MapSignals(locationAlwaysAllowed = false))

@Preview(name = "리포트 도착 (60:147)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeReportPreview() = Preview(MapSignals(arrivedReport = previewMonth))

@Preview(name = "오프라인 (60:215)", showBackground = true, widthDp = 360, heightDp = 728)
@Composable
private fun MapHomeOfflinePreview() = Preview(MapSignals(isOnline = false))
