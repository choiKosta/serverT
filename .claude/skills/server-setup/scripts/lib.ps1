# lib.ps1 — server-setup 스크립트 공통 헬퍼
# dot-source 방식으로 각 단계 스크립트에서 로드: . "$PSScriptRoot\lib.ps1"
#
# 스코프 주의: setup.ps1 은 각 단계를 자식 스코프(& file)로 실행하므로,
# 단계 간에 공유되어야 하는 상태(Apply 플래그, 결과 수집)는 $global: 로 둔다.
# $script:ProjectRoot 는 dot-source 시 각 스코프에서 재계산되어 동일 값이므로 그대로 사용.

# ---- 공유 상태 (전역) ----
if (-not (Test-Path variable:global:ServerSetupApply))   { $global:ServerSetupApply = $false }
if (-not (Test-Path variable:global:ServerSetupResults)) { $global:ServerSetupResults = @() }

# 프로젝트 루트: scripts -> server-setup -> skills -> .claude -> <root>
$script:ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path

# ---- 로깅 ----
function Write-Step   { param([string]$m) Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Write-Info   { param([string]$m) Write-Host "  $m" }
function Write-Ok     { param([string]$m) Write-Host "  [OK]   $m" -ForegroundColor Green }
function Write-Skip   { param([string]$m) Write-Host "  [SKIP] $m" -ForegroundColor DarkGray }
function Write-Plan   { param([string]$m) Write-Host "  [PLAN] $m" -ForegroundColor Yellow }
function Write-Warn   { param([string]$m) Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Write-Err    { param([string]$m) Write-Host "  [FAIL] $m" -ForegroundColor Red }

# ---- 단계 결과 수집 (setup.ps1 요약용) ----
function Add-Result {
    param([string]$Tool, [ValidateSet('installed','skipped','planned','failed')][string]$Status, [string]$Detail = '')
    $global:ServerSetupResults += [pscustomobject]@{ Tool = $Tool; Status = $Status; Detail = $Detail }
}

# ---- 도구 탐지 ----
function Test-HasCommand {
    param([Parameter(Mandatory)][string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

# ---- winget 설치 (dry-run/apply 분기, 멱등) ----
function Install-WithWinget {
    param(
        [Parameter(Mandatory)][string]$WingetId,
        [Parameter(Mandatory)][string]$DisplayName,
        [string]$VerifyCommand
    )
    if ($VerifyCommand) {
        $probe = ($VerifyCommand -split '\s+')[0]
        if (Test-HasCommand $probe) {
            Write-Skip "$DisplayName 이미 설치됨 ($probe 발견)"
            Add-Result $DisplayName 'skipped' '이미 설치됨'
            return $true
        }
    }
    if (-not (Test-HasCommand 'winget')) {
        Write-Err "winget 이 없어 $DisplayName 를 설치할 수 없음"
        Add-Result $DisplayName 'failed' 'winget 없음'
        return $false
    }
    if (-not $global:ServerSetupApply) {
        Write-Plan "winget install --id $WingetId  ($DisplayName)"
        Add-Result $DisplayName 'planned' "winget: $WingetId"
        return $true
    }
    Write-Info "winget 로 $DisplayName 설치 중..."
    try {
        winget install --id $WingetId --exact --silent --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -eq 0) { Write-Ok "$DisplayName 설치 완료"; Add-Result $DisplayName 'installed' $WingetId; return $true }
        Write-Err "$DisplayName 설치 실패 (exit $LASTEXITCODE)"; Add-Result $DisplayName 'failed' "winget exit $LASTEXITCODE"; return $false
    } catch {
        Write-Err "$DisplayName 설치 예외: $_"; Add-Result $DisplayName 'failed' "$_"; return $false
    }
}

# ---- 파일 다운로드 (dry-run/apply 분기) ----
function Invoke-Download {
    param([Parameter(Mandatory)][string]$Url, [Parameter(Mandatory)][string]$OutFile)
    if (-not $global:ServerSetupApply) { Write-Plan "다운로드: $Url -> $OutFile"; return $false }
    $dir = Split-Path -Parent $OutFile
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Write-Info "다운로드 중: $Url"
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing
    return (Test-Path $OutFile)
}

# ---- manifest 로드 ----
function Get-Manifest {
    $p = Join-Path $PSScriptRoot "..\tools.manifest.json"
    if (-not (Test-Path $p)) { throw "manifest 없음: $p" }
    return Get-Content $p -Raw | ConvertFrom-Json
}
