package com.wherepaid.app

import android.app.Application
import android.content.Context
import com.wherepaid.app.core.data.createTransactionRepository
import com.wherepaid.app.core.report.createReportWriter
import com.wherepaid.app.core.model.LocationProvider
import com.wherepaid.app.core.model.PaymentSource
import com.wherepaid.app.core.model.PlaceResolver
import com.wherepaid.app.core.model.ReportWriter
import com.wherepaid.app.core.model.TransactionRepository
import com.wherepaid.app.core.model.fake.FakeLocationProvider
import com.wherepaid.app.core.model.fake.FakePaymentSource
import com.wherepaid.app.core.model.fake.FakePlaceResolver
import com.wherepaid.app.core.model.fake.FakeReportWriter
import com.wherepaid.app.core.model.fake.FakeTransactionRepository
import com.wherepaid.app.feature.map.home.initKakaoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

class WherePaidApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 카카오맵 SDK는 Application에서 한 번 초기화한다 (공식 문서 권장). 키가 없으면 지도 자리에 안내가 나온다.
        initKakaoMap(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as WherePaidApp).container

/**
 * 수동 의존성 주입. 인터페이스마다 어떤 구현을 쓸지 여기 한 곳에서 정한다.
 * 실제 구현이 생기면 해당 줄의 가짜 구현을 바꿔 끼운다.
 */
class AppContainer(context: Context) {
    /** local.properties의 wherepaid.useFakeData (기본 true). */
    val usesFakeData: Boolean = BuildConfig.USE_FAKE_DATA

    val transactionRepository: TransactionRepository by lazy {
        if (usesFakeData) FakeTransactionRepository() else createTransactionRepository(context)
    }

    /** 첫 실행 온보딩을 끝냈는지. 기기에만 저장하는 작은 설정값이라 SharedPreferences로 둔다. */
    val onboardingStore = OnboardingStore(context)

    /** 인터넷 연결 여부 (오프라인 안내, 매칭 대기 재검색). */
    val networkMonitor = NetworkMonitor(context)

    // --- 지도 홈 배너 입력값 (분석서 NFR-04, UC-01 5a, FR-04). 아직 만들 사람이 없어 가짜 값이다. ---

    // TODO(①): 알림 접근이 켜져 있는지. false면 "자동 기록이 꺼져 있어요" 배너.
    val autoRecordEnabled: Flow<Boolean> = MutableStateFlow(true)

    // TODO(②): 위치 권한이 "항상 허용"인지. false면 "위치를 항상 허용해 주세요" 배너.
    val locationAlwaysAllowed: Flow<Boolean> = MutableStateFlow(true)

    // TODO(④): 새로 도착한 월간 리포트의 달. 있으면 "○월 리포트가 도착했어요" 배너.
    val arrivedReport: Flow<YearMonth?> = MutableStateFlow(null)

    // TODO(①): :core:notification 구현으로 교체
    val paymentSource: PaymentSource by lazy { FakePaymentSource() }

    // TODO(②): :core:location 구현으로 교체
    val locationProvider: LocationProvider by lazy { FakeLocationProvider() }

    // TODO(②): :core:place 구현으로 교체
    val placeResolver: PlaceResolver by lazy { FakePlaceResolver() }

    /** local.properties의 wherepaid.relayUrl이 있으면 LLM 중계 서버, 없으면 가짜 칭호·요약. */
    val reportWriter: ReportWriter by lazy { createReportWriter() ?: FakeReportWriter() }

    // TODO(④): paymentSource → locationProvider → transactionRepository → placeResolver를 잇는
    //  파이프라인은 아직 없다 (인계 문서 §6 데이터 흐름).
}
