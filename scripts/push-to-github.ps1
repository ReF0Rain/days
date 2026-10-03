<#!
.SYNOPSIS
    把当前项目推送到 GitHub 仓库（Windows PowerShell 版一键脚本）。

.DESCRIPTION
    做这些事：校验参数 → git 初始化（如需要）→ 提交 → 校验远端可达 → 推送，
    并逐步检查退出码（原生命令失败不会被 PowerShell 自动抛出，所以这里手动校验）。
    推送成功后 GitHub Actions 会按 .github/workflows/android-ci.yml 自动构建 APK。

.PARAMETER RemoteUrl
    远端仓库地址，例如 https://github.com/ReF0Rain/days.git
    注意：不要带 < > 尖括号，那是文档里的占位符。

.PARAMETER Branch
    分支名，默认 main。

.PARAMETER CommitMessage
    提交信息。

.PARAMETER SkipPush
    只本地提交，不推送。

.EXAMPLE
    .\scripts\push-to-github.ps1 -RemoteUrl https://github.com/ReF0Rain/days.git
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RemoteUrl,

    [string]$Branch = 'main',

    [string]$CommitMessage = 'feat: 倒计日 App（Compose + Room + WorkManager + Glance 小组件）',

    [switch]$SkipPush
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    <#
      运行 git 并强制检查退出码。
      PowerShell 不会因为原生命令返回非 0 而抛异常，这是之前误报“推送完成”的根因。
    #>
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [string]$What = 'git',

        [switch]$AllowFailure
    )
    $output = & git @Arguments 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        Write-Host ($output | Out-String) -ForegroundColor DarkGray
        throw "$What 失败（git 退出码 $code）。"
    }
    return [pscustomobject]@{ Output = $output; Code = $code }
}

# ---------- 1) 参数校验：这是本次 400 错误的直接原因 ----------
$RemoteUrl = $RemoteUrl.Trim()
if ($RemoteUrl -match '[<>]') {
    throw @"
远端地址里包含尖括号：$RemoteUrl
<> 是文档里的占位符，必须替换成真实用户名。正确写法例如：
  https://github.com/ReF0Rain/days.git
"@
}
if ($RemoteUrl -match '\s') {
    throw "远端地址里包含空格：$RemoteUrl"
}
if ($RemoteUrl -notmatch '^(https://|git@|ssh://)') {
    throw "无法识别的远端地址：$RemoteUrl（应形如 https://github.com/owner/repo.git）"
}

$root = Split-Path -Parent $PSScriptRoot
Write-Host "项目根目录: $root" -ForegroundColor Cyan
Write-Host "远端地址  : $RemoteUrl" -ForegroundColor Cyan

Push-Location $root
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw '未找到 git，请先安装 Git for Windows: https://git-scm.com/download/win'
    }

    # ---------- 2) 初始化仓库 ----------
    if (-not (Test-Path (Join-Path $root '.git'))) {
        Write-Host '初始化 git 仓库…' -ForegroundColor Yellow
        [void](Invoke-Git -Arguments @('init') -What 'git init')
        [void](Invoke-Git -Arguments @('branch', '-M', $Branch) -What 'git branch -M')
    }

    # ---------- 3) 关键文件自检 ----------
    $must = @(
        'settings.gradle.kts',
        'build.gradle.kts',
        'app\build.gradle.kts',
        'gradle\libs.versions.toml',
        '.github\workflows\android-ci.yml'
    )
    foreach ($f in $must) {
        if (-not (Test-Path (Join-Path $root $f))) { throw "缺少必要文件: $f" }
    }
    Write-Host '必要文件检查通过 ✓' -ForegroundColor Green

    # ---------- 4) 提交 ----------
    [void](Invoke-Git -Arguments @('add', '-A') -What 'git add')

    $hasHead = (Invoke-Git -Arguments @('rev-parse', '--verify', 'HEAD') -What 'git rev-parse' -AllowFailure).Code -eq 0
    $staged = & git diff --cached --name-only
    $stagedCount = ($staged | Where-Object { $_ }).Count

    if (-not $hasHead) {
        Write-Host "创建首个提交（$stagedCount 个文件）…" -ForegroundColor Yellow
        [void](Invoke-Git -Arguments @('commit', '-m', $CommitMessage) -What 'git commit')
        Write-Host "已提交: $CommitMessage" -ForegroundColor Green
    }
    elseif ($stagedCount -gt 0) {
        [void](Invoke-Git -Arguments @('commit', '-m', $CommitMessage) -What 'git commit')
        Write-Host "已提交 $stagedCount 个文件: $CommitMessage" -ForegroundColor Green
    }
    else {
        Write-Host '没有需要提交的改动，沿用已有提交。' -ForegroundColor Yellow
    }

    # 保证真的有提交可推
    $headSha = (Invoke-Git -Arguments @('rev-parse', 'HEAD') -What 'git rev-parse HEAD').Output
    Write-Host "当前提交: $headSha" -ForegroundColor DarkGray

    if ($SkipPush) {
        Write-Host '-SkipPush 已指定，跳过推送。' -ForegroundColor Yellow
        return
    }

    # ---------- 5) 关联远端 ----------
    $existing = & git remote
    if ($existing -contains 'origin') {
        [void](Invoke-Git -Arguments @('remote', 'set-url', 'origin', $RemoteUrl) -What 'git remote set-url')
    }
    else {
        [void](Invoke-Git -Arguments @('remote', 'add', 'origin', $RemoteUrl) -What 'git remote add')
    }

    # ---------- 6) 推送前先确认远端可达（凭据/仓库名错误在这里就暴露） ----------
    Write-Host '检查远端可达性…' -ForegroundColor Cyan
    $probe = Invoke-Git -Arguments @('ls-remote', '--heads', 'origin') -What 'git ls-remote' -AllowFailure
    if ($probe.Code -ne 0) {
        Write-Host ($probe.Output | Out-String) -ForegroundColor DarkGray
        throw @"
无法访问远端 $RemoteUrl
常见原因：
  1. 仓库还不存在 —— 先去 GitHub 建一个空仓库（不要勾 README/.gitignore）
  2. 地址写错（把占位符 <用户名> 原样保留了，或多了 .gitt 之类的尾巴）
  3. 没有权限 / 凭据失效 —— 用 https 推送需要 PAT，或配置 Git Credential Manager
"@
    }

    # ---------- 7) 推送并校验退出码 ----------
    Write-Host "推送到 $RemoteUrl ($Branch)…" -ForegroundColor Cyan
    $push = Invoke-Git -Arguments @('push', '-u', 'origin', $Branch) -What 'git push' -AllowFailure
    Write-Host ($push.Output | Out-String).Trim() -ForegroundColor DarkGray
    if ($push.Code -ne 0) {
        throw "推送失败（git 退出码 $($push.Code)），请检查上面的错误信息。"
    }

    Write-Host ''
    Write-Host '推送完成 ✓' -ForegroundColor Green
    Write-Host '下一步：' -ForegroundColor Green
    Write-Host '  1. 打开仓库 Actions 页面，等待 “Android CI” 跑完'
    Write-Host '  2. 在该次运行页面底部 Artifacts 里下载 countdown-debug-apk.zip'
    Write-Host '  3. 解压得到 app-debug.apk，传到手机安装即可'
    Write-Host ''
    Write-Host '也可以直接查状态（含下载链接）：' -ForegroundColor Cyan
    Write-Host '  .\scripts\ci-status.ps1 -Wait'
}
finally {
    Pop-Location
}
