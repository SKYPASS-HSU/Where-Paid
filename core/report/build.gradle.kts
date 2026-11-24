import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// 루트 build.gradle.kts가 local.properties / 환경변수에서 읽어 둔 값
val secrets: Map<String, String> by rootProject.extra

android {
    namespace = "com.wherepaid.app.core.report"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // LLM 중계 서버(server/llm-relay) 주소와 앱 토큰. 비어 있으면 앱은 가짜 리포트를 쓴다.
        buildConfigField("String", "RELAY_URL", "\"${secrets.getValue("wherepaid.relayUrl")}\"")
        buildConfigField("String", "RELAY_TOKEN", "\"${secrets.getValue("wherepaid.relayToken")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 단위 테스트(JVM)에서는 Android의 org.json이 비어 있어 실제 구현을 넣는다
    testImplementation(libs.json)
}
