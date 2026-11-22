# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

"어디서돈썼지도"는 토스 결제 알림을 받은 순간의 GPS 위치로 결제 장소를 지도에 남기는 안드로이드 앱이다 (팀 4명, 시연용 직접 설치, Play 스토어 출시 안 함).

기준 문서 (충돌하면 위가 우선):
1. 팀 요구사항 분석서 (기능 FR-01~05, 결제 상태 이동표 2-3, 오프라인 동작 4-2). 저장소 밖 문서라 핵심 규칙은 아래와 `core/model`의 KDoc에 옮겨 둔다
2. 피그마 화면 시안: https://www.figma.com/design/OiqHzesyDbKJQ5ne4CpgRx (섹션 ⑩ "개발 참고"에 지도 줌·지역 색칠 규칙, "보관" 섹션은 구현하지 않음)
3. 검증 앱 인계 문서 (알림 형식, 위치 측정 근거): https://github.com/LSU1101/MobileProgramming/blob/main/docs/HANDOFF.md

PR 리뷰어(Claude Review 워크플로)도 이 파일을 기준으로 리뷰한다. 아래 "팀 규칙"을 어긴 변경은 지적한다.

## 명령

```bash
./gradlew build                      # 전 모듈 컴파일 + 단위 테스트 + lint (PR 전에 통과시킨다)
./gradlew testDebugUnitTest :core:model:test   # 단위 테스트만 (:core:model은 순수 Kotlin이라 태스크가 test)
./gradlew :core:model:test --tests "com.wherepaid.app.core.model.fake.FakesTest"   # 테스트 클래스 하나
./gradlew :core:data:testDebugUnitTest --tests "*.SomeTest.someMethod"            # Android 모듈의 테스트 하나
./gradlew :app:lintDebug             # lint만
./gradlew :core:data:connectedDebugAndroidTest   # Room 저장소 테스트 (에뮬레이터/기기 필요)
./gradlew installDebug               # 연결된 기기/에뮬레이터에 설치
adb shell am start -n com.wherepaid.app/.MainActivity
```

Android SDK 위치는 `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`로 잡는다. 클라우드 세션에서는 `dl.google.com`, `maven.google.com`이 허용돼야 빌드된다.

## 구조

```
:app                 Application, AppContainer(수동 DI), 하단 탭 네비게이션
:core:model          순수 Kotlin. 모델 + 인터페이스 4개(Contracts.kt) + 가짜 구현(fake/)
:core:designsystem   피그마 시안의 색·글꼴(Pretendard)·모양 토큰, 공통 컴포넌트, 금액·날짜 표기
:core:notification   ① PaymentSource 구현 자리 (알림 수집·파싱)
:core:location       ② LocationProvider 구현 자리 (GPS 1회 측정)
:core:place          ② PlaceResolver 구현 자리 (카카오 로컬 API)
:core:data           ④ Room + TransactionRepository 구현
:feature:onboarding  ③ S-01 온보딩 (첫 실행)
:feature:map         ③ 탭 1 지도: S-02 지도 홈, S-03 핀 상세, D-01, S-04 후보 고르기, S-07 온라인 결제
:feature:placecheck  ③ 탭 2 장소 확인: S-05, S-06 매장 찾기, D-02
:feature:report      ③ 탭 3 리포트: S-08 월간 리포트, S-09 공유 카드
```

데이터 흐름 (목표. 파이프라인은 아직 없다):
토스 알림 → 필터(진행 중·묶음 제외) → 파싱 → 결제면 GPS 1회 측정 후 Room에 저장 / 취소면 원래 결제에 취소 줄 추가 → 판정 순서표(분석서 FR-02: 사용자 지정 → PG사 리스트 → 위치 신뢰 → 학습기록 → 별칭사전 + 반경 300 m 검색)로 장소 상태 결정 → 화면

- 모듈 사이의 약속은 `core/model`의 `Contracts.kt`(인터페이스)와 `Models.kt`(모델)가 전부다. 화면은 이 인터페이스만 받고, 어떤 구현을 넣을지는 `app`의 `AppContainer` 한 곳에서 정한다. 구현이 없는 인터페이스에는 `core/model`의 `fake/` 구현이 들어가 있다.
- 결제는 `PlaceStatus` 다섯 가지 중 하나다: `MAPPED`(지도 핀) / `AMBIGUOUS`(느낌표 핀, 후보 2곳+) / `UNMATCHED`(매칭실패함) / `PENDING_SEARCH`(매칭 대기) / `ONLINE`(온라인 목록). 사용자 동작에 따른 이동은 `PlaceTransitions`와 저장소의 `choosePlace`·`markNotHere`·`markOnline`·`markOffline`·`delete`가 분석서 2-3 표대로 처리한다. 학습기록·사용자 지정은 가맹점명(`merchantRaw`) 단위다.
- 취소는 별도 거래가 아니라 원래 결제의 `cancellations`에 붙고, 금액은 항상 `netAmount`(취소 반영)를 쓴다. 대상 결제 고르는 규칙은 `CancellationRules`.
- 저장소 구현(가짜, Room)과 화면이 같이 쓰는 규칙은 `core/model/Rules.kt`의 순수 함수에 둔다 (월 범위는 한국 시간, 월 합계 `MonthSummary`). 규칙을 한쪽 구현에만 쓰지 않는다.
- `local.properties`의 `wherepaid.useFakeData`(기본 `true`)가 `FakeTransactionRepository`(메모리 샘플)와 Room 구현을 고른다. CI 빌드는 `false`다.
- **저장은 확정**: 로그인·앱 서버 없음. 기기 DB(Room)가 유일한 저장소이고 Android 자동 백업(`allowBackup`)으로 보존한다. 앱 밖으로 나가는 것은 분석서 NFR-03의 네 가지(카카오 로컬 API에 가맹점명·좌표, 카카오맵에 화면 좌표, LLM 중계 서버에 월별 집계, 본인 Google 백업)뿐이다. Firebase·원격 DB 의존성을 추가하지 않는다.
- 비밀 값(서명 키, 카카오 키)은 루트 `build.gradle.kts`가 `local.properties` → 환경변수 순으로 읽어 `rootProject.extra["secrets"]`에 둔다. 새 키가 필요하면 그 맵과 `local.properties.example`, `.github/workflows/android.yml`, README를 같이 고친다. 값이 비어 있어도 빌드는 돼야 한다.
- 화면 번호(S-01 등)는 피그마 시안의 프레임 이름이다. 하단 탭과 경로는 `app`의 `MainActivity.kt`(`Routes`, `TopLevelDestination`)에 있고, 첫 실행 여부는 `OnboardingStore`.
- 화면 작성 규칙: `XxxScreen(state, onAction…)`은 상태만 받아 그리는 함수로 두고 시안의 상태 프레임마다 `@Preview`를 만든다. 저장소 연결과 상태 계산은 같은 모듈의 `XxxRoute` + ViewModel에서 하고, ViewModel은 `AppContainer`의 인터페이스를 생성자로 받는다.
- 지도 탭은 상태 바 밑까지 그린다. 다른 탭 화면은 `statusBarsPadding()`을 받는다 (`Scaffold`의 `contentWindowInsets`가 0).
- 패키지: `com.wherepaid.app`(app), `com.wherepaid.app.core.<모듈>`, `com.wherepaid.app.feature.<모듈>`. 버전은 `gradle/libs.versions.toml`에서만 관리한다.

## 팀 규칙

### 모듈 경계
- 의존 방향: `feature:* → core:model, core:designsystem` / `core:{notification,location,place,data} → core:model` / `app → 전부`. `core:designsystem`은 다른 모듈에 의존하지 않는다.
- feature끼리, core 구현 모듈끼리는 서로 의존하지 않는다. feature가 `core:data` 같은 구현 모듈을 의존하지 않는다.
- `core:model`에는 Android 의존성을 넣지 않는다.
- 화면 코드에서 색(`Color(0x…)`)·글자 크기·모서리를 직접 쓰지 않는다. `WherePaidTheme.colors`, `WherePaidTheme.typography`, `WherePaidShapes`와 `core:designsystem`의 컴포넌트를 쓰고, 없으면 피그마에서 값을 확인해 디자인 시스템에 먼저 추가한다. 금액·날짜 표기는 `format/Format.kt`("61,500원", "6.2만", "10.11 (토) 14:32").
- Room 타입(Entity, Dao, Database)은 `core:data` 안에서 `internal`로 둔다.
- `Contracts.kt`와 `Models.kt`를 바꾸면 모든 담당이 영향을 받는다. 다른 작업에 섞지 말고 그 변경만 담은 PR로 올려 팀 전체 확인을 받는다.
- 담당: ① `core:notification` / ② `core:location`, `core:place` / ③ `feature:*`, `app`의 네비게이션·테마 / ④ `core:data`, `.github/`, 문서. 남의 모듈을 고쳐야 하면 PR 설명에 적는다.

### 개인정보와 데이터
- **알림 원문을 저장하지 않는다.** 알림의 title, text, extras는 파싱에만 쓰고 DB, 파일, 로그(`Log.*`), 원격 어디에도 남기지 않는다. 저장하는 것은 파싱 결과(`Transaction`)뿐이다. 모델과 Entity에 원문 필드를 추가하지 않는다.
- 실제 결제 기록, 팀원의 위치, 실제 알림 원문을 테스트·샘플 데이터·문서로 커밋하지 않는다. 파서 테스트 케이스는 가맹점과 금액을 바꾼 것을 쓴다.
- 서명 키(`*.jks`, `*.keystore`), `local.properties`, `google-services.json`, API 키를 커밋하지 않는다.

### 알림과 위치 (검증으로 확인된 것)
- 대상은 토스(`viva.republica.toss`) 알림뿐이다. `sbn.isOngoing`과 `FLAG_GROUP_SUMMARY` 알림은 무시한다.
- **파싱이 먼저다.** 결제·취소·실패로 판별된 알림에서만 위치를 측정한다 (토스 알림의 95%는 결제가 아니다).
- 위치는 매번 새로 측정한다 (`getCurrentLocation`, 고정확도, `maxUpdateAge = 0`, 제한 10~15초). 캐시 위치(`lastLocation`)는 대체값으로도 쓰지 않는다. 검증에서 최대 2.2km 어긋났다.
- 결제 실패는 결제로 세지 않는다. "잔액 N원" 줄을 결제 금액으로 읽지 않는다.
- 파싱 규칙(정규식, 키워드)은 파일 하나에 모으고, 새 알림 형식을 지원할 때는 테스트 케이스를 같이 추가한다.

### PR
- `main`에 직접 푸시하지 않는다. 브랜치 → PR → Claude 리뷰 확인 + 팀원 1명 승인 → 병합.
- 병합은 **Create a merge commit**만 쓴다 (Squash·Rebase는 저장소 설정에서 꺼 두었다). 쌓인 PR의 커밋이 그대로 이어지게 하기 위해서다.
- `main` 보호 규칙: 승인 1명, 필수 검사 `build`(Android 워크플로), 브랜치 최신 상태. 새 커밋이 올라오면 받은 승인은 풀린다. **bypass rules로 병합하지 않는다.**
- 병합 순서: [Update branch] → `build` 통과 확인 → 승인 → 병합. 업데이트도 새 커밋이라 승인을 먼저 받으면 다시 받아야 한다.
- 다른 PR 위에 쌓은 PR은 설명 첫 줄에 의존 PR을 적는다. 앞 PR이 병합되면 base를 `main`으로 바꾼 뒤 위 순서로 병합한다. 앞 PR부터 차례로 병합한다.
- 승인: 자기 PR은 승인할 수 없다. `Contracts.kt`·`Models.kt`를 바꾸는 PR은 영향을 받는 담당 전원이 확인한 뒤 병합한다.
- PR을 올리기 전에 `./gradlew build`를 통과시킨다.
- PR 하나에는 한 가지 일만 담고, 화면을 바꿨으면 스크린샷을 붙인다.
- Room 구조를 바꾸면 `core/data/schemas/`의 JSON을 같이 커밋한다. 첫 릴리스(`v*` 태그) 전에는 version 1을 그대로 고치고, 그 뒤로는 `version`을 올리고 마이그레이션을 추가한다.
- 저장소 동작을 바꾸면 가짜 구현(`FakeTransactionRepository`)과 Room 구현을 함께 고치고, 양쪽 테스트(`core/model` 단위 테스트, `core/data` androidTest)를 맞춘다.
