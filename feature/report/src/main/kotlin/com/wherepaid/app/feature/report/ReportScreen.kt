package com.wherepaid.app.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wherepaid.app.core.designsystem.component.ButtonSize
import com.wherepaid.app.core.designsystem.component.ButtonStyle
import com.wherepaid.app.core.designsystem.component.ConfirmDialog
import com.wherepaid.app.core.designsystem.component.ScreenHeader
import com.wherepaid.app.core.designsystem.component.WherePaidButton
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme

/**
 * S-08 월간 리포트 (탭 3). 아직 자리표시다.
 * 이 모듈에 들어갈 화면: S-08 월간 리포트, S-09 스타일 카드 공유. 피그마 섹션 ⑦⑧.
 *
 * [데이터 초기화]는 시안에 없는 기능이라 설정 화면이 생길 때까지 임시로 이 탭 아래에 둔다 (보충 B).
 * 지우는 일은 [onResetData]를 받는 쪽(:app)이 저장소의 clearAll로 한다.
 */
@Composable
fun ReportScreen(onResetData: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ScreenHeader("리포트")
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("준비 중", style = WherePaidTheme.typography.body15Medium, color = WherePaidTheme.colors.textSecondary)
        }
        ResetDataSection(onClick = { confirming = true })
    }
    if (confirming) {
        ConfirmDialog(
            title = "데이터를 모두 지울까요?",
            message = "결제 기록, 고른 매장, 온라인 지정이\n모두 사라지고 되돌릴 수 없어요",
            confirmText = "모두 지우기",
            dismissText = "취소",
            onConfirm = {
                confirming = false
                onResetData()
            },
            onDismiss = { confirming = false },
            confirmStyle = ButtonStyle.Danger,
        )
    }
}

@Composable
private fun ResetDataSection(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "앱 데이터를 처음 상태로 되돌려요",
            style = WherePaidTheme.typography.caption13,
            color = WherePaidTheme.colors.textSecondary,
        )
        WherePaidButton("데이터 초기화", onClick, style = ButtonStyle.Secondary, size = ButtonSize.Small)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ReportScreenPreview() {
    WherePaidTheme { ReportScreen(onResetData = {}) }
}
