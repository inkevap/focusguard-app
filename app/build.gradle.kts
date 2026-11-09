plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
    // Solo se aplica si existe `app/google-services.json` (ver más abajo): sin él la app compila igual, sin push.
    alias(libs.plugins.google.services) apply false
}

// El fichero de configuración de Firebase no se sube al repositorio. Sin él no hay push y el teléfono se entera al consultar el estado.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.focusguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "0.7.1"
        // Dirección del servidor propio por defecto: `./gradlew assembleRelease -PserverUrl=https://focusguard.midominio.com`.
        // Vacía = la pone el usuario en la app. No es un secreto.
        buildConfigField("String", "SERVER_URL", "\"${providers.gradleProperty("serverUrl").getOrElse("")}\"")
    }

    // Firma de la versión de prueba: el almacén de claves vive fuera del repositorio (ver docs/DESPLIEGUE.md). Sin las variables, el APK de
    // release sale sin firmar y Android no lo instala.
    val keystoreFile = System.getenv("FG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("FG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FG_KEY_ALIAS") ?: "focusguard"
                keyPassword = System.getenv("FG_KEY_PASSWORD") ?: System.getenv("FG_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].assets.srcDir("$rootDir/data/seed")
    // Las plantillas personales (`app/personal/plantillas.json`) las ignora git: solo entran en el APK de quien las tiene. Sin ellas se compila igual.
    sourceSets["main"].assets.srcDir("$projectDir/personal")
    // Los modelos de imágenes (`app/modelos/`) tampoco van a git: cada uno tiene su licencia y esa decisión es del autor. Sin ellos se compila igual.
    sourceSets["main"].assets.srcDir("$projectDir/modelos")
    // Un modelo comprimido no se puede abrir directamente desde el archivo: sin comprimir se mapea en memoria sin copiarlo.
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-decision"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    // Detección de imágenes en pantalla (experimental, solo registra): ejecuta el modelo en el teléfono.
    implementation(libs.tensorflow.lite)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    // org.json de Android es un stub en las pruebas locales; esta es la implementación real.
    testImplementation(libs.org.json)
}

// La prueba de la paleta lee estos dos ficheros como texto: sin declararlos, Gradle daría la prueba por buena aunque cambiara uno.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("data/theme/tokens.json"))
    inputs.file("src/main/java/com/focusguard/ui/theme/Theme.kt")
    inputs.file(rootProject.file("data/seed/plantillas_genericas.json"))
    inputs.file(rootProject.file(".gitignore"))
}
