import org.jetbrains.kotlin.gradle.utils.loadPropertyFromResources
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.aivy.navigator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aivy.navigator"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        //manifestPlaceholders["naverClientId"] = localProperties.getProperty("NAVER_CLIENT_ID", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "TMAP_APP_KEY", "\"${localProperties.getProperty("TMAP_APP_KEY", "")}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // jetpack 관련
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3") // Material Design 3
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2") // Compose Activity
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // Compose ViewModel

    // 디버깅/미리보기 용도
    debugImplementation("androidx.compose.ui:ui-tooling")

    // -----------------------------

    // TMap SDK (JAR from libs/)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Naver Maps SDK
    implementation("com.naver.maps:map-sdk:3.23.2")

    // Google Play Services - 위치 서비스
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Material Design Components
    implementation(libs.material)

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // Retrofit - 네트워크 통신
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // OkHttp (TMap API 호출용)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // OkHttp Logging Interceptor (네트워크 디버깅용)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson (JSON 파싱용)
    implementation("com.google.code.gson:gson:2.10.1")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Camera X
    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // Gemini AI Android SDK (Google 공식 SDK)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
