package com.wherepaid.app.feature.map.candidate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wherepaid.app.core.designsystem.component.ButtonSize
import com.wherepaid.app.core.designsystem.component.ButtonStyle
import com.wherepaid.app.core.designsystem.component.SheetHandle
import com.wherepaid.app.core.designsystem.component.WherePaidButton
import com.wherepaid.app.core.designsystem.theme.WherePaidShapes
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.model.Place
import com.wherepaid.app.core.model.PlaceStatus
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import java.time.YearMonth

/**
 * S-04 느낌표 핀 후보 고르기 시트. 지도의 느낌표 핀과 S-05 [여기예요]에서 연다.
 * 무엇을 고르든 저장 후 결제가 느낌표 핀에서 빠지면 [onDismiss]로 닫힌다.
 * [기억 안 나요]는 시안에 없는 버튼이다 (매칭실패함 + 장소 확인에서 제외).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatePickerRoute(
    transactionId: String,
    repository: TransactionRepository,
    onDismiss: () -> Unit,
) {
    val viewModel: CandidatePickerViewModel = viewModel(key = "candidate-$transactionId") {
        CandidatePickerViewModel(repository, transactionId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == CandidatePickerUiState.Done) onDismiss()
    }
    val picking = state as? CandidatePickerUiState.Picking ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = WherePaidShapes.sheet,
        containerColor = WherePaidTheme.colors.background,
        scrimColor = WherePaidTheme.colors.scrim,
        dragHandle = null,
    ) {
        CandidatePickerContent(
            state = picking,
            onChoose = viewModel::choose,
            onOnline = viewModel::markOnline,
            onNotHere = viewModel::markNotHere,
            onForgot = viewModel::markForgotten,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** 시트 안쪽. 시안 프레임 4:148의 "Bottom sheet". */
@Composable
fun CandidatePickerContent(
    state: CandidatePickerUiState.Picking,
    onChoose: (Place) -> Unit,
    onOnline: () -> Unit,
    onNotHere: () -> Unit,
    onForgot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHandle()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("어디서 결제했나요?", style = type.title20, color = colors.textPrimary)
            Text(state.summary, style = type.caption13, color = colors.textSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.candidates.forEach { candidate ->
                CandidateRow(candidate, onClick = { onChoose(candidate.place) })
            }
        }
        Text("한 번 고르면 다음부터는 자동으로 찍어드려요", style = type.caption12, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WherePaidButton("온라인 결제예요", onOnline, Modifier.weight(1f), ButtonStyle.Muted, ButtonSize.Medium)
            WherePaidButton("여기 아니에요", onNotHere, Modifier.weight(1f), ButtonStyle.Muted, ButtonSize.Medium)
        }
        // 시안 밖 추가 (보충 B): 장소 확인 목록에서 뺀다. 총액에는 남는다.
        WherePaidButton("기억 안 나요", onForgot, Modifier.fillMaxWidth(), ButtonStyle.Muted, ButtonSize.Small)
    }
}

@Composable
private fun CandidateRow(candidate: CandidateItem, onClick: () -> Unit) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .clip(WherePaidShapes.large)
            .border(1.dp, colors.border, WherePaidShapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.warning))
        Text(candidate.place.name, style = type.body15Strong, color = colors.textPrimary, modifier = Modifier.weight(1f))
        candidate.distance?.let { Text(it, style = type.caption13, color = colors.textSecondary) }
    }
}

@Preview(name = "기본 (4:148)", showBackground = true, widthDp = 360)
@Composable
private fun CandidatePickerPreview() {
    val month = YearMonth.now()
    val gs25 = SampleData.transactions(month).first { it.status == PlaceStatus.AMBIGUOUS && it.merchantRaw == "GS25" }
    val state = toCandidatePickerUiState(gs25, month.atDay(11)) as CandidatePickerUiState.Picking
    WherePaidTheme { CandidatePickerContent(state, {}, {}, {}, {}) }
}
