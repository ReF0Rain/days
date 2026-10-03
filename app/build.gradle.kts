plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.countdown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.countdown"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // ---------- 签名配置 ----------
    // 若存在 keystore/countdown.jks（或用环境变量 COUNTDOWN_KEYSTORE 指定），release 用它签名；
    // 否则回落到 debug 签名，保证在没有 keystore 的机器上也能直接构建 release APK。
    val releaseKeystoreFile = file(System.getenv("COUNTDOWN_KEYSTORE") ?: "$rootDir/keystore/countdown.jks")
    signingConfigs {
        if (releaseKeystoreFile.exists()) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("COUNTDOWN_STORE_PASSWORD") ?: "countdown"
                keyAlias = System.getenv("COUNTDOWN_KEY_ALIAS") ?: "countdown"
                keyPassword = System.getenv("COUNTDOWN_KEY_PASSWORD") ?: "countdown"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有 keystore 就用 release 签名，否则回落到 debug 签名；
            // 两者都取不到时设为 null（不显式签名），不会让配置阶段失败。
            signingConfig = runCatching { signingConfigs.getByName("release") }
                .recoverCatching { signingConfigs.getByName("debug") }
                .getOrNull()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // 与 Kotlin 1.9.24 匹配的 Compose 编译器版本
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room 导出 schema，便于版本迁移与测试
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ---------- 基础 ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // ---------- Compose ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ---------- Room ----------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---------- WorkManager ----------
    implementation(libs.androidx.work.runtime.ktx)

    // ---------- DataStore ----------
    implementation(libs.androidx.datastore.preferences)

    // ---------- Glance 桌面小组件 ----------
    implementation(libs.androidx.glance.appwidget)

    // ---------- 测试 ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // ---------- 调试工具 ----------
    debugImplementation(libs.androidx.ui.tooling)
}
