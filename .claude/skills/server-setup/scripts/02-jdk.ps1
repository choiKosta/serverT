# 02-jdk.ps1 — OpenJDK 17 점검/설치
. "$PSScriptRoot\lib.ps1"
Write-Step "02 JDK 17"

$m = Get-Manifest
$jdk = $m.tools | Where-Object { $_.id -eq 'jdk' }

if (Test-HasCommand 'java') {
    $ver = (& java -version 2>&1 | Select-Object -First 1)
    if ($ver -match '"?(\d+)(\.|")') {
        $major = [int]$Matches[1]
        if ($major -ge 17) {
            Write-Skip ("JDK {0} 이미 설치됨 — {1}" -f $major, $ver)
            Add-Result 'OpenJDK 17' 'skipped' $ver
            return
        }
        Write-Warn ("설치된 Java 버전이 17 미만({0}) — JDK 17 설치 진행" -f $major)
    }
}
Install-WithWinget -WingetId $jdk.wingetId -DisplayName $jdk.name -VerifyCommand $jdk.verifyCommand | Out-Null
