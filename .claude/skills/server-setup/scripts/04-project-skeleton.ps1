# 04-project-skeleton.ps1 — Gradle/Spring Boot 프로젝트 골격 생성 (멱등: 기존 파일 보존)
. "$PSScriptRoot\lib.ps1"
Write-Step "04 프로젝트 골격 (build.gradle / 소스 / Wrapper properties)"

$root = $script:ProjectRoot
$m = Get-Manifest
$opencvDep = ($m.tools | Where-Object { $_.id -eq 'opencv' }).gradleDependency

function New-FileIfMissing {
    param([string]$Path, [string]$Content, [string]$Label)
    if (Test-Path $Path) { Write-Skip "$Label 이미 존재"; Add-Result $Label 'skipped' '이미 존재'; return }
    if (-not $global:ServerSetupApply) { Write-Plan "생성: $Path"; Add-Result $Label 'planned' $Path; return }
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Set-Content -Path $Path -Value $Content -Encoding UTF8
    Write-Ok "$Label 생성"; Add-Result $Label 'installed' $Path
}

# settings.gradle
New-FileIfMissing -Path (Join-Path $root 'settings.gradle') -Label 'settings.gradle' -Content @"
rootProject.name = 'serverT'
"@

# build.gradle (Spring Boot + JUnit5 + OpenCV)
New-FileIfMissing -Path (Join-Path $root 'build.gradle') -Label 'build.gradle' -Content @"
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.2'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.servert'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // OpenCV (Java 바인딩) - 요구사항: Vision Library OpenCV
    implementation '$opencvDep'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    // JUnit 5 - 요구사항: Test JUnit
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

test { useJUnitPlatform() }
"@

# gradle-wrapper.properties (IntelliJ 가 이 버전으로 Gradle 을 내려받아 사용)
New-FileIfMissing -Path (Join-Path $root 'gradle\wrapper\gradle-wrapper.properties') -Label 'gradle-wrapper.properties' -Content @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@

# 메인 애플리케이션 클래스
New-FileIfMissing -Path (Join-Path $root 'src\main\java\com\servert\ServerApplication.java') -Label 'ServerApplication.java' -Content @"
package com.servert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
"@

# 기본 테스트
New-FileIfMissing -Path (Join-Path $root 'src\test\java\com\servert\ServerApplicationTests.java') -Label 'ServerApplicationTests.java' -Content @"
package com.servert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApplicationTests {
    @Test
    void contextPlaceholder() {
        assertTrue(true);
    }
}
"@

# application.properties (RTSP 인터페이스 규격 메모)
New-FileIfMissing -Path (Join-Path $root 'src\main\resources\application.properties') -Label 'application.properties' -Content @"
# Server 인터페이스 계약 (docs/requirements.md)
# RTSP: rtsp://<server-ip>:8554/camera  (MediaMTX)
server.port=8080
"@

if (-not $global:ServerSetupApply) {
    Write-Plan "참고: gradlew.bat/gradlew(Wrapper 실행 스크립트)는 gradle 설치 후 'gradle wrapper' 또는 IntelliJ Gradle import 시 자동 생성됨"
}
