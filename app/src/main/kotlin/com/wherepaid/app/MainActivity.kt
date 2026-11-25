package com.wherepaid.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wherepaid.app.core.designsystem.component.BottomTab
import com.wherepaid.app.core.designsystem.component.WherePaidBottomBar
import com.wherepaid.app.core.designsystem.component.WherePaidIcons
import com.wherepaid.app.core.designsystem.theme.WherePaidTheme
import com.wherepaid.app.core.location.LocationPermissions
import com.wherepaid.app.core.notification.DebugTestNotification
import com.wherepaid.app.core.notification.NotificationAccess
import com.wherepaid.app.core.notification.NotificationPaymentSource
import com.wherepaid.app.feature.map.candidate.CandidatePickerRoute
import com.wherepaid.app.feature.map.home.MapBannerKind
import com.wherepaid.app.feature.map.home.MapHomeRoute
import com.wherepaid.app.feature.map.home.MapSignals
import com.wherepaid.app.feature.onboarding.OnboardingScreen
import com.wherepaid.app.feature.placecheck.PlaceCheckRoute
import com.wherepaid.app.feature.placecheck.find.FindPlaceRoute
import com.wherepaid.app.feature.report.ReportScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WherePaidTheme {
                WherePaidNav(appContainer)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 위치 권한을 바꾸고 돌아오면 배너를 다시 계산한다.
        appContainer.locationAccess.refresh()
    }
}

/** 화면 경로. 하위 화면(S-06 매장 찾기, S-07 온라인 결제, S-09 공유)은 화면을 만들 때 여기에 추가한다. */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val MAP = "map"
    const val PLACE_CHECK = "place-check"
    const val REPORT = "report"

    /** S-06 매장 찾기. 탭이 아니라 하단 탭 바가 없다. */
    const val FIND_PLACE = "find-place/{transactionId}"
    fun findPlace(transactionId: String) = "find-place/$transactionId"
}

/** 하단 탭 (피그마 시안: 지도 / 장소 확인 / 리포트). */
private enum class TopLevelDestination(val route: String, val tab: BottomTab) {
    MAP(Routes.MAP, BottomTab("지도", WherePaidIcons.tabMap)),
    PLACE_CHECK(Routes.PLACE_CHECK, BottomTab("장소 확인", WherePaidIcons.tabPlace)),
    REPORT(Routes.REPORT, BottomTab("리포트", WherePaidIcons.tabReport)),
}

@Composable
private fun WherePaidNav(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = TopLevelDestination.entries.indexOfFirst { top ->
        destination?.hierarchy?.any { it.route == top.route } == true
    }
    val startDestination = remember { if (container.onboardingStore.isDone) Routes.MAP else Routes.ONBOARDING }
    // S-04 후보 고르기 시트는 지도와 장소 확인 어디서든 열리므로 화면 경로가 아니라 여기서 띄운다.
    var candidateSheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    // 데이터 초기화는 탭을 옮겨도 끝까지 하도록 탭 화면이 아니라 내비게이션 쪽 코루틴에서 돌린다.
    val scope = rememberCoroutineScope()

    Scaffold(
        // 화면이 상태 바 영역을 직접 다룬다 (지도는 상태 바 밑까지 그린다).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selectedTab >= 0) {
                WherePaidBottomBar(
                    tabs = TopLevelDestination.entries.map { it.tab },
                    selectedIndex = selectedTab,
                    onSelect = { navController.navigateToTab(TopLevelDestination.entries[it].route) },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onStart = {
                        container.onboardingStore.markDone()
                        navController.navigate(Routes.MAP) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.MAP) {
                    val context = LocalContext.current
                    val signals = remember {
                        combine(
                            container.autoRecordEnabled,
                            container.locationAlwaysAllowed,
                            container.arrivedReport,
                            container.networkMonitor.isOnline,
                        ) { autoRecord, locationAlways, report, online -> MapSignals(autoRecord, locationAlways, report, online) }
                    }
                    fun soon(what: String) = Toast.makeText(context, "$what 화면은 준비 중이에요", Toast.LENGTH_SHORT).show()
                    MapHomeRoute(
                        repository = container.transactionRepository,
                        signals = signals,
                        onOpenCandidates = { candidateSheetFor = it },
                        onChangePlace = { navController.navigate(Routes.findPlace(it)) },
                        // TODO(③): S-07 온라인 결제
                        onOpenOnline = { soon("온라인 결제") },
                        onOpenPlaceCheck = { navController.navigateToTab(Routes.PLACE_CHECK) },
                        onBannerAction = { kind ->
                            when (kind) {
                                // TODO(①): 알림 접근 설정 화면으로 이동
                                MapBannerKind.AutoRecordOff -> soon("권한 설정")
                                // 앱 설정 화면 → 권한 → 위치 → "항상 허용"과 "정확한 위치 사용"
                                MapBannerKind.LocationNotAlways ->
                                    context.startActivity(LocationPermissions.appSettingsIntent(context))
                                MapBannerKind.ReportArrived -> navController.navigateToTab(Routes.REPORT)
                                MapBannerKind.Offline -> Unit
                            }
                        },
                    )
                }
                composable(Routes.PLACE_CHECK) {
                    PlaceCheckRoute(
                        repository = container.transactionRepository,
                        isOnline = container.networkMonitor.isOnline,
                        onChooseCandidate = { candidateSheetFor = it },
                        onFindPlace = { navController.navigate(Routes.findPlace(it)) },
                        onOpenMap = { navController.navigateToTab(Routes.MAP) },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
                composable(Routes.REPORT) {
                    ReportScreen(
                        onResetData = {
                            container.reportArrivalStore.clear()
                            scope.launch { container.transactionRepository.clearAll() }
                        },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
                composable(
                    Routes.FIND_PLACE,
                    arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
                ) { entry ->
                    FindPlaceRoute(
                        transactionId = entry.arguments?.getString("transactionId").orEmpty(),
                        repository = container.transactionRepository,
                        resolver = container.placeResolver,
                        onFinished = { navController.popBackStack() },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            }

            // 개발용 임시 패널 (Debug 빌드에서만). 실제 결제 없이
            // Notification → Listener → Parser → PaymentSource 흐름을 확인한다.
            if (BuildConfig.DEBUG) {
                DebugNotificationPanel(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp),
                )
            }
        }
    }

    candidateSheetFor?.let { id ->
        CandidatePickerRoute(
            transactionId = id,
            repository = container.transactionRepository,
            onDismiss = { candidateSheetFor = null },
        )
    }
}

/**
 * 실제 토스 결제 없이 알림 파싱 흐름을 확인하기 위한 개발용 패널.
 *
 * 가짜 Notification
 * → PaymentNotificationListener
 * → TossNotificationParser
 * → NotificationPaymentSource
 */
@Composable
private fun DebugNotificationPanel(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    var lastResult by remember {
        mutableStateOf("아직 수신 없음")
    }

    var hasNotificationAccess by remember {
        mutableStateOf(
            NotificationAccess.isGranted(context)
        )
    }

    // PaymentSource 수신 결과 확인
    LaunchedEffect(Unit) {
        NotificationPaymentSource.payments.collectLatest { payment ->
            lastResult =
                "${payment.kind} / " +
                        "${payment.amount}원 / " +
                        (payment.merchant ?: "-")
        }
    }

    // 알림 접근 설정 화면에서 돌아오면 권한 상태 다시 확인
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasNotificationAccess =
                        NotificationAccess.isGranted(context)
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android 13 이상 알림 권한
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // 허용 후 테스트 버튼을 다시 누르면 됨
        }

    fun sendNotification(
        action: () -> Unit,
    ) {
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            action()
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        // 평소에는 이것만 보임
        FloatingActionButton(
            onClick = {
                expanded = !expanded
            },
        ) {
            Text(
                if (expanded) "닫기" else "DEV"
            )
        }

        // DEV를 눌렀을 때만 펼쳐짐
        AnimatedVisibility(
            visible = expanded
        ) {
            Card(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(190.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "DEBUG",
                        style =
                            MaterialTheme.typography.titleSmall,
                    )

                    Text(
                        text = lastResult,
                        style =
                            MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text =
                            if (hasNotificationAccess) {
                                "알림 접근: 허용됨"
                            } else {
                                "알림 접근: 필요"
                            },
                        style =
                            MaterialTheme.typography.bodySmall,
                    )

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("알림 접근 설정")
                    }

                    OutlinedButton(
                        onClick = {
                            sendNotification {
                                DebugTestNotification
                                    .sendPayment(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("가짜 결제")
                    }

                    OutlinedButton(
                        onClick = {
                            sendNotification {
                                DebugTestNotification
                                    .sendCancel(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("가짜 취소")
                    }

                    OutlinedButton(
                        onClick = {
                            sendNotification {
                                DebugTestNotification
                                    .sendFailed(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("가짜 실패")
                    }

                    OutlinedButton(
                        onClick = {
                            sendNotification {
                                DebugTestNotification
                                    .sendPoint(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("가짜 포인트")
                    }
                }
            }
        }
    }
}

/**
 * 탭을 오갈 때 백스택이 쌓이지 않게 하고 각 탭의 상태를 유지한다.
 * 그래프의 시작 화면은 첫 실행 때 온보딩이라, 항상 지도 홈을 기준으로 정리한다.
 */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(Routes.MAP) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
