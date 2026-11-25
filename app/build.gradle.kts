import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 루트 build.gradle.kts가 local.properties / 환경변수에서 읽어 둔 값
val secrets: Map<String, String> by rootProject.extra

// 팀 공용 서명 키. 카카오맵(키 해시)이 서명 키에 묶이므로
// 로컬과 CI가 모두 같은 키로 서명해야 한다. 키 파일과 비밀번호는 커밋하지 않는다 (README.md 참고).
val teamKeystore = secrets.getValue("signing.storeFile").takeIf { it.isNotEmpty() }?.let { rootProject.file(it) }
val hasTeamKey = teamKeystore?.exists() == true &&
    listOf("signing.storePassword", "signing.keyAlias", "signing.keyPassword").all { secrets.getValue(it).isNotEmpty() }
if (!hasTeamKey) {
    logger.warn(
        "⚠️  팀 서명 키가 설정되지 않아 이 PC의 기본 debug 키로 서명합니다. " +
            "빌드와 실행은 되지만 카카오맵은 동작하지 않고, 팀 키로 서명한 앱 위에 덮어 설치할 수 없습니다. " +
            "(README.md의 'local.properties 설정' 참고)"
    )
}

android {
    namespace = "com.wherepaid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wherepaid.app"
        minSdk = 26
        targetSdk = 36
        // CI에서 빌드 번호로 덮어쓴다 (.github/workflows/android.yml)
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        // 카카오맵 SDK 네이티브 앱 키. SDK 2.9.5부터 매니페스트를 읽지 않아 WherePaidApp에서 KakaoMapSdk.init으로 넣는다.
        // 비어 있어도 빌드는 되고, 지도 자리에 "지도를 불러오지 못했어요"가 나온다.
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${secrets.getValue("kakao.nativeAppKey")}\"")

        // true(기본): 가짜 데이터, false: 기기 DB(Room). local.properties의 wherepaid.useFakeData
        buildConfigField("boolean", "USE_FAKE_DATA", "${secrets.getValue("wherepaid.useFakeData") != "false"}")
    }

    signingConfigs {
        if (hasTeamKey) {
            create("team") {
                storeFile = teamKeystore
                storePassword = secrets.getValue("signing.storePassword")
                keyAlias = secrets.getValue("signing.keyAlias")
                keyPassword = secrets.getValue("signing.keyPassword")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.getByName(if (hasTeamKey) "team" else "debug")
        debug {
            signingConfig = signing
        }
        release {
            // 시연용 직접 설치 앱이라 난독화하지 않는다. 배포는 debug APK로 한다.
            isMinifyEnabled = false
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // :app만 모든 모듈을 알고, AppContainer에서 구현을 골라 화면에 넘긴다.
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:notification"))
    implementation(project(":core:location"))
    implementation(project(":core:place"))
    implementation(project(":core:data"))
    implementation(project(":core:report"))
    implementation(project(":feature:map"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:placecheck"))
    implementation(project(":feature:report"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
