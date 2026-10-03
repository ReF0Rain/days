<#!
.SYNOPSIS
    查询 GitHub Actions 构建状态，跑完后给出 artifact 下载地址（Windows PowerShell）。

.DESCRIPTION
    通过公开的 GitHub REST API 查询工作流运行记录，不需要 token。
    可加 -Wait 轮询直到本次运行结束；结束时打印可直接下载的 artifact 链接。

.PARAMETER Repo
    仓库全名，格式 owner/name。默认 ReF0Rain/days。

.PARAMETER Wait
    轮询直到最新一次运行结束（最多 -TimeoutMinutes 分钟）。

.PARAMETER TimeoutMinutes
    -Wait 的最长等待时间，默认 25 分钟。

.EXAMPLE
    .\scripts\ci-status.ps1
    .\scripts\ci-status.ps1 -Wait
#>
[CmdletBinding()]
param(
    [string]$Repo = 'ReF0Rain/days',

    [switch]$Wait,

    [int]$TimeoutMinutes = 25
)

$ErrorActionPreference = 'Stop'
$headers = @{ 'User-Agent' = 'countdown-ci-status'; 'Accept' = 'application/vnd.github+json' }
$apiBase = "https://api.github.com/repos/$Repo"

function Get-LatestRun {
    $url = "$apiBase/actions/runs?per_page=1"
    $resp = Invoke-RestMethod -Uri $url -Headers $headers -TimeoutSec 45
    return $resp.workflow_runs | Select-Object -First 1
}

function Format-Elapsed {
    param($Start, $End)
    if (-not $End) { $End = (Get-Date).ToUniversalTime() }
    $span = $End - $Start
    return ('{0:mm\:ss}' -f $span)
}

Write-Host "仓库: $Repo" -ForegroundColor Cyan
$run = Get-LatestRun
if (-not $run) {
    Write-Host '还没有任何工作流运行记录。确认已 push 到默认分支。' -ForegroundColor Yellow
    return
}

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$lastStatus = ''

while ($true) {
    $run = Get-LatestRun
    $status = $run.status
    $conclusion = $run.conclusion
    $elapsed = Format-Elapsed -Start $run.run_started_at -End $run.updated_at

    $line = "  #$($run.run_number)  status=$status  conclusion=$conclusion  elapsed=$elapsed"
    if ($line -ne $lastStatus) {
        Write-Host $line
        $lastStatus = $line
    }

    if ($status -eq 'completed') { break }
    if (-not $Wait) {
        Write-Host "`n当前仍在进行中。加 -Wait 可轮询到结束，或直接打开：" -ForegroundColor Yellow
        Write-Host "  $($run.html_url)"
        return
    }
    if ((Get-Date) -gt $deadline) {
        Write-Host "`n等待超时（$TimeoutMinutes 分钟），仍在运行：" -ForegroundColor Yellow
        Write-Host "  $($run.html_url)"
        return
    }
    Start-Sleep -Seconds 15
}

Write-Host ''
if ($conclusion -eq 'success') {
    Write-Host '构建成功 ✓' -ForegroundColor Green
    $arts = Invoke-RestMethod -Uri "$apiBase/actions/runs/$($run.id)/artifacts" -Headers $headers -TimeoutSec 45
    if ($arts.artifacts.Count -eq 0) {
        Write-Host '没有生成 artifact。' -ForegroundColor Yellow
    }
    else {
        Write-Host '可下载的产物：' -ForegroundColor Green
        foreach ($a in $arts.artifacts) {
            $sizeMb = [math]::Round($a.size_in_bytes / 1MB, 2)
            Write-Host ("  {0,-24} {1,8} MB  下载: {2}" -f $a.name, $sizeMb, $a.archive_download_url)
        }
        Write-Host ''
        Write-Host '也可以在浏览器里下载（需要登录）：' -ForegroundColor Cyan
        Write-Host "  $($run.html_url)"
    }
}
else {
    Write-Host "构建未成功：$conclusion" -ForegroundColor Red
    Write-Host "查看日志：$($run.html_url)" -ForegroundColor Cyan
    Write-Host '把失败步骤的日志贴给 AI 即可拿到修复补丁。'
}
