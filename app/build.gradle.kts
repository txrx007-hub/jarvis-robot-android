plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.jarvis.robot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.robot"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "2.37"
        buildConfigField("boolean", "DEMO", "false")
        buildConfigField("boolean", "UI_V2", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // The demo ("Jarvis Demo Mel"): a pretend robot and backend inside the app, for showing it to people who have no robot. Installs next to the real app.
        // .uild.ps1 assembleDemo  ->  app/build/outputs/apk/demo/app-demo.apk
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "DEMO", "true")
            matchingFallbacks += listOf("debug")
        }
        // Jarvis v2: the new layout (tabs, tiles, sheets; V2App.kt) on the same robot and backend code. Installs next to the classic app, so both can be compared.
        // .\build.ps1 assembleV2  ->  app/build/outputs/apk/v2/app-v2.apk (copied as Jarvis-v2.apk)
        create("v2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".v2"
            versionNameSuffix = "-v2"
            buildConfigField("boolean", "UI_V2", "true")
            matchingFallbacks += listOf("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")   // pull down to reload the backend page (a WebView does not take part in Compose's pull-to-refresh)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
