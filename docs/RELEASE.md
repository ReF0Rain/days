# 发版与签名指南

本文档覆盖三件事：**正式签名怎么配**、**怎么发一个版本**、**怎么改数据库不丢用户数据**。

---

## 一、正式签名

### 1.1 keystore 已经生成好了

`keystore/countdown.jks`（PKCS12，RSA 2048，有效期 10000 天，alias = `countdown`）已经生成，
同目录下还有：

| 文件 | 用途 | 是否入库 |
| --- | --- | --- |
| `keystore/countdown.jks` | 私钥本体 | ❌ 已被 `.gitignore` 忽略 |
| `keystore/keystore.properties` | 本地构建读取的密码 | ❌ 已忽略 |
| `keystore/keystore.base64.txt` | 贴到 GitHub Secrets 用 | ❌ 已忽略 |

> ⚠️ **请立刻离线备份整个 `keystore/` 目录**（U 盘 / 密码管理器 / 私有网盘）。
> 私钥丢失后，用同一个 applicationId 发布过的应用将**永远无法再更新**，
> 只能换包名重新上架，用户得重新安装。

重新生成（会更换签名，老用户将无法覆盖安装）：
```powershell
py tools\make_keystore.py . --force
```

### 1.2 配到 GitHub Secrets

需要 4 个 Secret：

| Secret 名 | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | `keystore/keystore.base64.txt` 的整行内容 |
| `KEYSTORE_PASSWORD` | `keystore/keystore.properties` 里的 `storePassword` |
| `KEY_ALIAS` | `countdown` |
| `KEY_PASSWORD` | 同 `storePassword`（PKCS12 要求一致） |

**方式 A（自动，推荐）**：装了 GitHub CLI 并登录后一条命令搞定：
```powershell
winget install GitHub.cli      # 只装一次
gh auth login
.\scripts\setup-github-secrets.ps1 -Repo ReF0Rain/days
```

**方式 B（手动）**：仓库 → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`，
逐个添加上面 4 个。运行下面这条可以打印出需要粘贴的值：
```powershell
.\scripts\setup-github-secrets.ps1 -Repo ReF0Rain/days   # 未登录 gh 时会打印值
```

配置完成后必须**重新构建**才生效（Secret 是在运行时注入的）。

### 1.3 本地构建也自动用正式签名

`app/build.gradle.kts` 的解析优先级：

1. 环境变量 `COUNTDOWN_KEYSTORE` / `COUNTDOWN_STORE_PASSWORD` / `COUNTDOWN_KEY_ALIAS` / `COUNTDOWN_KEY_PASSWORD`（CI 用）
2. `keystore/keystore.properties`（本地用，所以本机 `assembleRelease` 已经是正式签名）
3. 都没有 → 回落到 debug 签名

想确认当前用的是哪种签名，跑：
```bash
./gradlew :app:printSigningInfo
# [signing] release 使用正式 keystore：countdown.jks, alias=countdown
# [signing] 未检测到正式签名凭据，release 会使用 debug 签名
```

---

## 二、发版流程

### 2.1 版本号规则

版本号**从 git tag 自动推导**（不需要手改 `build.gradle.kts`）：

| tag | versionName | versionCode |
| --- | --- | --- |
| 无 tag | `1.0.0` | `1` |
| `v1.2.3` | `1.2.3` | `10203` |
| `v2.0.0` | `2.0.0` | `20000` |

`versionCode = major*10000 + minor*100 + patch`，所以每段最多两位（`v1.100.0` 会被忽略并回落到默认值）。
这个规则满足 Google Play 的要求（versionCode 单调递增且不超上限）。

### 2.2 发一个版本（三步）

```bash
# 1) 确保 main 上是要发的代码
git push origin main

# 2) 打 tag 并推送 —— 这一步就会触发正式构建和自动发布
git tag v1.0.0
git push origin v1.0.0
```

3) 等 Actions 跑完（约 4~6 分钟），然后去 **仓库首页右侧 Releases**，
   或直接访问 `https://github.com/<owner>/<repo>/releases/latest`。

这个链接是**永久固定**的，每次发新版本它自动指向最新版 —— 以后分享给人装就用这个，
不用再进 Actions 翻 Artifacts。

### 2.3 打 tag 时 Actions 做了什么

`.github/workflows/android-ci.yml` 里的 `release` job（只在 `v*` tag 触发）：

1. 还原 `KEYSTORE_BASE64` 为 `keystore/countdown.jks`
2. 跑单元测试（失败则不发版）
3. `./gradlew :app:assembleRelease`（正式签名 + R8 混淆 + 资源压缩）
4. 用 `apksigner verify --print-certs` 校验签名，把证书指纹写进 Actions Summary
5. 上传 `mapping.txt`（保留 90 天，用于还原线上崩溃栈）
6. 创建 GitHub Release，附带 APK 和自动生成的变更说明

常驻的 `build` job（任何 push / PR 都跑）则只出 debug + release 验证包并上传为 Artifacts。

### 2.4 预发布版本

tag 名里带 `-` 会被自动标记为 pre-release，例如 `v1.1.0-beta1`：

```bash
git tag v1.1.0-beta1
git push origin v1.1.0-beta1
```

> 注意：`v1.1.0-beta1` 不匹配 `\d+\.\d+\.\d+`，所以版本号会回落到默认的 `1.0.0`。
> 需要严格的预发布版本号，就手动传参构建：
> `./gradlew :app:assembleRelease -PversionNameSuffix=-beta1`

### 2.5 撤销一个发错的版本

```bash
gh release delete v1.0.0 --yes     # 或网页上删除 Release
git push origin :refs/tags/v1.0.0  # 删除远端 tag
git tag -d v1.0.0                  # 删除本地 tag
```

> 已经被人下载安装的包无法收回；如果签名/密钥泄露，只能换 keystore 并更换包名。

---

## 三、数据库迁移约定

`CountdownDatabase` 现在**没有** `fallbackToDestructiveMigration()` —— 这是有意的：
那个方法会在版本升级时删库重建，用户存的倒计日事件会全部丢失。

代价是：如果升了 `version` 却没写迁移，应用启动时会直接抛
`IllegalStateException: A migration from X to Y was required but not found`。
这是**好事** —— 构建期/测试期就能发现，而不是用户悄悄丢数据。

### 改表结构的正确步骤

假设要给事件加一个"颜色标签"字段：

1. `CountdownEvent.kt` 加字段：`val colorTag: String? = null`
2. `CountdownDatabase.kt` 把 `version = 1` 改成 `2`
3. 在 `CountdownDatabase` 的 companion 里加迁移：

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN color_tag TEXT")
    }
}
```

4. 加进注册表：`private val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)`
5. 补一个迁移测试（`app/schemas/` 下已经导出了 schema JSON，可直接用 Room 的
   `MigrationTestHelper` 断言"升级后旧数据还在、表结构正确"）

### 为什么必须提交 `app/schemas/`

`app/build.gradle.kts` 里配了 `room.schemaLocation`，KSP 会把每个版本的 schema JSON 导出到
`app/schemas/`。这些文件**要提交进仓库**：迁移测试和 `MigrationTestHelper` 都依赖它们，
而且它是判断"某个版本的表结构到底是什么样"的唯一权威记录。

> 注意：`.gitignore` 里排除了 `schemas/` 目录名的匹配是给 Gradle 的构建目录用的，
> `app/schemas/` 不在排除范围内（路径不同），会正常提交。

---

## 四、附：CI 里用到的 Secret 汇总

| Secret | 必需 | 作用 |
| --- | --- | --- |
| `KEYSTORE_BASE64` | 发版必需 | 还原 keystore 文件 |
| `KEYSTORE_PASSWORD` | 发版必需 | keystore 口令 |
| `KEY_ALIAS` | 发版必需 | 密钥别名（`countdown`） |
| `KEY_PASSWORD` | 发版必需 | 密钥口令（与 keystore 口令相同） |

未配置时 CI **不会失败**，但 release APK 会是 debug 签名，Release 说明里也会明确标注警告。

---

## 五、发版失败怎么排查

### 5.1 签名诊断（推荐第一步）

GitHub → **Actions** → 左侧选 `Android CI` → 右上 **Run workflow** → 勾选 **`diagnostics`** → 运行。

诊断 job（`Signing secrets check`）会把下列信息写进**公开可读**的 job 日志，**不打印任何 Secret 内容**：

```
KEYSTORE_BASE64      长度=3668   空=no
KEYSTORE_PASSWORD    长度=24     空=no
KEY_ALIAS            长度=9      空=no
KEY_PASSWORD         长度=24     空=no

base64 原始长度: 3668（期望 3668）
非法字符数: 0  十六进制(sha256前16=...): 
清洗后长度: 3668
前 24: MIIKugIBAzCCCmQGCSqGSIb3
末 16: 9hIb2cxix+XCIQICJxA=
长度对 4 取余: 0
解码字节数: 2750（期望 2750）
sha256: e31d6cc7fc8788cef0cd76d7f00a3bd9d2a4f17234b60297ef57e87478893289
期望  : e31d6cc7fc8788cef0cd76d7f00a3bd9d2a4f17234b60297ef57e87478893289
keystore 口令校验: 通过
```

**对照表**：

| 现象 | 含义 | 处理 |
| --- | --- | --- |
| `长度=0 空=yes` | Secret 名写错或没保存 | 检查名字是否完全一致（区分大小写） |
| 非法字符数 > 0，且十六进制含 `0A`/`0D`/`20` | 粘贴时带进了换行/回车/空格 | 用 `Get-Content keystore\keystore.base64.txt \| Set-Clipboard` 重新粘贴 |
| 非法字符含 `EF BB BF` | 带进了 UTF-8 BOM | 同上，重新粘贴（不要从文件"另存为"取内容） |
| 清洗后长度 < 3668 | 内容被截断 | 重新完整复制 |
| 解码字节数 ≠ 2750 或 sha256 不匹配 | 内容不是这个 keystore | 确认用的是本仓库生成的 `keystore.base64.txt` |
| 口令校验失败 | `KEYSTORE_PASSWORD` 与 keystore 实际口令不符 | 见 `keystore/keystore.properties` 的 `storePassword` |

### 5.2 为什么不用 `echo "$SECRET" | base64 -d`

最初的实现是这一行，结果 tag 发版在第 5 步反复失败：

```bash
echo "$KEYSTORE_BASE64" | base64 --decode > keystore/countdown.jks   # ❌ 太脆
```

`base64 --decode` 对**任何** base64 字母表之外的字符零容忍（包括看不见的换行、空格、BOM），
遇到就报 `invalid input`。而把 3668 字符复制进网页表单时，带进不可见字符是很常见的。

现在的实现（`.github/workflows/android-ci.yml` 的 `Decode signing keystore` 步骤）：

1. **逐字符扫描**，只保留 `[A-Za-z0-9+/=]`，并统计/打印被剔除字符的十六进制
   （不用 `tr -d '[:space:]'`，因为不同 locale 下 `[:space:]` 行为不一致，且可能截断非 ASCII 字节）
2. 长度不足 3000 直接判为"内容不完整"并给出重新复制的命令
3. **自动补齐**被吞掉的尾部 `=` 补位符（长度 %4 == 2 补 `==`，== 3 补 `=`，== 1 判非法）
4. 打印解码后字节数与 SHA256，与期望值直接比对
5. 用 `keytool -list` 校验 keystore 能被口令打开（带 `JAVA_HOME` 兜底）

### 5.3 日志在哪儿看

- **Actions 页面**：`https://github.com/<owner>/<repo>/actions` → 点进某次运行 → 点左侧 job 名 → 展开步骤
- **注意**：GitHub 现在对**未登录**请求一律拒绝下载 job 原始日志和 artifact（HTTP 403），
  必须登录浏览器或用 `gh run view --log`。想本地自动化读取，装 GitHub CLI 后：
  ```powershell
  winget install GitHub.cli
  gh auth login
  gh run list -R ReF0Rain/days
  gh run view <run-id> --log -R ReF0Rain/days
  ```

### 5.4 本地复现发版流程

不依赖 CI 也能验证签名与版本号：

```powershell
py tools\local_build.py --task :app:assembleRelease :app:printSigningInfo
# [signing] release 使用正式 keystore：countdown.jks, alias=countdown
# [version] tag=v1.0.0 -> versionName=1.0.0, versionCode=10000

# 校验产物签名（应显示 CN=Countdown，而不是 CN=Android Debug）
& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```
