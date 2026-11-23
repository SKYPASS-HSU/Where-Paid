package com.wherepaid.app.feature.placecheck.find

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wherepaid.app.core.designsystem.component.BackHeader
import com.wherepaid.app.core.designsystem.component.ButtonSize
import com.wherepaid.app.core.designsystem.component.ButtonStyle
import com.wherepaid.app.core.designsystem.component.ConfirmDialog
import com.wherepaid.app.core.designsystem.component.RadioMark
import com.wherepaid.app.core.designsystem.component.SearchField
import com.wherepaid.app.core.designsystem.component.SkeletonRow
import com.wherepaid.app.core.designsystem.component.StateBlock
import com.wherepaid.app.core.designsystem.component.WherePaidButton
import com.wherepaid.app.core.designsystem.theme.WherePaidShapes
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.model.PlaceResolver
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.fake.SampleData

/**
 * S-06 매장 찾기 (+ D-02 등록 전 나가기). 탭이 아닌 화면이라 하단 탭 바가 없다.
 * @param onFinished 등록했거나 나갔을 때. 이전 화면으로 돌아간다.
 */
@Composable
fun FindPlaceRoute(
    transactionId: String,
    repository: TransactionRepository,
    resolver: PlaceResolver,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FindPlaceViewModel = viewModel(key = "find-$transactionId") {
        FindPlaceViewModel(repository, resolver, transactionId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    BackHandler(onBack = viewModel::onBack)
    FindPlaceScreen(
        state = state,
        onBack = viewModel::onBack,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onSelect = viewModel::select,
        onRegister = viewModel::register,
        onOnline = viewModel::markOnline,
        onForgot = viewModel::markForgotten,
        modifier = modifier,
    )
    if (state.showLeaveDialog) {
        val name = state.selected?.place?.name.orEmpty()
        ConfirmDialog(
            title = "선택한 매장을 등록하지 않고\n나갈까요?",
            summary = "$name 선택됨",
            message = "나가도 이 결제는 장소 확인에\n그대로 남아 있어요",
            dismissText = "나가기",
            onDismiss = viewModel::leave,
            confirmText = "계속 고르기",
            onConfirm = viewModel::keepChoosing,
            onDismissRequest = viewModel::keepChoosing,
        )
    }
}

/** 시안 프레임: 기본 5:71, 로딩 61:2, 오프라인 61:66, 0곳 61:125. */
@Composable
fun FindPlaceScreen(
    state: FindPlaceUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (String) -> Unit,
    onRegister: () -> Unit,
    onOnline: () -> Unit,
    onForgot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Column(modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        BackHeader("매장 찾기", onBack = onBack)
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                state.summary,
                style = type.caption13Medium,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WherePaidShapes.medium)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            SearchField(state.query, onQueryChange, onSearch)
        }
        MiniMap(state, onSelect)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val results = state.results) {
                SearchResults.Idle -> Unit
                SearchResults.Loading -> Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                    SkeletonRow(150.dp, 110.dp)
                    SkeletonRow(120.dp, 140.dp)
                    SkeletonRow(170.dp, 90.dp)
                }
                SearchResults.Offline -> StateBlock(
                    symbol = "!",
                    title = "인터넷 연결 후\n등록할 수 있어요",
                    message = "연결되면 검색 결과를\n다시 불러와요",
                    symbolColor = colors.danger,
                    symbolContainer = colors.dangerContainer,
                    actionText = "다시 시도",
                    onAction = onSearch,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                )
                is SearchResults.Empty -> StateBlock(
                    symbol = "?",
                    title = "\"${results.query}\"로 찾은 매장이 없어요",
                    message = "지점명을 빼거나 한글로 바꿔서\n다시 검색해 보세요",
                    symbolColor = colors.textSecondary,
                    symbolContainer = colors.surfaceMuted,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
                )
                is SearchResults.Found -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp)) {
                    items(results.places, key = { it.place.id }) { found ->
                        ResultRow(found, selected = found.place.id == state.selectedId, onClick = { onSelect(found.place.id) })
                    }
                }
            }
        }
        // 오프라인·로딩 중에는 온라인 전환도 숨긴다 (시안 61:2, 61:66).
        if (state.results != SearchResults.Loading && state.results != SearchResults.Offline) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("찾는 매장이 없나요?", style = type.caption13, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WherePaidButton("온라인 결제예요", onOnline, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                    // 시안 밖 추가 (보충 B): 장소 확인 목록에서 뺀다. 총액에는 남는다.
                    WherePaidButton("기억 안 나요", onForgot, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        WherePaidButton(
            "이 장소로 등록",
            onClick = onRegister,
            enabled = state.canRegister,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultRow(found: FoundPlace, selected: Boolean, onClick: () -> Unit) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(found.number, selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(found.place.name, style = type.body15Strong, color = colors.textPrimary)
            Text(found.detail, style = type.caption12, color = colors.textSecondary)
        }
        RadioMark(selected)
    }
}

@Composable
private fun NumberBadge(number: Int, selected: Boolean) {
    val colors = WherePaidTheme.colors
    Box(
        Modifier.size(28.dp).clip(WherePaidShapes.small).background(if (selected) colors.primary else colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = WherePaidTheme.typography.badge12,
            color = if (selected) colors.onPrimary else colors.textSecondary,
        )
    }
}

/**
 * 검색 결과 위치를 보여주는 작은 지도 자리 (높이 200).
 * TODO(③): 카카오맵 SDK가 붙으면 실제 지도로 바꾼다. 지금은 결과 좌표를 칸 안에 비율대로 찍기만 한다.
 */
@Composable
private fun MiniMap(state: FindPlaceUiState, onSelect: (String) -> Unit) {
    val colors = WherePaidTheme.colors
    val results = state.results
    val background = if (results == SearchResults.Loading || results == SearchResults.Offline) colors.border else colors.mapBackground
    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp).background(background)) {
        if (results == SearchResults.Offline) {
            Text(
                "지도를 불러올 수 없어요",
                style = WherePaidTheme.typography.caption13Medium,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val places = (results as? SearchResults.Found)?.places.orEmpty()
        if (places.isEmpty()) return@BoxWithConstraints
        val lats = places.map { it.place.latLng.latitude }
        val lngs = places.map { it.place.latLng.longitude }
        val pin = 28.dp
        val pad = 24.dp
        val usableW = maxWidth - pad * 2 - pin
        val usableH = maxHeight - pad * 2 - pin
        fun ratio(value: Double, min: Double, max: Double) = if (max - min < 1e-9) 0.5f else ((value - min) / (max - min)).toFloat()
        // 선택된 핀이 위에 오도록 마지막에 그린다.
        places.sortedBy { it.place.id == state.selectedId }.forEach { found ->
            val x = pad + usableW * ratio(found.place.latLng.longitude, lngs.min(), lngs.max())
            val y = pad + usableH * (1f - ratio(found.place.latLng.latitude, lats.min(), lats.max()))
            MapPin(found.number, found.place.id == state.selectedId, Modifier.offset(x, y).clickable { onSelect(found.place.id) })
        }
    }
}

@Composable
private fun MapPin(number: Int, selected: Boolean, modifier: Modifier) {
    val colors = WherePaidTheme.colors
    Box(
        modifier
            .size(28.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.background)
            .border(2.dp, if (selected) colors.background else colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = WherePaidTheme.typography.badge12,
            color = if (selected) colors.onPrimary else colors.textPrimary,
        )
    }
}

// --- 시안 상태별 미리보기 ---

private val previewSummary = "(주)케이에프씨코리 · 9,800원 · 10.09 19:20"
private val previewFound = SearchResults.Found(
    toFoundPlaces(listOf(SampleData.kfcSungshin) + SampleData.kfcOthers, SampleData.kfcSungshin.latLng),
)

@Composable
private fun Preview(state: FindPlaceUiState) {
    WherePaidTheme { FindPlaceScreen(state, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "기본 (5:71)", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FindPlaceFoundPreview() =
    Preview(FindPlaceUiState(previewSummary, "KFC", previewFound, selectedId = SampleData.kfcSungshin.id))

@Preview(name = "로딩 (61:2)", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FindPlaceLoadingPreview() = Preview(FindPlaceUiState(previewSummary, "KFC", SearchResults.Loading))

@Preview(name = "오프라인 (61:66)", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FindPlaceOfflinePreview() = Preview(FindPlaceUiState(previewSummary, "KFC", SearchResults.Offline))

@Preview(name = "0곳 (61:125)", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FindPlaceEmptyPreview() = Preview(FindPlaceUiState(previewSummary, "KFC", SearchResults.Empty("KFC")))
