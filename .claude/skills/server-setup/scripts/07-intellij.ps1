# 07-intellij.ps1 — IntelliJ 플러그인/설정
#  (1) .idea/externalDependencies.xml 에 필요 플러그인 선언 (IDE 가 열 때 설치 안내, git 공유)
#  (2) idea CLI 로 installPlugins best-effort 시도 (가능 시)
. "$PSScriptRoot\lib.ps1"
Write-Step "07 IntelliJ 플러그인/설정"

$root = $script:ProjectRoot
$m = Get-Manifest
# 선택(optional) 플러그인은 필수 선언에서 제외 — IntelliJ 가 "필수 플러그인 미설치" 알림을 띄우지 않도록.
$plugins  = @($m.intellij.plugins | Where-Object { -not $_.optional })
$optional = @($m.intellij.plugins | Where-Object { $_.optional })

# ---- (1) externalDependencies.xml ----
$xmlPath = Join-Path $root '.idea\externalDependencies.xml'
$pluginLines = ($plugins | ForEach-Object { "      <plugin id=`"$($_.id)`" />" }) -join "`n"
$xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ExternalDependencies">
$pluginLines
  </component>
</project>
"@

if (Test-Path $xmlPath) {
    if (-not $global:ServerSetupApply) {
        Write-Plan "갱신: $xmlPath (플러그인 $($plugins.Count)개 선언)"
        Add-Result 'IntelliJ externalDependencies' 'planned' '갱신'
    } else {
        Set-Content -Path $xmlPath -Value $xml -Encoding UTF8
        Write-Ok "externalDependencies.xml 갱신 (플러그인 $($plugins.Count)개)"
        Add-Result 'IntelliJ externalDependencies' 'installed' '갱신'
    }
} else {
    if (-not $global:ServerSetupApply) {
        Write-Plan "생성: $xmlPath (플러그인 $($plugins.Count)개 선언)"
        Add-Result 'IntelliJ externalDependencies' 'planned' '생성'
    } else {
        Set-Content -Path $xmlPath -Value $xml -Encoding UTF8
        Write-Ok "externalDependencies.xml 생성 (플러그인 $($plugins.Count)개)"
        Add-Result 'IntelliJ externalDependencies' 'installed' '생성'
    }
}
Write-Info "선언된 플러그인(필수): $(( $plugins | ForEach-Object { $_.name }) -join ', ')"
if ($optional.Count -gt 0) {
    Write-Info "선택 플러그인(필수 선언 제외): $(( $optional | ForEach-Object { $_.name }) -join ', ') — 필요 시 IDE 에서 직접 설치"
}
Write-Warn "Spring/Spring Boot 플러그인은 IntelliJ Ultimate 전용입니다(Community 는 미지원)."

# ---- (2) idea CLI installPlugins best-effort ----
$ideaBin = $null
$candidates = @()
if ($m.intellij.installPath) { $candidates += (Join-Path $m.intellij.installPath 'bin') }
$candidates += (Get-ChildItem 'C:\Program Files\JetBrains\IntelliJ IDEA*\bin' -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
foreach ($b in $candidates) {
    foreach ($name in @('idea64.exe','idea.bat','idea.exe')) {
        $p = Join-Path $b $name
        if (Test-Path $p) { $ideaBin = $p; break }
    }
    if ($ideaBin) { break }
}

if (-not $ideaBin) {
    Write-Info "idea 실행 파일을 찾지 못함 — externalDependencies.xml 선언으로 IDE 열 때 설치 안내됩니다."
    return
}

$ids = ($plugins | ForEach-Object { $_.id }) -join ' '
if (-not $global:ServerSetupApply) {
    Write-Plan "idea installPlugins $ids  (best-effort)"
    Add-Result 'IntelliJ installPlugins' 'planned' $ids
    return
}
try {
    Write-Info "idea installPlugins 시도: $ideaBin"
    & $ideaBin installPlugins @($plugins | ForEach-Object { $_.id })
    Write-Ok "idea installPlugins 명령 실행 (결과는 IDE 로그 확인)"
    Add-Result 'IntelliJ installPlugins' 'installed' $ids
} catch {
    Write-Warn "idea installPlugins 실패($_) — externalDependencies.xml 로 폴백(이미 생성됨)"
    Add-Result 'IntelliJ installPlugins' 'failed' "$_ (xml 폴백)"
}
