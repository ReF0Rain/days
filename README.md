# 倒计日 · Countdown

Kotlin + Jetpack Compose 实现的倒计日 App：记录重要日期，实时显示剩余天数。

- 包名 / applicationId：`com.example.countdown`
- minSdk 26（Android 8.0）/ targetSdk 34 / compileSdk 34
- 技术栈：Compose + Material3 + Room(KSP) + ViewModel + WorkManager + Glance 小组件

## 功能

| 功能 | 实现位置 |
| --- | --- |
| 添加 / 编辑 / 删除事件 | `ui/screens/AddEditScreen.kt`、`data/CountdownDao.kt` |
| 选择日期（Material3 DatePicker） | `AddEditScreen.kt` 中的 `DatePickerDialog` |
| 显示剩余天数（今天=0，未来为正，过去为负） | `data/CountdownCalculator.kt`、`ui/components/EventCard.kt` |
| 按剩余天数排序（可切换创建时间） | `ui/CountdownViewModels.kt` |
| Room 本地持久化（日期存 epochDay，避免时区偏移） | `data/CountdownEvent.kt`、`CountdownDatabase.kt` |
| Android 13 通知权限 | `MainActivity.kt`、`notification/CountdownNotifications.kt` |
| 每日更新通知（每天 09:00 对齐，WorkManager 周期任务） | `notification/DailyUpdateScheduler.kt`、`DailyUpdateWorker.kt` |
| 可选桌面小组件（Glance） | `widget/CountdownWidget.kt`、`widget/CountdownWidgetReceiver.kt` |

## 目录结构

```
CountdownApp/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml            # 版本目录（统一管理依赖）
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/countdown/...
│       │   └── res/{values,values-night,drawable,layout,mipmap-*,xml}
│       └── test/java/com/example/countdown/data/CountdownCalculatorTest.kt
└── tools/make_icons.py               # 生成 mipmap PNG 图标（可选）
```

## 云端构建（推荐，无需安装 Android Studio）

推到 GitHub 后由 Actions 自动出 APK，本机什么都不用装（连 Android SDK 和 JDK 都不用）。

```powershell
# 1) 在 GitHub 上先建一个空仓库（不要勾选 README/.gitignore）
# 2) 一键提交 + 推送（脚本会顺带校验关键文件是否存在）
.\scripts\push-to-github.ps1 -RemoteUrl https://github.com/<你的用户名>/countdown.git
```

推送后：

1. 打开仓库 → **Actions** → 等 `Android CI` 这次运行变绿（首次约 3~5 分钟）
2. 进入这次运行页面，拉到底部 **Artifacts**，下载 `countdown-debug-apk.zip`
3. 解压得到 `app-debug.apk`，传到手机安装（需允许「安装未知来源应用」）

流水线文件：[.github/workflows/android-ci.yml](.github/workflows/android-ci.yml)，做了这些事：

| 步骤 | 作用 |
| --- | --- |
| `actions/checkout@v4` | 拉代码 |
| `actions/setup-java@v4` (temurin 17) | 装 JDK 17 |
| `gradle/actions/setup-gradle@v3` | 装 Gradle 并开启依赖缓存 |
| `gradle wrapper --gradle-version 8.6` | **生成 Wrapper**，所以不必提交 `gradle-wrapper.jar` |
| `./gradlew :app:testDebugUnitTest` | 跑单元测试，失败即中断 |
| `./gradlew :app:assembleDebug` / `assembleRelease` | 出两个 APK |
| `actions/upload-artifact@v4` | 上传 `countdown-debug-apk` / `countdown-release-apk` |
| `if: failure()` 上传报告 | 失败时上传 build/reports 方便排查 |

也可以不推送就手动跑：Actions → Android CI → **Run workflow**，`build_release` 勾掉就只出 debug。

> Artifacts 默认保留 30 天；如需长期保存，可在 CI 里加一步把 APK 发布到 Release（需要 `contents: write` 权限）。

## 本地构建（可选）

需要 JDK 17（AGP 8.4 支持 17~21，请勿使用 JDK 22+）与 Android SDK（API 34 + Build-Tools）。

**第 0 步（必做一次）：生成 Gradle Wrapper。** 仓库里只带了 `gradle/wrapper/gradle-wrapper.properties`（已锁定 Gradle 8.6），
`gradlew` / `gradlew.bat` / `gradle-wrapper.jar` 这三个二进制文件需要由本机 Gradle 生成一次；
用 Android Studio 打开项目时它也会自动补全。**走云端构建则完全不需要这一步，CI 里会自动生成。**

```bash
# 需要本机已安装 Gradle（任意 8.x 版本均可，会按 properties 下载 8.6）
gradle wrapper --gradle-version 8.6

# 1) Debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 2) Release APK（开启 R8 混淆 + 资源压缩）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk

# 3) 单元测试 / 静态检查
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Windows PowerShell 里用 `.\gradlew.bat` 代替 `./gradlew`。

> 本机若只装了 JDK 25，AGP 8.4 会直接报错，必须装 JDK 17；这也是推荐走云端构建的原因。

### Release 签名

默认情况下（没有 `keystore/countdown.jks`）release 构建会回落到 debug 签名，方便直接产出可安装的 APK。
接入正式签名：

```bash
keytool -genkeypair -v -keystore keystore/countdown.jks \
  -alias countdown -keyalg RSA -keysize 2048 -validity 10000

# 可选：用环境变量覆盖默认值
export COUNTDOWN_KEYSTORE=keystore/countdown.jks
export COUNTDOWN_STORE_PASSWORD=你的密码
export COUNTDOWN_KEY_ALIAS=countdown
export COUNTDOWN_KEY_PASSWORD=你的密码
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 说明

- **剩余天数**：`ChronoUnit.DAYS.between(today, target)`，今天到期显示 `0 / 就是今天`。
- **日期存储**：Room 里存 `epochDay`（`LocalDate.toEpochDay()`），跨时区不会错一天。
- **每日通知**：`PeriodicWorkRequest` 24 小时一次，`initialDelay` 对齐到次日 09:00；
  汇总成一条通知（最多列 6 条事件），已过期事件不再提醒。
- **小组件**：长按桌面 → 小组件 → 找到「倒计日」；`updatePeriodMillis=0`，由 WorkManager 驱动刷新。
- **通知权限**：仅在 Android 13+ 请求；被拒绝时事件仍可保存，只是不发通知。
