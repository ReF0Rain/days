# 倒计日 · Countdown

Kotlin + Jetpack Compose 实现的倒计日 App：记录重要日期，实时显示剩余天数。

- 包名 / applicationId：`com.example.countdown`
- minSdk 26（Android 8.0）/ targetSdk 34 / compileSdk 34
- 技术栈：Compose + Material3 + Room(KSP) + ViewModel + WorkManager + Glance 小组件

## 功能

| 功能 | 实现位置 |
| --- | --- |
| 添加 / 编辑 / 删除事件 | `ui/screens/AddEditScreen.kt`、`data/CountdownDao.kt` |
| **倒计日 / 正计日双模式** | `data/CountdownEvent.kt`（`CountdownMode`）、`data/CountdownCalculator.kt` |
| **自选图片背景 + 文字清晰度调节** | `util/BackgroundImageStore.kt`、`ui/components/EventCard.kt` |
| 选择日期（Material3 DatePicker） | `AddEditScreen.kt` 中的 `DatePickerDialog` |
| 显示天数（倒计日：未来为正；正计日：已过天数） | `data/CountdownCalculator.kt` |
| 按天数排序（可切换创建时间） | `ui/CountdownViewModels.kt` |
| Room 本地持久化 + **显式迁移** | `data/CountdownEvent.kt`、`CountdownDatabase.kt` |
| Android 13 通知权限 | `MainActivity.kt`、`notification/CountdownNotifications.kt` |
| 每日更新通知（每天 09:00 对齐，WorkManager 周期任务） | `notification/DailyUpdateScheduler.kt`、`DailyUpdateWorker.kt` |
| 可选桌面小组件（Glance） | `widget/CountdownWidget.kt`、`widget/CountdownWidgetReceiver.kt` |

### 两种计时模式

| 模式 | 语义 | 举例 | 卡片文案 |
| --- | --- | --- | --- |
| 倒计日 `COUNTDOWN` | 距离目标还有多少天 | 婚礼在 2027-05-01 | `128 天后` |
| 正计日 `COUNTUP` | 从那天起已经过了多少天 | 入职日 2025-03-01 | `已过 365 天` |

两者共用 `target_date` 一个字段，靠 `mode` 列区分；排序口径统一在
`CountdownCalculator.sortKey()` 里（倒计日按剩余天数、正计日按已过天数取负），
所以列表页与桌面小组件的顺序始终一致。

### 自定义背景图

- 通过系统照片选择器 `ActivityResultContracts.PickVisualMedia` 选图，
  **不需要申请存储权限**（Android 13+ 由系统选择器授权）
- 图片会**复制进应用内部存储**（`filesDir/backgrounds/`），而不是只存 content URI ——
  用户删掉相册原图后背景不会变空白；换图/删除背景时旧文件会被清理
- 卡片自动叠加暗化蒙版 + 上下渐变，保证任何图片上的文字都可读；
  编辑页有「文字清晰度」滑杆可实时预览调节（存进 `background_dim`）

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

# 校验数据库迁移：把迁移 SQL 作用在 v1 schema 上，与 Room 期望的 v2 schema 逐列比对
# （等价于 MigrationTestHelper.runMigrationsAndValidate，但不需要模拟器）
py tools\verify_migration.py app\schemas

# 校验 base64 解码逻辑（7 种脏数据场景）
bash tools/test_decode_logic.sh keystore/keystore.base64.txt <口令> "$JAVA_HOME"
```

> **改了数据库就一定要跑 `verify_migration.py`**。它会用两个版本的 Room schema JSON
> 建表、灌一条旧数据、执行迁移 SQL，然后逐列对比类型/非空/默认值与索引。
> 实测它抓到过一个真问题：迁移里写了 `ADD COLUMN background_uri TEXT DEFAULT NULL`，
> sqlite 会把默认值记成字符串 `'NULL'`，与 Room 期望的"无默认值"不一致。
>
> 有真机/模拟器时也应跑一次真机迁移测试：
> ```bash
> ./gradlew :app:connectedDebugAndroidTest   # 见 CountdownMigrationTest
> ```

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

发版失败时先跑**签名诊断**（在 Actions 页面 Run workflow 时勾选 `diagnostics`）：
它会把 4 个 Secret 的长度、base64 清洗前后长度、解码后字节数与 SHA256、keystore 口令校验
结果全部打进公开可读的 job 日志，便于一眼定位是 Secret 内容问题还是流程问题。
详见 [docs/RELEASE.md](docs/RELEASE.md) 第五节。

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
- **跨零点刷新**：天数由 `util/DateTicker` 驱动的按天 ticker 重算，
  所以应用在前台过夜时"剩余 1 天"会正确变成 0。
  `CountdownItem` 的派生逻辑是顶层函数 `toItem(date)`，日期必须显式传入（便于单测）。
- **背景图存储**：导入时按最长边 1440px 降采样 + JPEG 85 重编码，
  不原样保存相册原图（否则 12MP 照片会带来 OOM 风险）。

### 已知限制

| 项 | 说明 |
| --- | --- |
| 每日通知时间 | WorkManager 周期任务，Doze 下可能被推迟，不保证精确 9:00 |
| 未做真机验证 | 迁移测试 `CountdownMigrationTest` 需要真机/模拟器，尚未运行过 |
| 无 UI 自动化测试 | 界面改动只能靠安装后人工确认（这是"正计日看不出区别"那次问题的根因） |
| 排序方式不持久化 | 「按天数 / 按创建时间」切换后重启会回到默认值 |
| 两个 APK 并存 | debug 包 `applicationId` 带 `.debug` 后缀，会和 release 包装成两个图标 |
