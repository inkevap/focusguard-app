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
    }
}

rootProject.name = "FocusGuard"

// Módulos Kotlin puros: se compilan y prueban sin Android SDK.
include(":core-model")
include(":core-decision")
project(":core-model").projectDir = file("app/core-model")
project(":core-decision").projectDir = file("app/core-decision")

// -PsoloJvm omite los módulos Android (útil en CI o entornos sin SDK).
if (!providers.gradleProperty("soloJvm").isPresent) {
    include(":app")
}
