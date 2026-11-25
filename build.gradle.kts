import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// ---------------------------------------------------------------------------
// 비밀 값(서명 키, API 키) 읽기
//
// local.properties(커밋 금지) → 환경변수(CI의 GitHub Secrets) 순서로 찾는다.
// 값이 없어도 빌드는 된다. 설정법은 README.md, 예시는 local.properties.example.
// 모듈에서는 `val secrets: Map<String, String> by rootProject.extra`로 꺼내 쓴다.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(propertyKey: String, envKey: String): String =
    localProperties.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""

val secrets: Map<String, String> by extra(
    mapOf(
        "signing.storeFile" to secret("signing.storeFile", "SIGNING_STORE_FILE"),
        "signing.storePassword" to secret("signing.storePassword", "SIGNING_STORE_PASSWORD"),
        "signing.keyAlias" to secret("signing.keyAlias", "SIGNING_KEY_ALIAS"),
        "signing.keyPassword" to secret("signing.keyPassword", "SIGNING_KEY_PASSWORD"),
        "kakao.nativeAppKey" to secret("kakao.nativeAppKey", "KAKAO_NATIVE_APP_KEY"),
        "kakao.restApiKey" to secret("kakao.restApiKey", "KAKAO_REST_API_KEY"),
        "wherepaid.useFakeData" to secret("wherepaid.useFakeData", "WHEREPAID_USE_FAKE_DATA"),
        "wherepaid.relayUrl" to secret("wherepaid.relayUrl", "WHEREPAID_RELAY_URL"),
        "wherepaid.relayToken" to secret("wherepaid.relayToken", "WHEREPAID_RELAY_TOKEN"),
    )
)
