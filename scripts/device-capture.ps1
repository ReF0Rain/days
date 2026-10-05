<#!
.SYNOPSIS
    抓当前屏幕：截图 + View 层级 + 文字排版分析。

.DESCRIPTION
    排查排版/格式问题专用。除了截图，还会从 uiautomator 的层级里把每段文字的
    实际坐标与宽高提取出来，这样能判断：
      - 文字是否超出屏幕边界（被裁切）
      - 元素宽度是否异常小（被挤压换行）
      - 元素是否零尺寸（没渲染出来）
      - 同一行多个元素的水平间距是否合理

.PARAMETER Serial
    设备序列号，默认用唯一在线设备。

.PARAMETER Note
    这次抓取的名字，用于区分多次抓取。

.EXAMPLE
    .\scripts\device-capture.ps1 -Note "全部事件"
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Note = "capture"
)

# 注意：不能用 'Stop'。adb 会把正常进度信息写到 stderr（例如
# "1 file pulled, 0 skipped"），在 Stop 模式下会被当成致命错误终止脚本。
# 改为显式检查 $LASTEXITCODE。
$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:TEMP 'android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }

# ---------- 选设备 ----------
if (-not $Serial) {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }
    $ready = @()
    foreach ($line in $lines) {
        $parts = $line -split '\s+'
        if ($parts.Count -ge 2 -and $parts[1] -eq 'device') { $ready += $parts[0] }
    }
    if ($ready.Count -eq 0) { throw '没有可用设备' }
    if ($ready.Count -gt 1) { throw "多个设备，请用 -Serial 指定：$($ready -join ', ')" }
    $Serial = $ready[0]
}
Write-Host "设备: $Serial" -ForegroundColor Green

# ---------- 输出目录 ----------
$stamp = Get-Date -Format 'HHmmss'
$safe = ($Note -replace '[^\w\u4e00-\u9fa5-]', '_')
$outDir = Join-Path $root "dist\device\$stamp-$safe"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "输出: $outDir" -ForegroundColor DarkGray

# ---------- 屏幕参数 ----------
$wmSize = (& $adb -s $Serial shell wm size | Out-String).Trim()
$wmDensity = (& $adb -s $Serial shell wm density | Out-String).Trim()
$fontScale = (& $adb -s $Serial shell settings get system font_scale | Out-String).Trim()
$screenW = 0; $screenH = 0
if ($wmSize -match '(\d+)x(\d+)') { $screenW = [int]$Matches[1]; $screenH = [int]$Matches[2] }
Write-Host "屏幕: $screenW x $screenH, $wmDensity, font_scale=$fontScale" -ForegroundColor DarkGray

# ---------- 截图 ----------
$shot = Join-Path $outDir 'screen.png'
& $adb -s $Serial exec-out screencap -p > $shot
if (Test-Path $shot) { Write-Host "截图: $shot ($([math]::Round((Get-Item $shot).Length/1KB))KB)" -ForegroundColor Cyan }

# ---------- View 层级 ----------
# 注意：adb pull 在 Windows 上无法写入含非 ASCII 字符的路径（本项目在「新建文件夹」下），
# 所以先拉到纯 ASCII 的临时目录，再复制到目标位置。
$remote = '/sdcard/dsh_ui.xml'
& $adb -s $Serial shell uiautomator dump $remote | Out-Null
$tmpXml = Join-Path $env:TEMP 'dsh_ui_dump.xml'
Remove-Item -Force $tmpXml -ErrorAction SilentlyContinue
& $adb -s $Serial pull $remote $tmpXml | Out-Null
& $adb -s $Serial shell rm -f $remote | Out-Null
$localXml = Join-Path $outDir 'hierarchy.xml'
if (Test-Path $tmpXml) { Copy-Item $tmpXml $localXml -Force }
else { throw '没能取到 View 层级（uiautomator dump 失败？）' }

# ---------- 提取文字 + 边界 ----------
$xmlText = Get-Content $localXml -Raw
$nodes = [regex]::Matches($xmlText, '<node[^>]*?/?>') | ForEach-Object {
    $tag = $_.Value
    $text = [regex]::Match($tag, 'text="([^"]*)"').Groups[1].Value
    $desc = [regex]::Match($tag, 'content-desc="([^"]*)"').Groups[1].Value
    $cls  = [regex]::Match($tag, 'class="([^"]*)"').Groups[1].Value
    $b = [regex]::Match($tag, 'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')
    if (-not $b.Success) { return }
    $x1=[int]$b.Groups[1].Value; $y1=[int]$b.Groups[2].Value
    $x2=[int]$b.Groups[3].Value; $y2=[int]$b.Groups[4].Value
    $w = $x2-$x1; $h = $y2-$y1
    if ($w -le 0 -or $h -le 0) { return }
    [pscustomobject]@{
        文字 = if ($text) { $text } elseif ($desc) { "($desc)" } else { '' }
        类   = ($cls -split '\.')[-1]
        左 = $x1; 上 = $y1; 右 = $x2; 下 = $y2; 宽 = $w; 高 = $h
    }
} | Where-Object { $_.文字 -ne '' }

$layoutFile = Join-Path $outDir 'text-layout.txt'
$nodes | Sort-Object 上, 左 | Format-Table -AutoSize | Out-String -Width 220 |
    Set-Content -Path $layoutFile -Encoding UTF8
Write-Host "文字排版: $layoutFile" -ForegroundColor Cyan

Write-Host ''
Write-Host '=== 屏幕上的文字与位置（按从上到下、从左到右）===' -ForegroundColor Yellow
$nodes | Sort-Object 上, 左 | Format-Table -AutoSize | Out-String -Width 220 | Write-Host

# ---------- 排版异常自查 ----------
Write-Host '=== 排版异常自查 ===' -ForegroundColor Yellow
$problems = 0

$overflow = $nodes | Where-Object { $screenW -gt 0 -and ($_.左 -lt 0 -or $_.右 -gt $screenW) }
if ($overflow) {
    $problems++
    Write-Host '  [越界] 文字超出屏幕左右边界：' -ForegroundColor Red
    $overflow | ForEach-Object { Write-Host ("    {0}  左={1} 右={2} (屏宽 {3})" -f $_.文字, $_.左, $_.右, $screenW) }
}

$narrow = $nodes | Where-Object { $_.宽 -lt 24 -and $_.文字.Length -gt 1 }
if ($narrow) {
    $problems++
    Write-Host '  [挤压] 文字元素宽度很小（很可能被换行或截断）：' -ForegroundColor Red
    $narrow | ForEach-Object { Write-Host ("    {0}  宽={1}" -f $_.文字, $_.宽) }
}

# 同一水平行（垂直中心接近）的文字，检查是否有重叠。
# 注意：必须排除"铺满全屏的父容器"（它们的 content-desc 往往是一句话），
# 否则会误报一堆重叠 —— 父容器本来就覆盖所有子元素。
$screenArea = $screenW * $screenH
$childNodes = $nodes | Where-Object {
    -not ($screenArea -gt 0 -and ($_.宽 * $_.高) -gt ($screenArea * 0.8))
}
$sorted = $childNodes | Sort-Object 上, 左
for ($i = 0; $i -lt $sorted.Count - 1; $i++) {
    for ($j = $i + 1; $j -lt $sorted.Count; $j++) {
        $a = $sorted[$i]; $b = $sorted[$j]
        if ($a.文字 -eq $b.文字) { continue }
        $vOverlap = [Math]::Min($a.下, $b.下) - [Math]::Max($a.上, $b.上)
        $hOverlap = [Math]::Min($a.右, $b.右) - [Math]::Max($a.左, $b.左)
        if ($vOverlap -gt ([Math]::Min($a.高, $b.高) * 0.5) -and $hOverlap -gt 8) {
            $problems++
            Write-Host ("  [重叠] 「{0}」与「{1}」水平重叠 {2}px" -f $a.文字, $b.文字, $hOverlap) -ForegroundColor Red
        }
    }
}

# 右边距过小（容易被认为"挤在一起"），同样排除全屏容器
$tight = $childNodes | Where-Object { $screenW -gt 0 -and $_.右 -gt ($screenW - 8) }
if ($tight) {
    Write-Host '  [贴边] 右边缘距屏幕不足 8px：' -ForegroundColor Yellow
    $tight | ForEach-Object { Write-Host ("    {0}  右={1} (屏宽 {2})" -f $_.文字, $_.右, $screenW) }
}

if ($problems -eq 0) {
    Write-Host '  未发现越界/挤压/重叠（自查看不出问题，需要结合截图判断）' -ForegroundColor Green
}

Write-Host ''
Write-Host "产物目录: $outDir" -ForegroundColor Green
