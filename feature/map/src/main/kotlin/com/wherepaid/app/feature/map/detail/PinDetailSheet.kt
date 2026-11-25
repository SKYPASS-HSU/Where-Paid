package com.wherepaid.app.feature.map.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wherepaid.app.core.designsystem.component.ButtonStyle
import com.wherepaid.app.core.designsystem.component.ConfirmDialog
import com.wherepaid.app.core.designsystem.component.HairlineDivider
import com.wherepaid.app.core.designsystem.component.SheetHandle
import com.wherepaid.app.core.designsystem.theme.WherePaidShapes
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.fake.SampleData
import java.time.YearMonth

/**
 * S-03 핀 상세 (+ D-01 삭제 확인). 지도의 매장 핀을 누르면 지도 위에 올라온다.
 * 시트 뒤 지도는 어둡게 하지 않는다 (시안 4:40).
 *
 * @param onChangePlace [장소 변경] → S-06 매장 찾기 (결제 id)
 * @param onDismiss 닫기. 결제를 모두 지우거나 옮겨 이 매장에 남은 결제가 없어도 닫힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailRoute(
    placeId: String,
    month: YearMonth,
    repository: TransactionRepository,
    onChangePlace: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: PinDetailViewModel = viewModel(key = "pin-$placeId-$month") {
        PinDetailViewModel(repository, placeId, month)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == PinDetailUiState.Gone) onDismiss()
    }
    val shown = state as? PinDetailUiState.Shown ?: return
    var deleting by remember { mutableStateOf<PinDetailRow?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = WherePaidShapes.sheet,
        containerColor = WherePaidTheme.colors.background,
        scrimColor = Color.Transparent,
        dragHandle = null,
    ) {
        PinDetailContent(
            state = shown,
            onChangePlace = { onChangePlace(it.transactionId) },
            onDelete = { deleting = it },
            modifier = Modifier.navigationBarsPadding(),
        )
    }

    deleting?.let { row ->
        ConfirmDialog(
            title = "이 결제를 삭제할까요?",
            summary = row.deleteSummary,
            message = "지도와 총액에서 빠져요\n고른 매장은 그대로 기억해요",
            dismissText = "취소",
            onDismiss = { deleting = null },
            confirmText = "삭제",
            confirmStyle = ButtonStyle.Danger,
            onConfirm = {
                viewModel.delete(row.transactionId)
                deleting = null
            },
        )
    }
}

/** 시트 안쪽. 시안 4:40의 "Bottom sheet". */
@Composable
fun PinDetailContent(
    state: PinDetailUiState.Shown,
    onChangePlace: (PinDetailRow) -> Unit,
    onDelete: (PinDetailRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHandle()
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.placeName, style = type.title20, color = colors.textPrimary)
                Text(state.subtitle, style = type.caption13, color = colors.textSecondary)
            }
            Text(state.total, style = type.title20, color = colors.primary)
        }
        HairlineDivider()
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            state.rows.forEach { row -> PaymentLine(row, onChangePlace, onDelete) }
        }
    }
}

@Composable
private fun PaymentLine(row: PinDetailRow, onChangePlace: (PinDetailRow) -> Unit, onDelete: (PinDetailRow) -> Unit) {
    val colors = WherePaidTheme.colors
    val type = WherePaidTheme.typography
    val accent = if (row.isCancellation) colors.danger else null
    var menu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.dateTime, style = type.body15Medium, color = colors.textPrimary)
            Text(row.detail, style = type.caption12, color = accent ?: colors.textSecondary)
        }
        Text(row.amount, style = type.body15Strong, color = accent ?: colors.textPrimary)
        Box {
            Text(
                "⋯",
                style = type.more16,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { menu = true }
                    .semantics { contentDescription = "결제 메뉴" }
                    .padding(4.dp),
            )
            // 시안 "row menu (⋯ 누르면)": 흰 바탕, 모서리 12, 장소 변경 / 삭제(빨강)
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = WherePaidShapes.medium,
                containerColor = colors.background,
            ) {
                DropdownMenuItem(
                    text = { Text("장소 변경", style = type.body14Medium, color = colors.textPrimary) },
                    onClick = {
                        menu = false
                        onChangePlace(row)
                    },
                )
                DropdownMenuItem(
                    text = { Text("삭제", style = type.body14Medium, color = colors.danger) },
                    onClick = {
                        menu = false
                        onDelete(row)
                    },
                )
            }
        }
    }
}

@Preview(name = "기본 (4:40)", showBackground = true, widthDp = 360)
@Composable
private fun PinDetailPreview() {
    val month = YearMonth.of(2026, 10)
    val state = toPinDetailUiState(
        SampleData.starbucksSeongsu.id, month, SampleData.transactions(month), month.atDay(12),
    ) as PinDetailUiState.Shown
    WherePaidTheme {
        Box(Modifier.background(WherePaidTheme.colors.background)) { PinDetailContent(state, {}, {}) }
    }
}
