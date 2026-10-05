<#!
.SYNOPSIS
    在真机上测试这个 App：查设备、装包、跑迁移测试、抓日志。

.DESCRIPTION
    不需要 Android Studio。脚本会自动找到本仓库用于构建的便携版 Android SDK 里的 adb
    （tools/setup_android_sdk.py 装的），并复用同一套 JDK 17 构建 APK。

    支持 USB 连接与无线调试两种方式；无线调试的分步操作会在 -Pair 时打印出来。

.PARAMETER Action
    devices  - 只列出设备
    install  - 构建并安装 debug APK
    test     - 构建并运行真机 instrumentation 测试（含数据库迁移测试）
    log      - 只抓日志（默认抓 App 相关标签 + 崩溃）
    all      - install -> test -> log（默认）

.PARAMETER Serial
    多设备时指定目标（adb devices 里的序列号）。

.PARAMETER LogSeconds
    log 动作抓取日志的秒数，默认 60。

.PARAMETER Pair
    打印无线调试的配对步骤。

.EXAMPLE
    .\scripts\device-test.ps1 -Action all
    .\scripts\device-test.ps1 -Action log -LogSeconds 120 -Serial 10.121.195.233:5555
    .\scripts\device-test.ps1 -Pair
#>
[CmdletBinding()]
param(
    [ValidateSet('devices', 'install', 'test', 'log', 'all')]
    [string]$Action = 'all',

    [string]$Serial,

    [int]$LogSeconds = 60,

    [switch]$Pair
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 定位工具链（与 tools/setup_android_sdk.py、tools/fetch_jdk17.py 保持一致）
# ---------------------------------------------------------------------------
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $env:TEMP 'android-sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$jdkCache = Join-Path $env:TEMP 'countdown-jdk17'

if (-not (Test-Path $adb)) {
    throw "找不到 adb：$adb`n先运行: py tools\setup_android_sdk.py `"$root`""
}

$jdkHome = $null
if (Test-Path $jdkCache) {
    $jdkHome = (Get-ChildItem $jdkCache -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
        Select-Object -First 1).FullName
}
if (-not $jdkHome) { throw "找不到便携版 JDK 17，先运行: py tools\fetch_jdk17.py `"$root`"" }

$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

Write-Host "adb : $adb" -ForegroundColor DarkGray
Write-Host "JDK : $jdkHome" -ForegroundColor DarkGray

function Show-WirelessSteps {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1).IPAddress
    Write-Host ''
    Write-Host '================ 无线调试（Android 11+）================' -ForegroundColor Cyan
    Write-Host '手机端：设置 -> 系统 -> 开发者选项 -> 无线调试 -> 开启'
    Write-Host '         点「使用配对码配对设备」，屏幕会显示 IP:端口 和 6 位配对码'
    Write-Host ''
    Write-Host '电脑端（把 <...> 换成手机上显示的）：'
    Write-Host "  1) & `"$adb`" pair <配对IP:端口>" -ForegroundColor Yellow
    Write-Host '     然后输入手机上那 6 位配对码'
    Write-Host "  2) & `"$adb`" connect <调试IP:端口>" -ForegroundColor Yellow
    Write-Host '     （注意：无线调试页面上的 IP:端口 和配对用的端口不一样）'
    Write-Host "  3) & `"$adb`" devices" -ForegroundColor Yellow
    Write-Host ''
    Write-Host 'Android 10 及以下没有无线调试界面，只能：'
    Write-Host '  1) 先用 USB 连接并开启「USB 调试」'
    Write-Host "  2) 执行 & `"$adb`" tcpip 5555"
    Write-Host "  3) 拔掉数据线，执行 & `"$adb`" connect $ip`:5555" -ForegroundColor Yellow
    Write-Host ''
    Write-Host "本机局域网 IP: $ip" -ForegroundColor DarkGray
    Write-Host '======================================================' -ForegroundColor Cyan
    Write-Host ''
}

function Get-TargetDevice {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }
    $ready = @()
    $unauthorized = @()
    foreach ($line in $lines) {
        $parts = $line -split '\s+'
        if ($parts.Count -lt 2) { continue }
        if ($parts[1] -eq 'device') { $ready += $parts[0] }
        elseif ($parts[1] -eq 'unauthorized') { $unauthorized += $parts[0] }
    }

    if ($unauthorized.Count -gt 0) {
        Write-Host '以下设备未授权，请在手机屏幕上点「允许 USB 调试」：' -ForegroundColor Yellow
        $unauthorized | ForEach-Object { Write-Host "  $_" }
    }

    if ($ready.Count -eq 0) { return $null }

    if ($Serial) {
        if ($ready -notcontains $Serial) {
            throw "指定的设备 $Serial 不在已就绪列表里：$($ready -join ', ')"
        }
        return $Serial
    }
    if ($ready.Count -gt 1) {
        Write-Host '检测到多个设备，请用 -Serial 指定：' -ForegroundColor Yellow
        $ready | ForEach-Object { Write-Host "  $_" }
        throw '需要 -Serial 参数'
    }
    return $ready[0]
}

function Invoke-Adb {
    param([string[]]$Arguments)
    & $adb @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') 失败（退出码 $LASTEXITCODE）" }
}

function Show-Devices {
    Write-Host ''
    Write-Host '=== adb devices ===' -ForegroundColor Cyan
    & $adb devices -l
    $d = Get-TargetDevice
    if ($d) { Write-Host "可用设备: $d" -ForegroundColor Green }
    else {
        Write-Host '没有可用设备。' -ForegroundColor Yellow
        Write-Host '请检查：1) USB 线已连接且手机已选「文件传输/MTP」模式  2) 已开启 USB 调试'
        Write-Host '        3) 手机上已点「允许 USB 调试」'
        Write-Host '或改用无线调试：.\scripts\device-test.ps1 -Pair'
    }
    return $d
}

function Build-Apks {
    Write-Host ''
    Write-Host '=== 构建 APK（debug + androidTest）===' -ForegroundColor Cyan
    # 复用项目自带的构建脚本：它会处理"中文路径"和 JDK/SDK 环境
    & py (Join-Path $root 'tools\local_build.py') --task :app:assembleDebug :app:assembleDebugAndroidTest --tail 5
    if ($LASTEXITCODE -ne 0) { throw '构建失败，先修构建再测真机' }

    $apkDir = Join-Path $env:TEMP 'CountdownApp\app\build\outputs\apk'
    return @{
        App  = Join-Path $apkDir 'debug\app-debug.apk'
        Test = Join-Path $apkDir 'androidTest\debug\app-debug-androidTest.apk'
    }
}

function Install-Apks {
    param([string]$Device, [hashtable]$Apks)
    Write-Host ''
    Write-Host '=== 安装到设备 ===' -ForegroundColor Cyan
    Invoke-Adb @('-s', $Device, 'install', '-r', '-t', $Apks.App)
    Invoke-Adb @('-s', $Device, 'install', '-r', '-t', $Apks.Test)
    Write-Host '安装完成' -ForegroundColor Green
}

function Run-Tests {
    param([string]$Device)
    Write-Host ''
    Write-Host '=== 运行真机 instrumentation 测试 ===' -ForegroundColor Cyan
    Write-Host '（包含数据库迁移测试 CountdownMigrationTest）'
    # -e 参数把 Room schema 位置传给 MigrationTestHelper
    & $adb -s $Device shell am instrument -w `
        -e class com.example.countdown.data.CountdownMigrationTest `
        -e room.schemaLocation /data/local/tmp/schemas `
        com.example.countdown.debug.test/androidx.test.runner.AndroidJUnitRunner
    $code = $LASTEXITCODE
    Write-Host "instrumentation 退出码: $code"
    if ($code -ne 0) {
        Write-Host '测试失败（或 runner 未找到）。完整结果需要看上面的输出。' -ForegroundColor Yellow
    }
    return $code
}

function Capture-Log {
    param([string]$Device, [int]$Seconds)
    Write-Host ''
    Write-Host "=== 抓取日志 $Seconds 秒 ===" -ForegroundColor Cyan
    Write-Host '请现在在手机上操作 App：添加一个正计日事件，复现你遇到的问题。' -ForegroundColor Yellow

    $logFile = Join-Path $env:TEMP ("countdown-logcat-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))
    # 清空旧缓冲，保证抓到的都是本次操作
    & $adb -s $Device logcat -c | Out-Null

    $job = Start-Job -ScriptBlock {
        param($adb, $device)
        & $adb -s $device logcat -v threadtime
    } -ArgumentList $adb, $Device

    Start-Sleep -Seconds $Seconds
    Stop-Job $job
    $content = Receive-Job $job
    Remove-Job $job -Force

    $content | Set-Content -Path $logFile -Encoding UTF8
    Write-Host "日志已保存: $logFile" -ForegroundColor Green

    # 立刻把关键信息摘出来，省得对方翻几万行
    Write-Host ''
    Write-Host '--- 崩溃 / 异常 ---' -ForegroundColor Cyan
    $patterns = 'FATAL EXCEPTION|AndroidRuntime|CountdownApp|BackgroundImageStore|SQLiteException|IllegalStateException|A migration from|ANR in'
    $hits = $content | Select-String -Pattern $patterns
    if ($hits) { $hits | Select-Object -First 40 | ForEach-Object { $_.Line.Trim() } }
    else { Write-Host '（没抓到崩溃或异常）' -ForegroundColor Green }

    Write-Host ''
    Write-Host '--- 与 App 相关的行（尾部 30 行）---' -ForegroundColor Cyan
    $appLines = $content | Select-String -Pattern 'com\.example\.countdown'
    if ($appLines) { $appLines | Select-Object -Last 30 | ForEach-Object { $_.Line.Trim() } }
    else { Write-Host '（没有 App 相关日志）' }

    return $logFile
}

# ===========================================================================
# 主流程
# ===========================================================================
if ($Pair) { Show-WirelessSteps }

switch ($Action) {
    'devices' {
        Show-Devices | Out-Null
    }
    'install' {
        $d = Show-Devices
        if (-not $d) { throw '没有可用设备' }
        $apks = Build-Apks
        Install-Apks -Device $d -Apks $apks
    }
    'test' {
        $d = Show-Devices
        if (-not $d) { throw '没有可用设备' }
        $apks = Build-Apks
        Install-Apks -Device $d -Apks $apks
        Run-Tests -Device $d | Out-Null
    }
    'log' {
        $d = Show-Devices
        if (-not $d) { throw '没有可用设备' }
        Capture-Log -Device $d -Seconds $LogSeconds | Out-Null
    }
    'all' {
        $d = Show-Devices
        if (-not $d) {
            Write-Host ''
            Write-Host '先完成设备连接再跑 -Action all。' -ForegroundColor Yellow
            Show-WirelessSteps
            return
        }
        $apks = Build-Apks
        Install-Apks -Device $d -Apks $apks
        Run-Tests -Device $d | Out-Null
        Capture-Log -Device $d -Seconds $LogSeconds | Out-Null
    }
}

Write-Host ''
Write-Host '完成。' -ForegroundColor Green
