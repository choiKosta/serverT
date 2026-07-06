# 06-opencv.ps1 — OpenCV (Java 바인딩) 준비
# 기본 전략: Gradle 의존성(org.openpnp:opencv)으로 제공 → 네이티브 라이브러리 자체 포함.
# 04-project-skeleton.ps1 의 build.gradle 에 의존성이 포함되므로 여기서는 점검/안내만 수행한다.
. "$PSScriptRoot\lib.ps1"
Write-Step "06 OpenCV (Java 바인딩)"

$root = $script:ProjectRoot
$m = Get-Manifest
$dep = ($m.tools | Where-Object { $_.id -eq 'opencv' }).gradleDependency
$build = Join-Path $root 'build.gradle'

if (Test-Path $build) {
    $content = Get-Content $build -Raw
    if ($content -match [regex]::Escape('org.openpnp:opencv')) {
        Write-Ok "build.gradle 에 OpenCV 의존성 존재: $dep"
        Add-Result 'OpenCV' 'skipped' 'build.gradle 의존성 확인'
    } else {
        Write-Warn "build.gradle 에 OpenCV 의존성이 없음 — implementation '$dep' 추가 필요"
        Add-Result 'OpenCV' 'planned' "build.gradle 에 $dep 추가"
    }
} else {
    Write-Info "build.gradle 아직 없음 — 04 단계 실행 시 OpenCV 의존성($dep) 포함됨"
    Add-Result 'OpenCV' 'planned' '04 단계에서 의존성 포함'
}

Write-Info "org.openpnp:opencv 는 네이티브 라이브러리를 JAR 에 포함하여 별도 시스템 설치가 불필요합니다."
Write-Info "OpenCV 초기화 예: nu.pattern.OpenCV.loadShared();  (또는 loadLocally())"
