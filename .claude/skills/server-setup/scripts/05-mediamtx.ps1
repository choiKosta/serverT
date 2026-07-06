# 05-mediamtx.ps1 — MediaMTX(RTSP Server) 다운로드 + 인터페이스 계약 반영 설정
. "$PSScriptRoot\lib.ps1"
Write-Step "05 MediaMTX (RTSP Server)"

$root = $script:ProjectRoot
$m = Get-Manifest
$mtx = $m.tools | Where-Object { $_.id -eq 'mediamtx' }
$installDir = Join-Path $root ($mtx.installDir -replace '/', '\')
$exe = Join-Path $installDir 'mediamtx.exe'
$port = $mtx.rtsp.port
$path = ($mtx.rtsp.path -replace '^/', '')   # "camera"

# mediamtx.yml — RTSP 포트 8554, 경로 /camera (요구사항 Interface URL 고정 규칙)
$ymlPath = Join-Path $installDir 'mediamtx.yml'
$yml = @"
# MediaMTX 설정 - docs/requirements.md 인터페이스 계약 반영
# 접속 URL: rtsp://<server-ip>:$port/$path
rtspAddress: :$port
paths:
  ${path}:
    # 카메라 소스는 서버 구현에서 publish 하거나 아래에 source 지정
    source: publisher
"@

if (Test-Path $exe) {
    Write-Skip "MediaMTX 이미 존재: $exe"
    Add-Result 'MediaMTX' 'skipped' '이미 존재'
} else {
    if (-not $global:ServerSetupApply) {
        Write-Plan "GitHub 최신 릴리스에서 $($mtx.assetPattern) 다운로드 -> $installDir"
        Add-Result 'MediaMTX' 'planned' "download -> $installDir"
    } else {
        try {
            Write-Info "MediaMTX 최신 릴리스 조회 중..."
            $rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$($mtx.githubRepo)/releases/latest" -Headers @{ 'User-Agent' = 'server-setup' } -UseBasicParsing
            $asset = $rel.assets | Where-Object { $_.name -match $mtx.assetPattern } | Select-Object -First 1
            if (-not $asset) { throw "자산 패턴 매칭 실패: $($mtx.assetPattern)" }
            $zip = Join-Path $env:TEMP $asset.name
            if (Invoke-Download -Url $asset.browser_download_url -OutFile $zip) {
                if (-not (Test-Path $installDir)) { New-Item -ItemType Directory -Force -Path $installDir | Out-Null }
                Expand-Archive -Path $zip -DestinationPath $installDir -Force
                Remove-Item $zip -ErrorAction SilentlyContinue
                Write-Ok "MediaMTX 설치: $installDir ($($rel.tag_name))"
                Add-Result 'MediaMTX' 'installed' $rel.tag_name
            } else {
                Write-Err "MediaMTX 다운로드 실패"; Add-Result 'MediaMTX' 'failed' 'download 실패'
            }
        } catch {
            Write-Err "MediaMTX 설치 오류: $_"; Add-Result 'MediaMTX' 'failed' "$_"
        }
    }
}

# 설정 파일은 항상 계약과 일치하도록 생성(없을 때만; 있으면 보존)
if (Test-Path $ymlPath) {
    Write-Skip "mediamtx.yml 이미 존재 (보존)"
} elseif (-not $global:ServerSetupApply) {
    Write-Plan "생성: $ymlPath (rtsp :$port, path /$path)"
} else {
    if (-not (Test-Path $installDir)) { New-Item -ItemType Directory -Force -Path $installDir | Out-Null }
    Set-Content -Path $ymlPath -Value $yml -Encoding UTF8
    Write-Ok "mediamtx.yml 생성 (rtsp://<server-ip>:$port/$path)"
}
