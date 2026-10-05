plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Gradle Kotlin DSL 允许在其他语句之前写 import（必须在 plugins {} 之后）
import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

// ---------------------------------------------------------------------------
// 正式签名凭据解析，优先级：
//   1) 环境变量（GitHub Actions 从 Secrets 注入）
//   2) keystore/keystore.properties（本地开发用，已被 .gitignore 忽略）
//   3) 两者都没有 -> release 回落到 debug 签名
//      （本地/CI 都仍能出包，但签名不适合上架、也不能和正式包互相覆盖安装）
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
// 注意：这里写 java.util.Properties() 会踩到 Kotlin DSL 的坑 ——
// 脚本作用域里有 org.gradle.api.plugins.JavaPluginExtension 暴露的 `java` 属性，
// 它会把 `java` 这个名字遮蔽掉，导致 "Unresolved reference: util"。
// Properties 本身在 Gradle Kotlin DSL 里是默认导入的，直接用即可。
val keystoreProps: Properties = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { stream -> load(stream) }
    }
}

fun signingSecret(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStorePath: String? = signingSecret("COUNTDOWN_KEYSTORE", "storeFile")
val releaseStorePassword: String? = signingSecret("COUNTDOWN_STORE_PASSWORD", "storePassword")
val releaseKeyAlias: String? = signingSecret("COUNTDOWN_KEY_ALIAS", "keyAlias")
val releaseKeyPassword: String? = signingSecret("COUNTDOWN_KEY_PASSWORD", "keyPassword")

val releaseStoreFile: File? = releaseStorePath?.let { path ->
    rootProject.file(path).takeIf { it.exists() }
}

val hasReleaseSigning: Boolean = releaseStoreFile != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// ---------------------------------------------------------------------------
// 版本号：打 tag（例如 v1.2.3）时自动从 tag 推导，否则用默认值 1 / 1.0.0。
//
// 这里必须用 ValueSource 而不是在配置期直接跑 git：
// 配置缓存（org.gradle.configuration-cache=true）不允许在配置阶段启动外部进程，
// 否则会报 "Starting an external process ... during configuration time is unsupported"
// 并让构建直接失败。ValueSource 是 Gradle 官方认可的合规写法。
// ---------------------------------------------------------------------------
abstract class GitTagValueSource : ValueSource<String, GitTagValueSource.Params> {
    interface Params : ValueSourceParameters {
        val projectDir: Property<String>
    }

    override fun obtain(): String = try {
        val dir = File(parameters.projectDir.get())
        if (!File(dir, ".git").exists()) {
            ""
        } else {
            val proc = ProcessBuilder("git", "describe", "--tags", "--exact-match", "HEAD")
                .directory(dir)              // 必须显式指定工作目录，否则 git 找不到仓库
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0) out else ""
        }
    } catch (_: Exception) {
        ""
    }
}

val currentTag: String = providers.of(GitTagValueSource::class) {
    parameters.projectDir.set(rootProject.projectDir.absolutePath)
}.get().orEmpty()
val tagVersionName: String? = currentTag.removePrefix("v").takeIf { v ->
    Regex("""^\d+\.\d+\.\d+$""").matches(v)
}
val tagVersionCode: Int? = tagVersionName?.split(".")?.let { parts ->
    if (parts.size != 3) return@let null
    val major = parts[0].toIntOrNull() ?: return@let null
    val minor = parts[1].toIntOrNull() ?: return@let null
    val patch = parts[2].toIntOrNull() ?: return@let null
    // 每段最多两位，避免超出 Google Play 的 versionCode 上限
    if (major > 2100 || minor > 99 || patch > 99) null
    else major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.example.countdown"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.countdown"
        minSdk = 26
        targetSdk = 34
        versionCode = tagVersionCode ?: 1
        versionName = tagVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // 三种签名方案都开：v2 是 Android 7+ 的必须项，
                // v3 支持密钥轮换，v1 兼容极老设备
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
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

            // 发版时可用 -PversionNameSuffix=-rc1 之类的覆盖版本名后缀
            (project.findProperty("versionNameSuffix") as String?)
                ?.takeIf { it.isNotBlank() }
                ?.let { versionNameSuffix = it }

            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // 取不到正式凭据时回落到 debug 签名，保证构建仍然成功
                runCatching { signingConfigs.getByName("debug") }.getOrNull()
            }
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

/**
 * 把实际生效的签名方式打在构建日志里，避免"以为用正式签名了其实还是 debug"。
 */
tasks.register("printSigningInfo") {
    val signed = hasReleaseSigning
    val storeName = releaseStoreFile?.name ?: "（未找到）"
    val alias = releaseKeyAlias ?: "（未配置）"
    val tag = currentTag ?: "（当前提交没有 tag）"
    val code = tagVersionCode ?: 1
    val name = tagVersionName ?: "1.0.0"
    doLast {
        if (signed) {
            println("[signing] release 使用正式 keystore：$storeName, alias=$alias")
        } else {
            println("[signing] 未检测到正式签名凭据，release 会使用 debug 签名")
            println("[signing] 配置方式见 README「Release 正式签名」一节")
        }
        println("[version] tag=$tag -> versionName=$name, versionCode=$code")
    }
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
