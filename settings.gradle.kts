pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오맵 SDK (com.kakao.maps.open:android). 카카오 패키지만 여기서 찾는다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content { includeGroupByRegex("com\\.kakao\\..*") }
        }
    }
}

rootProject.name = "WherePaid"

include(":app")

// 모듈 간 약속: 모델, 인터페이스, 가짜 구현
include(":core:model")

// 디자인 시스템: 색·글꼴·공통 컴포넌트
include(":core:designsystem")

// 구현 모듈 (담당은 CLAUDE.md 참고)
include(":core:notification")
include(":core:location")
include(":core:place")
include(":core:data")

// 화면
include(":feature:map")
include(":feature:onboarding")
include(":feature:placecheck")
include(":feature:report")
