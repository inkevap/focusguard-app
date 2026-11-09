plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-model"))
    testImplementation(kotlin("test"))
    // Solo para probar funciones `suspend` (el módulo no usa corrutinas en su código).
    testImplementation(libs.kotlinx.coroutines.core)
}
