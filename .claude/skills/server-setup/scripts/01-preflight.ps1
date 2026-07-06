# 01-preflight.ps1 — 환경/기존 도구 탐지 및 리포트 (변경 없음)
. "$PSScriptRoot\lib.ps1"
Write-Step "01 Preflight — 환경 점검"

# OS
$os = (Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue).Caption
Write-Info ("OS: {0}" -f ($(if ($os) { $os } else { $env:OS })))

# 패키지 매니저
if (Test-HasCommand 'winget') { Write-Ok  "winget 사용 가능" } else { Write-Warn "winget 없음 — 자동 설치 제한됨" }

# 핵심 도구 탐지
$checks = @(
    @{ n = 'java';   c = 'java';   v = 'java -version'   },
    @{ n = 'javac';  c = 'javac';  v = 'javac -version'  },
    @{ n = 'gradle'; c = 'gradle'; v = 'gradle -v'       },
    @{ n = 'git';    c = 'git';    v = 'git --version'   },
    @{ n = 'docker'; c = 'docker'; v = 'docker --version'}
)
foreach ($chk in $checks) {
    if (Test-HasCommand $chk.c) {
        $src = (Get-Command $chk.c).Source
        Write-Ok ("{0}: {1}" -f $chk.n, $src)
    } else {
        Write-Info ("{0}: 미설치" -f $chk.n)
    }
}

# IntelliJ 설치 경로 탐지
$m = Get-Manifest
$ideaPath = $m.intellij.installPath
$ideaGlob = "C:\Program Files\JetBrains\IntelliJ IDEA*"
$found = @(Get-ChildItem -Path $ideaGlob -Directory -ErrorAction SilentlyContinue)
if (Test-Path $ideaPath) {
    Write-Ok ("IntelliJ IDEA: {0}" -f $ideaPath)
} elseif ($found.Count -gt 0) {
    Write-Ok ("IntelliJ IDEA: {0}" -f $found[0].FullName)
} else {
    Write-Warn "IntelliJ IDEA 설치 경로를 찾지 못함 (07 단계는 설정 파일만 생성)"
}

Add-Result 'preflight' 'skipped' '점검 전용'
