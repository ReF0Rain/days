<#!
.SYNOPSIS
    把当前项目推送到 GitHub 仓库（Windows PowerShell 版一键脚本）。

.DESCRIPTION
    做四件事：git 初始化（如需要）→ 提交 → 关联远端 → 推送。
    推送后 GitHub Actions 会按 .github/workflows/android-ci.yml 自动构建 APK。

.PARAMETER RemoteUrl
    远端仓库地址，例如 https://github.com/<你的用户名>/countdown.git

.EXAMPLE
    .\scripts\push-to-github.ps1 -RemoteUrl https://github.com/yourname/countdown.git

.EXAMPLE
    # 只想本地提交、不推送
    .\scripts\push-to-github.ps1 -RemoteUrl https://github.com/yourname/countdown.git -SkipPush
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

# 项目根目录 = 本脚本所在目录的上一级
$root = Split-Path -Parent $PSScriptRoot
Write-Host "项目根目录: $root" -ForegroundColor Cyan

Push-Location $root
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw '未找到 git，请先安装 Git for Windows: https://git-scm.com/download/win'
    }

    # 1) 初始化仓库
    if (-not (Test-Path (Join-Path $root '.git'))) {
        Write-Host '初始化 git 仓库…' -ForegroundColor Yellow
        git init | Out-Null
        git branch -M $Branch
    }

    # 2) 确认关键文件存在（避免推出一个构建不起来的仓库）
    $must = @(
        'settings.gradle.kts',
        'build.gradle.kts',
        'app\build.gradle.kts',
        'gradle\libs.versions.toml',
        '.github\workflows\android-ci.yml'
    )
    foreach ($f in $must) {
        if (-not (Test-Path (Join-Path $root $f))) {
            throw "缺少必要文件: $f"
        }
    }
    Write-Host '必要文件检查通过 ✓' -ForegroundColor Green

    # 3) 提交
    git add -A
    $status = git status --porcelain
    if ([string]::IsNullOrWhiteSpace($status)) {
        Write-Host '没有需要提交的改动。' -ForegroundColor Yellow
    }
    else {
        git commit -m $CommitMessage | Out-Null
        Write-Host "已提交: $CommitMessage" -ForegroundColor Green
    }

    if ($SkipPush) {
        Write-Host '-SkipPush 已指定，跳过推送。' -ForegroundColor Yellow
        return
    }

    # 4) 关联并推送远端
    $existing = git remote 2>$null
    if ($existing -contains 'origin') {
        git remote set-url origin $RemoteUrl
    }
    else {
        git remote add origin $RemoteUrl
    }

    Write-Host "推送到 $RemoteUrl ($Branch)…" -ForegroundColor Cyan
    git push -u origin $Branch

    Write-Host ''
    Write-Host '推送完成。下一步：' -ForegroundColor Green
    Write-Host '  1. 打开仓库的 Actions 页面，等待 “Android CI” 跑完'
    Write-Host '  2. 在该次运行页面底部 Artifacts 里下载 countdown-debug-apk.zip'
    Write-Host '  3. 解压得到 app-debug.apk，传到手机安装即可'
}
finally {
    Pop-Location
}
