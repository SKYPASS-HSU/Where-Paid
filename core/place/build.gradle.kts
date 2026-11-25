import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// 루트 build.gradle.kts가 local.properties / 환경변수에서 읽어 둔 값
val secrets: Map<String, String> by rootProject.extra

android {
    namespace = "com.wherepaid.app.core.place"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // 카카오 로컬 API(장소 검색) REST 키. 비어 있어도 빌드는 된다.
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${secrets.getValue("kakao.restApiKey")}\"")
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
