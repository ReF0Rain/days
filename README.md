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
| `actions/setup-java@v4` (temurin 17) | 装 JDK 17（Gradle 8.6 不能在 JDK 25 上跑） |
| `gradle/actions/setup-gradle@v3` | 配置 Gradle 依赖缓存（不负责提供 `gradle` 命令） |
| `chmod +x gradlew` + `./gradlew --version` | **验证仓库内已提交的 Wrapper**（jar 缺失时自动用官方发行包补） |
| `./gradlew :app:testDebugUnitTest` | 跑单元测试，失败即中断 |
| `./gradlew :app:assembleDebug` / `assembleRelease` | 出两个 APK |
| `actions/upload-artifact@v4` | 上传 `countdown-debug-apk` / `countdown-release-apk` |
| `if: failure()` 上传报告 | 失败时上传 build/reports 方便排查 |

也可以不推送就手动跑：Actions → Android CI → **Run workflow**，`build_release` 勾掉就只出 debug。

> Artifacts 默认保留 30 天；如需长期保存，可在 CI 里加一步把 APK 发布到 Release（需要 `contents: write` 权限）。

### 查构建状态 / 拿下载链接

```powershell
# 查最新一次运行（不加 -Wait 只报告当前状态）
.\scripts\ci-status.ps1 -Repo ReF0Rain/days

# 轮询到结束，并打印 artifact 下载地址
.\scripts\ci-status.ps1 -Repo ReF0Rain/days -Wait
```

也可以用 Python 版本：`python tools/watch_ci.py ReF0Rain/days`（跑完自动列出产物）。
CI 失败时用 `python tools/ci_jobs.py ReF0Rain/days` 看是哪一步挂了。

## 本地构建（可选）

需要 JDK 17（AGP 8.4 支持 17~21，请勿使用 JDK 22+）与 Android SDK（API 34 + Build-Tools）。

Wrapper 已随仓库提交（`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar`，Gradle 8.6，
并带官方发行包 `distributionSha256Sum` 校验），**开箱即可构建，不需要先跑 `gradle wrapper`**。

```bash
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

> 本机若只装了 JDK 25，Gradle 8.6 会直接报 `What went wrong: 25.0.1`，必须装 JDK 17；
> 这也是推荐走云端构建的原因。Wrapper 文件丢失时可用 `python tools/fetch_wrapper.py .` 重新拉取官方文件。

### 完全没有 Android 环境时的本地验证（本仓库自带工具）

这台开发机最初没有 Android SDK、没有 Gradle、只有 JDK 25，下面是为此写的一次性环境搭建脚本，
装到临时目录（不污染系统）。GitHub Actions 上不需要这些：

```powershell
py tools\fetch_jdk17.py .           # 便携版 Temurin JDK 17 -> %TEMP%\countdown-jdk17
py tools\setup_android_sdk.py .     # 便携版 Android SDK（platform-tools + android-34 + build-tools 34）
                                    # 同时写 local.properties

# 一键构建（自动复制到 ASCII 临时路径、注入 JDK17/SDK 环境）
py tools\local_build.py --task :app:assembleDebug
py tools\local_build.py --task :app:assembleDebug :app:testDebugUnitTest
py tools\local_build.py --task :app:assembleRelease

# 只看错误行
py tools\local_build.py --task :app:assembleDebug --errors-only

# 脱离 Gradle 单独跑纯 JVM 单测（几秒钟）
py tools\run_unit_test.py .

# 查 CI 状态 / 拉日志
py tools\watch_ci.py ReF0Rain/days
py tools\ci_jobs.py ReF0Rain/days
```

> **为什么 `local_build.py` 要复制到 ASCII 路径**：项目路径 `...\新建文件夹\CountdownApp` 含非 ASCII 字符，
> AGP 会直接抛 `StopExecutionException: Your project path contains non-ASCII characters.`。
> 该脚本把源码复制到 `%TEMP%\CountdownApp` 再构建，从而绕开这个限制；
> 若你把项目放在纯 ASCII 路径下（如 `D:\code\CountdownApp`），直接 `.\gradlew.bat` 即可，不需要这个脚本。

### Release 签名与发版

正式签名（keystore 已生成）、GitHub Secrets 配置、打 tag 发版流程、ProGuard mapping 留档、
以及**数据库迁移约定**，全部整理在 **[docs/RELEASE.md](docs/RELEASE.md)**。速览：

```powershell
# 配置 Secrets（需 gh 已登录；未登录会打印要手动粘贴的值）
.\scripts\setup-github-secrets.ps1 -Repo ReF0Rain/days

# 发版：打 tag 并推送，CI 会自动签名构建 + 创建 GitHub Release
git tag v1.0.0
git push origin v1.0.0
# 之后永久下载地址： https://github.com/ReF0Rain/days/releases/latest
```

**版本号从 tag 自动推导**，不需要手改 `build.gradle.kts`：`v1.2.3` → versionName `1.2.3`、
versionCode `10203`（= `1*10000 + 2*100 + 3`）。没有 tag 时回落到 `1.0.0` / `1`。

本机查看当前生效的签名方式与版本：
```bash
./gradlew :app:printSigningInfo
```

> 签名凭据解析优先级：环境变量 → `keystore/keystore.properties` → 回落到 debug 签名。
> 所以本机 `assembleRelease` 出的就是正式签名包；CI 上没配 Secrets 时则回落并明确告警。

### Release 签名（历史说明）

`keystore/` 目录不入库（`.gitignore` 已忽略），私钥请离线备份 —— 丢失后已发布的应用将无法更新。

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 注意 release 包与 debug 包 applicationId 不同（.debug 后缀），会同时装成两个图标
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 说明

- **剩余天数**：`ChronoUnit.DAYS.between(today, target)`，今天到期显示 `0 / 就是今天`。
- **日期存储**：Room 里存 `epochDay`（`LocalDate.toEpochDay()`），跨时区不会错一天。
- **每日通知**：`PeriodicWorkRequest` 24 小时一次，`initialDelay` 对齐到次日 09:00；
  汇总成一条通知（最多列 6 条事件），已过期事件不再提醒。
- **小组件**：长按桌面 → 小组件 → 找到「倒计日」；`updatePeriodMillis=0`，由 WorkManager 驱动刷新。
- **通知权限**：仅在 Android 13+ 请求；被拒绝时事件仍可保存，只是不发通知。
- **数据库迁移**：刻意不用 `fallbackToDestructiveMigration()`（那会静默清空用户数据），
  改为显式注册迁移；改表结构时请按 [docs/RELEASE.md](docs/RELEASE.md) 第三节的步骤操作。
