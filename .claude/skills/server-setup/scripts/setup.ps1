<#
.SYNOPSIS
  Server 개발/실행 환경 setup 오케스트레이터.
  요구사항(docs/requirements.md → tools.manifest.json)에 정의된 도구/IntelliJ 설정을 설치·구성한다.
  이 환경은 Server 전용이므로 Client 도구는 다루지 않는다.

.PARAMETER Apply
  실제 설치/다운로드/파일생성을 수행한다. 미지정 시 기본은 dry-run(계획만 출력, 시스템 변경 없음).

.PARAMETER Only
  실행할 단계 번호만 지정(쉼표 구분). 예: -Only 01,03,05  또는  -Only 1,3,5

.EXAMPLE
  .\setup.ps1                    # dry-run: 무엇을 설치할지 미리보기
  .\setup.ps1 -Apply             # 전체 설치 수행
  .\setup.ps1 -Apply -Only 03,04,05
#>
[CmdletBinding()]
param(
    [switch]$Apply,
    [string]$Only = ''
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib.ps1"
$global:ServerSetupApply = [bool]$Apply
$global:ServerSetupResults = @()

# 실행할 단계 정의(순서 보장)
$steps = [ordered]@{
    '01' = '01-preflight.ps1'
    '02' = '02-jdk.ps1'
    '03' = '03-gradle.ps1'
    '04' = '04-project-skeleton.ps1'
    '05' = '05-mediamtx.ps1'
    '06' = '06-opencv.ps1'
    '07' = '07-intellij.ps1'
}

# -Only 파싱 (앞자리 0 정규화: 1 -> 01)
$selected = $null
if ($Only.Trim()) {
    $selected = $Only -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { '{0:D2}' -f [int]$_ }
}

Write-Host "server-setup" -ForegroundColor Magenta
Write-Host ("모드: {0}" -f ($(if ($global:ServerSetupApply) { 'APPLY (실제 설치)' } else { 'DRY-RUN (미리보기, --Apply 로 실제 실행)' }))) -ForegroundColor Magenta
Write-Host ("프로젝트 루트: {0}" -f $script:ProjectRoot)

foreach ($key in $steps.Keys) {
    if ($selected -and ($selected -notcontains $key)) { continue }
    $file = Join-Path $PSScriptRoot $steps[$key]
    if (-not (Test-Path $file)) { Write-Warn "단계 $key 스크립트 없음: $file"; continue }
    try {
        & $file
    } catch {
        Write-Err "단계 $key 실행 중 오류: $_"
        Add-Result "step $key" 'failed' "$_"
        # 중단 없이 다음 단계 진행
    }
}

# ---- 최종 요약 ----
Write-Step "요약"
if ($global:ServerSetupResults.Count -eq 0) {
    Write-Info "수집된 결과 없음"
} else {
    $global:ServerSetupResults | Format-Table -AutoSize | Out-String | Write-Host
    $failed = @($global:ServerSetupResults | Where-Object { $_.Status -eq 'failed' })
    if ($failed.Count -gt 0) {
        Write-Warn ("실패 {0}건 — 위 표의 [failed] 항목 확인" -f $failed.Count)
    }
}
if (-not $global:ServerSetupApply) {
    Write-Host "`n실제로 설치하려면: .\setup.ps1 -Apply" -ForegroundColor Yellow
}
