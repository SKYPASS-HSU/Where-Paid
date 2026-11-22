# 어디서돈썼지도

토스 결제 알림을 받은 순간의 위치로 "어디서 돈을 썼는지"를 지도에 남기는 안드로이드 앱입니다.

- 안드로이드 전용, 시연용 직접 설치 (Play 스토어 출시 안 함)
- 결정 사항과 검증 결과: [검증 앱 인계 문서](https://github.com/LSU1101/MobileProgramming/blob/main/docs/HANDOFF.md)
- 팀 규칙(모듈 경계, PR, 알림 원문 저장 금지): [CLAUDE.md](CLAUDE.md)

> 지금은 **뼈대**만 있습니다. 모듈 구조, 모듈 간 인터페이스, 가짜 구현, 빈 화면 3개(지도 / 내역 / 설정)까지이고 알림 수집, 위치 측정, 장소 검색, 지도는 아직 없습니다.

---

## 1. 처음 설치 (5분)

### 준비물
- **Android Studio** 최신 안정 버전 (JDK가 들어 있습니다)
- Android SDK Platform **36** (Android Studio가 처음 열 때 설치를 안내합니다)
- 커맨드라인만 쓸 경우: JDK 17 이상 + Android SDK

### Android Studio로
1. 저장소를 클론합니다.
   ```bash
   git clone https://github.com/SKYPASS-HSU/Where-Paid.git
   ```
2. Android Studio → **Open** → 클론한 폴더를 선택하고 Gradle Sync가 끝날 때까지 기다립니다.
3. 상단에서 에뮬레이터(없으면 Device Manager에서 Pixel 계열, API 36으로 하나 만듭니다)를 고르고 ▶ **Run**을 누릅니다.
4. 첫 화면 온보딩에서 **시작하기**를 누른 뒤, 하단 탭 **지도 / 장소 확인 / 리포트**가 보이면 성공입니다. (화면은 아직 자리표시입니다)

`local.properties`는 Android Studio가 SDK 경로(`sdk.dir`)만 넣어서 자동으로 만듭니다. **여기까지는 다른 설정이 필요 없습니다.**

### 커맨드라인으로
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
```
```bash
./gradlew build
```
```bash
./gradlew installDebug
```
```bash
adb shell am start -n com.wherepaid.app/.MainActivity
```
- Windows는 `gradlew.bat`, SDK 경로는 보통 `%LOCALAPPDATA%\Android\Sdk`입니다.
- `ANDROID_HOME` 대신 `local.properties`에 `sdk.dir=/경로/Android/sdk`를 적어도 됩니다.
- `installDebug`는 에뮬레이터가 켜져 있거나 폰이 USB 디버깅으로 연결돼 있어야 합니다.

---

## 2. local.properties 설정

`local.properties`는 **내 PC에만 두는 설정 파일**이고 커밋되지 않습니다(`.gitignore`). 예시 파일을 복사해서 시작합니다.

```bash
cp local.properties.example local.properties
```
Android Studio가 이미 만들어 둔 `local.properties`가 있다면 덮어쓰지 말고, 예시 파일의 내용을 `sdk.dir` 줄 아래에 붙여 넣으세요.

| 키 | 용도 | 비어 있으면 |
|---|---|---|
| `sdk.dir` | Android SDK 위치 | Android Studio가 자동으로 채움 (또는 `ANDROID_HOME`) |
| `signing.storeFile`<br>`signing.storePassword`<br>`signing.keyAlias`<br>`signing.keyPassword` | 팀 공용 서명 키 | 내 PC의 기본 debug 키로 서명 (빌드할 때 경고가 나옵니다) |
| `kakao.nativeAppKey` | 카카오맵 SDK | 빌드는 됨. 지도 기능을 만들 때 필요 |
| `kakao.restApiKey` | 카카오 로컬 API(장소 검색) | 빌드는 됨. 장소 검색을 만들 때 필요 |
| `wherepaid.useFakeData` | `true`: 가짜 거래, `false`: 기기 DB(Room) | `true` |

**누가 무엇을 채워야 하나요?**
- 화면만 만드는 동안: 아무것도 안 채워도 됩니다.
- 카카오맵, 장소 검색을 다루거나 **실기기에 설치해 팀원과 같은 앱을 쓸 때**: 서명 키와 카카오 키를 채웁니다.

### 팀 공용 서명 키

카카오맵은 앱 서명 키의 키 해시에 묶여 있습니다. 또 서명이 다른 APK는 덮어 설치할 수 없어서 앱을 지워야 하고, 그러면 데이터가 사라집니다. 그래서 **팀 전원과 CI가 같은 키 하나로 서명**합니다.

**팀원 (키 받기)**
1. 팀 채널에서 `team.jks` 파일과 비밀번호, 별칭을 받습니다.
2. 저장소 루트에 `keystore` 폴더를 만들고 `keystore/team.jks`로 둡니다. (이 폴더는 커밋되지 않습니다)
3. `local.properties`의 `signing.*` 네 줄을 채웁니다.
4. 빌드했을 때 "팀 서명 키가 설정되지 않아…" 경고가 사라지면 된 것입니다.

**저장소 관리자 (키 만들기, 최초 1회)**
```bash
mkdir -p keystore && keytool -genkeypair -v -keystore keystore/team.jks -alias wherepaid -keyalg RSA -keysize 2048 -validity 10000
```
- 비밀번호를 물으면 팀에서 쓸 값을 정합니다. 키 파일과 비밀번호는 팀 비공개 채널로만 공유합니다. **저장소, 이슈, PR에 올리지 않습니다.**
- 키를 잃어버리면 모든 팀원이 앱을 지우고 다시 설치해야 하니 백업해 두세요.

카카오 개발자 콘솔에 등록할 **키 해시**는 이렇게 뽑습니다. 패키지명은 `com.wherepaid.app`입니다.
```bash
keytool -exportcert -alias wherepaid -keystore keystore/team.jks | openssl sha1 -binary | openssl base64
```

### 카카오 키
카카오 개발자 콘솔의 팀 공용 앱 1개를 같이 씁니다. **앱 키** 메뉴의 "네이티브 앱 키"와 "REST API 키"를 `local.properties`에 넣습니다. 키는 팀 채널에서 받으세요.

---

## 3. 구조

```
app/                 앱 진입점, AppContainer(구현 선택), 하단 탭 네비게이션
core/model/          ★ 모듈 간 약속: 모델, 인터페이스 4개, 가짜 구현
core/notification/   ① 알림 수집·파싱        → PaymentSource
core/location/       ② GPS 1회 측정          → LocationProvider
core/place/          ② 카카오 장소 검색       → PlaceResolver
core/data/           ④ 저장 (Room)           → TransactionRepository
core/designsystem/   ③ 색·글꼴·공통 컴포넌트 (피그마 시안)
feature/onboarding/  ③ 첫 실행 온보딩
feature/map/         ③ 탭 1: 지도 홈, 핀 상세, 온라인 결제
feature/placecheck/  ③ 탭 2: 장소 확인, 매장 찾기
feature/report/      ③ 탭 3: 월간 리포트, 공유 카드
```

| 역할 | 맡은 곳 |
|---|---|
| ① 알림 수집·파싱 | `core/notification` |
| ② 위치·장소 매칭 | `core/location`, `core/place` |
| ③ 화면·디자인 | `feature/*`, `app`의 네비게이션과 테마 |
| ④ 설계·데이터·배포 | `core/data`, `.github/`, 문서 |

### 모듈 간 약속
모든 모듈은 [`core/model`](core/model/src/main/kotlin/com/wherepaid/app/core/model)만 바라봅니다.

```kotlin
interface PaymentSource         { val payments: Flow<ParsedPayment> }                                // ①
interface LocationProvider      { suspend fun capture(): LocationResult }                              // ②
interface PlaceResolver         { suspend fun resolve(merchant: String, near: LatLng?): List<Place>    // ② 자동 매칭
                                  suspend fun search(query: String, near: LatLng?): List<Place> }     // ② 매장 찾기
interface TransactionRepository { /* 월별·장소 확인 조회, 기록, 취소, 상태 이동, 학습기록 */ }           // ④
```

- 결제는 요구사항 분석서의 다섯 가지 장소 상태(`PlaceStatus`) 중 하나입니다: 지도 핀(`MAPPED`), 느낌표 핀(`AMBIGUOUS`), 매칭실패함(`UNMATCHED`), 매칭 대기(`PENDING_SEARCH`), 온라인(`ONLINE`). 취소는 원래 결제에 붙습니다.
- 화면(③)은 인터페이스를 매개변수로 받습니다. 실제 구현이 없어도 `core/model`의 `fake/`에 있는 가짜 구현과 `SampleData`(다섯 상태와 취소가 모두 들어 있음)로 화면과 미리보기(`@Preview`)를 만들 수 있습니다.
- ①②④는 자기 모듈에 구현을 만든 뒤 [`AppContainer`](app/src/main/kotlin/com/wherepaid/app/WherePaidApp.kt)에서 가짜 구현을 바꿔 끼웁니다.
- 모델과 인터페이스를 바꿀 때는 그 변경만 담은 PR로 올려 팀 전체 확인을 받습니다.

### 저장 방식
로그인과 앱 서버 없이 **기기 DB(Room)에만 저장**하고, Android 자동 백업으로 본인 Google 계정에 보존합니다 (요구사항 분석서 1-4, NFR-02). 서버는 월간 리포트의 LLM 호출을 중계하는 것 하나뿐입니다.

가짜 데이터 대신 Room으로 실행해 보려면 `local.properties`에 아래 줄을 넣고 다시 빌드합니다. (아직 결제를 저장하는 기능이 없어서 0건으로 나옵니다)
```properties
wherepaid.useFakeData=false
```

---

## 4. 작업 방식

1. `main`에서 브랜치를 만듭니다. `main`에는 직접 푸시하지 않습니다.
2. 푸시하기 전에 `./gradlew build`를 통과시킵니다.
3. PR을 올리면 **Android**(테스트 + APK 빌드)와 **Claude Review**(자동 리뷰)가 돕니다.
4. Claude 리뷰를 확인하고 팀원 1명의 승인을 받은 뒤 병합합니다.

병합할 때:
- 병합 버튼은 **Create a merge commit**만 켜져 있습니다.
- 순서는 **[Update branch] → `build` 통과 → 승인 → 병합**입니다. 새 커밋(업데이트 포함)이 올라오면 받은 승인이 풀립니다.
- **"Merge without waiting for requirements (bypass rules)"는 체크하지 않습니다.**
- 다른 PR 위에 쌓은 PR은 앞 PR부터 병합하고, 앞 PR이 병합되면 PR 제목 옆 ✏ → base를 `main`으로 바꾼 뒤 병합합니다.
- 모델·인터페이스(`Contracts.kt`, `Models.kt`)를 바꾸는 PR은 담당 전원이 확인한 뒤 병합합니다.

자주 쓰는 명령은 [CLAUDE.md](CLAUDE.md)에 있습니다.

### APK 배포
APK는 GitHub Actions가 빌드합니다. 아래 둘 중 하나를 하면 몇 분 뒤 [Releases](https://github.com/SKYPASS-HSU/Where-Paid/releases)에 올라갑니다.
- 저장소 → **Actions** → **Android** → **Run workflow**
- 태그 푸시: `git tag v0.2.0` 후 `git push origin v0.2.0`

시연 기기에는 `adb install -r app-debug.apk`로 설치하세요. 브라우저나 메신저로 받은 APK는 Play 프로텍트와 Android 13 이상의 "제한된 설정"에 막힙니다(인계 문서 §4).

---

## 5. 저장소 관리자가 한 번 해 둘 일

**Settings → Secrets and variables → Actions**에 등록합니다. 없어도 CI는 통과하지만, 그때는 APK가 CI의 임시 debug 키로 서명돼 팀원 앱 위에 덮어 설치할 수 없습니다.

| Secret | 값 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -i keystore/team.jks`의 출력 (Linux는 `base64 -w0 keystore/team.jks`) |
| `SIGNING_STORE_PASSWORD` | 키 저장소 비밀번호 |
| `SIGNING_KEY_ALIAS` | 별칭 (위 명령대로 만들었다면 `wherepaid`) |
| `SIGNING_KEY_PASSWORD` | 키 비밀번호 |
| `KAKAO_NATIVE_APP_KEY` | 카카오 네이티브 앱 키 |
| `KAKAO_REST_API_KEY` | 카카오 REST API 키 |
| `CLAUDE_CODE_OAUTH_TOKEN` | `claude setup-token`으로 발급한 토큰 (Claude Review용) |

그 밖에:
- [Claude GitHub App](https://github.com/apps/claude)을 이 저장소에 설치합니다 (Claude Review가 PR에 코멘트를 남기려면 필요).
- **Settings → Branches**에서 `main` 브랜치 보호를 켭니다: PR 필수, 승인 1명, 새 커밋 시 승인 해제, 필수 검사 `build`(Android 워크플로의 job 이름), 브랜치 최신 상태 필수.
- **Settings → General → Pull Requests**에서 "Allow merge commits"만 켜고 Squash·Rebase는 끕니다.
- 카카오 개발자 콘솔에 패키지명 `com.wherepaid.app`과 팀 키의 키 해시를 등록하고, REST 키는 사용량 제한을 걸어 둡니다 (이 저장소는 공개이고 APK에 키가 들어갑니다).
