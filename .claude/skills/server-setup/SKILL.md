---
name: server-setup
description: 요구사항(docs/requirements.md)에 정의된 Server 개발/실행 도구(JDK17, Gradle, Spring Boot, MediaMTX, OpenCV, JUnit)와 IntelliJ 플러그인/설정을 설치·구성한다. 새 머신에서 서버 환경을 재현하거나 도구 설치/점검이 필요할 때 사용. 이 환경은 Server 전용이므로 Client 도구는 다루지 않는다.
---

# server-setup

Server 개발/실행 환경을 요구사항에 맞춰 설치·구성하는 스킬. 모든 절차는 PowerShell 스크립트로 구현되어 있으며, `tools.manifest.json`(요구사항에서 파생한 단일 출처)을 참조한다.

## 단일 출처
- `tools.manifest.json` — 설치할 도구/버전/IntelliJ 플러그인 목록. **요구사항이 바뀌면 hook이 이 파일을 재생성**하고 스크립트가 이를 읽는다.

## 실행 방법 (Windows PowerShell)

```powershell
# 1) 미리보기(dry-run, 기본) — 무엇을 설치/생성할지 출력, 시스템 변경 없음
.\.claude\skills\server-setup\scripts\setup.ps1

# 2) 실제 설치/구성
.\.claude\skills\server-setup\scripts\setup.ps1 -Apply

# 3) 특정 단계만
.\.claude\skills\server-setup\scripts\setup.ps1 -Apply -Only 03,04,05
```

- 기본은 **dry-run**. `-Apply` 를 줘야 실제 winget 설치/다운로드/파일 생성을 수행한다.
- 모든 단계는 **멱등** — 이미 설치/존재하면 건너뛴다. 재실행 안전.
- 한 단계가 실패해도 오케스트레이터는 중단 없이 다음 단계로 진행하고, 마지막에 결과 표로 요약한다.

## 단계 구성 (scripts/)
| # | 스크립트 | 역할 |
|---|----------|------|
| — | `lib.ps1` | 공통 헬퍼(로깅, 도구 탐지, winget/다운로드 dry-run·apply 분기, manifest 로드) |
| — | `setup.ps1` | 오케스트레이터(`-Apply`/`-Only`, 최종 요약) |
| 01 | `01-preflight.ps1` | OS/winget/기존 도구/IntelliJ 경로 탐지 리포트 |
| 02 | `02-jdk.ps1` | OpenJDK 17 점검, 없으면 winget 설치 |
| 03 | `03-gradle.ps1` | Gradle Wrapper 준비(시스템 gradle 있으면 생성, 없으면 04로 위임) |
| 04 | `04-project-skeleton.ps1` | build.gradle(Spring Boot/JUnit/OpenCV)·settings.gradle·소스 골격·wrapper properties 생성(기존 보존) |
| 05 | `05-mediamtx.ps1` | MediaMTX 다운로드 + `mediamtx.yml`(RTSP :8554, /camera — 인터페이스 계약) |
| 06 | `06-opencv.ps1` | OpenCV Java 바인딩(Gradle 의존성) 점검/안내 |
| 07 | `07-intellij.ps1` | `.idea/externalDependencies.xml` 플러그인 선언 + `idea installPlugins` best-effort |

## 주의
- 실제 설치는 사용자 승인 후 `-Apply` 로만 수행한다(무단 설치 금지).
- IntelliJ Spring/Spring Boot 플러그인은 **Ultimate 전용**. Community 에서는 externalDependencies.xml 선언만 남고 설치되지 않는다.
- 인터페이스 규격(RTSP `8554`/`camera`, H.264 등)은 Client와의 계약이므로 스크립트가 임의 변경하지 않는다.

## 재정의(요구사항 변경 시)
`docs/requirements.md` 가 수정되면 PostToolUse hook(`.claude/hooks/on-requirements-change.js`)이 `tools.manifest.json`·스크립트·`server-setup` 에이전트·CLAUDE.md 재정의를 지시한다.
