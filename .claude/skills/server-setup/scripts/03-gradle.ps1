# 03-gradle.ps1 — Gradle 준비 (Wrapper 우선, 시스템 설치는 옵션)
. "$PSScriptRoot\lib.ps1"
Write-Step "03 Gradle (Wrapper 우선)"

$root = $script:ProjectRoot
$wrapperScript = Join-Path $root 'gradlew.bat'
$wrapperJar    = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'

if (Test-Path $wrapperScript) {
    Write-Skip "Gradle Wrapper 이미 존재 (gradlew.bat)"
    Add-Result 'Gradle Wrapper' 'skipped' '이미 존재'
    return
}

if (Test-HasCommand 'gradle') {
    # 시스템 gradle 로 wrapper 생성
    if (-not $global:ServerSetupApply) {
        Write-Plan "gradle wrapper --gradle-version 8.7  (프로젝트에 Wrapper 생성)"
        Add-Result 'Gradle Wrapper' 'planned' 'gradle wrapper'
        return
    }
    Write-Info "gradle wrapper 생성 중..."
    Push-Location $root
    try {
        gradle wrapper --gradle-version 8.7 | Out-Null
        if (Test-Path $wrapperScript) { Write-Ok "Gradle Wrapper 생성 완료"; Add-Result 'Gradle Wrapper' 'installed' 'gradle wrapper' }
        else { Write-Err "Wrapper 생성 확인 실패"; Add-Result 'Gradle Wrapper' 'failed' 'gradlew.bat 없음' }
    } finally { Pop-Location }
    return
}

# 시스템 gradle 없음: 04(project-skeleton)에서 wrapper properties 를 시드하거나 winget 설치를 안내
$m = Get-Manifest
$gradleWingetId = ($m.tools | Where-Object { $_.id -eq 'gradle' }).wingetId
if (-not $global:ServerSetupApply) {
    Write-Plan "gradle 미설치 — 옵션1: winget install --id $gradleWingetId  / 옵션2: 04 단계에서 Wrapper properties 시드(권장)"
    Write-Plan "권장: 04-project-skeleton.ps1 이 wrapper properties 를 시드하고 IntelliJ Gradle import 시 배포판을 내려받으므로 별도 gradle 설치 불필요"
    Add-Result 'Gradle Wrapper' 'planned' '04 단계에서 Wrapper 시드'
    return
}
Write-Warn "시스템 gradle 이 없어 wrapper 를 생성할 수 없음. 04-project-skeleton.ps1 이 Wrapper 파일을 시드한다."
Add-Result 'Gradle Wrapper' 'skipped' '04 단계로 위임'
