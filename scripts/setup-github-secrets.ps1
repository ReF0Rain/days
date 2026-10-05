<#!
.SYNOPSIS
    把 release 签名所需的 4 个值写入 GitHub 仓库 Secrets。

.DESCRIPTION
    优先使用 GitHub CLI（gh）。若未安装或未登录，则打印需要手动粘贴的值和步骤。
    值来自 keystore/keystore.base64.txt 与 keystore/keystore.properties
    （由 tools/make_keystore.py 生成）。

.PARAMETER Repo
    仓库全名 owner/name。

.PARAMETER KeystoreDir
    keystore 目录，默认 <项目根>/keystore。

.EXAMPLE
    .\scripts\setup-github-secrets.ps1 -Repo ReF0Rain/days
#>
[CmdletBinding()]
param(
    [string]$Repo,
    [string]$KeystoreDir
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $KeystoreDir) { $KeystoreDir = Join-Path $root 'keystore' }

$b64File = Join-Path $KeystoreDir 'keystore.base64.txt'
$propsFile = Join-Path $KeystoreDir 'keystore.properties'
$jksFile = Join-Path $KeystoreDir 'countdown.jks'

foreach ($f in @($b64File, $propsFile, $jksFile)) {
    if (-not (Test-Path $f)) {
        throw "缺少 $f，请先运行: python tools\make_keystore.py `"$root`""
    }
}

function Read-Props {
    param([string]$Path)
    $map = @{}
    foreach ($line in Get-Content $Path) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith('#')) { continue }
        $idx = $t.IndexOf('=')
        if ($idx -lt 1) { continue }
        $map[$t.Substring(0, $idx).Trim()] = $t.Substring($idx + 1).Trim()
    }
    return $map
}

$props = Read-Props -Path $propsFile
$base64 = (Get-Content $b64File -Raw).Trim()

$secrets = [ordered]@{
    'KEYSTORE_BASE64'   = $base64
    'KEYSTORE_PASSWORD' = $props['storePassword']
    'KEY_ALIAS'         = $props['keyAlias']
    'KEY_PASSWORD'      = $props['keyPassword']
}

foreach ($k in $secrets.Keys) {
    if ([string]::IsNullOrWhiteSpace($secrets[$k])) {
        throw "keystore.properties 里缺少 $k 对应的值（storePassword / keyAlias / keyPassword）"
    }
}

Write-Host "keystore : $jksFile" -ForegroundColor Cyan
Write-Host "base64   : $($base64.Length) 字符" -ForegroundColor Cyan
Write-Host "alias    : $($secrets['KEY_ALIAS'])" -ForegroundColor Cyan
Write-Host ''

$gh = Get-Command gh -ErrorAction SilentlyContinue
$canUseGh = $false
if ($gh) {
    & gh auth status 2>&1 | Out-Null
    $canUseGh = ($LASTEXITCODE -eq 0)
}

if ($canUseGh -and $Repo) {
    Write-Host "使用 GitHub CLI 写入 $Repo 的 Secrets ..." -ForegroundColor Green
    foreach ($k in $secrets.Keys) {
        # 通过标准输入传值，避免出现在进程命令行里
        $secrets[$k] | & gh secret set $k --repo $Repo --body -
        if ($LASTEXITCODE -ne 0) { throw "设置 $k 失败" }
        Write-Host "  ✓ $k" -ForegroundColor Green
    }
    Write-Host ''
    Write-Host '完成。验证方式：' -ForegroundColor Green
    Write-Host "  gh secret list --repo $Repo"
    Write-Host '然后推送一个 tag 触发正式发版（见 README「发版流程」）。'
    return
}

if (-not $gh) {
    Write-Host '未检测到 GitHub CLI（gh），改为手动配置。' -ForegroundColor Yellow
}
elseif (-not $canUseGh) {
    Write-Host 'gh 未登录（先执行 gh auth login），改为手动配置。' -ForegroundColor Yellow
}
elseif (-not $Repo) {
    Write-Host '未提供 -Repo 参数，改为手动配置。' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '=' * 78
Write-Host '手动配置步骤'
Write-Host '=' * 78
Write-Host '1) 打开：<仓库> -> Settings -> Secrets and variables -> Actions'
Write-Host '2) 点 "New repository secret"，逐个添加下面 4 个：'
Write-Host ''
foreach ($k in @('KEYSTORE_BASE64', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD')) {
    Write-Host "   Name : $k"
    if ($k -eq 'KEYSTORE_BASE64') {
        Write-Host "   Secret: 见文件 $b64File （整行复制，$($base64.Length) 字符）"
    }
    else {
        Write-Host "   Secret: $($secrets[$k])"
    }
    Write-Host ''
}
Write-Host '=' * 78
Write-Host '也可以先安装并登录 GitHub CLI，再重跑本脚本自动写入：'
Write-Host '  winget install GitHub.cli'
Write-Host '  gh auth login'
Write-Host "  .\scripts\setup-github-secrets.ps1 -Repo <owner>/<repo>"
