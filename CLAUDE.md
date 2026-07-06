# CLAUDE.md — 프로젝트 공통 규칙 / 원칙 / 템플릿

> 이 파일은 세션 시작 시점부터 항상 로드됩니다. 아래 규칙을 기본 전제로 작업하세요.
> 상세 요구사항은 [docs/requirements.md](docs/requirements.md) 참조.

---

## 0. 이 환경의 개발 범위 (가장 중요)

- **이 환경(`serverT`)에서는 Server 만 개발한다.**
  - Client(Ubuntu/WSL2, C++/QT)는 **이 환경의 개발 대상이 아니다.** 별도 환경에서 개발됨.
  - Client 관련 코드/빌드/실행은 여기서 다루지 않는다. 단, **Server가 Client와 맞춰야 하는 인터페이스 규격(RTSP/H.264, Control 채널, URL 규칙 등)은 반드시 준수**한다.
  - Web Client는 Server가 제공하는 Web UI 범위 내에서 다룬다.
- 요청이 Client 구현을 요구하는 것으로 보이면, 진행 전에 "이 환경은 Server 전용"임을 확인하고 인터페이스 관점으로 재해석한다.

---

## 1. Server 기술 스택 (고정)

| 항목 | 값 |
|------|-----|
| 실행 OS | Windows |
| 개발 언어 | Java |
| Framework | Spring Boot |
| Build Tool | Gradle |
| 스트리밍 | MediaMTX (RTSP Server) |
| Server Interface | RTSP / H.264 |
| Vision Library | OpenCV |
| UI | Web (Web UI) |
| Test | JUnit |
| IDE | IntelliJ / JetBrains |

- 위 스택을 벗어나는 대안(예: Maven, 다른 언어/프레임워크)을 도입하기 전에 반드시 사용자 확인을 받는다.

---

## 2. Server 기능 요구사항 (요약)

- 클라이언트로부터 제어 명령 수신 및 수행
  - 접속 / 접속 해제
  - 화면 제어 (Frame Rate / Resolution)
- 클라이언트 접속 시
  - 카메라로부터 비디오 스트리밍 수신
  - 클라이언트로 비디오 스트리밍 송출
- 문제 발생 시 관리자 UI 또는 Client에 메시지 표출

---

## 3. 인터페이스 규격 (변경 금지 — 반드시 준수)

### 3.1 Video / Control 채널 분리
- **Video**: RTSP (H.264 / MJPEG)
- **Control**: TCP JSON 또는 REST

### 3.2 RTSP URL 고정 규칙
- Port: **8554**
- Path: **/camera**
- 예: `rtsp://<server-ip>:8554/camera`

### 3.3 비디오 포맷
- Default: **1920x1080, 30FPS, H.264**
- 확장: 1280x720, 1920x1080, 3840x2160(4K)

### 3.4 지연(Latency) 목표
- 최소 500ms 이하 / 권장 200ms 이하 / 실시간 제어 100ms 이하

---

## 4. 오류 처리 원칙

- Server는 다음 상황을 감지·전파할 수 있어야 한다: RTSP 연결 실패, 스트림 끊김, Frame Timeout, Codec 오류, 재접속.
- **재접속 정책: 3초 간격 자동 재시도.**
- 오류 발생 시 관리자 UI 혹은 Client로 메시지를 전달한다.

---

## 5. 개발 공통 원칙

1. **요구사항 우선**: 구현 전 [docs/requirements.md](docs/requirements.md)의 해당 항목(FR/NFR ID)을 근거로 삼는다.
2. **인터페이스 계약 준수**: 3장의 규격(포트/경로/코덱/채널 분리)은 Client와의 계약이므로 임의 변경 금지.
3. **테스트**: 신규/변경 로직은 JUnit 테스트를 동반한다.
4. **기존 코드 스타일 준수**: 주변 코드의 네이밍·구조·컨벤션을 따른다.
5. **미결정 이슈 존중**: 아래 6장 이슈가 확정되기 전에는 관련 설계를 단정하지 말고 확인한다.

---

## 6. 미결정 이슈 (확정 필요)

1. **다중 Client 접속 지원 여부** (1대 vs 다중) → Server 세션/스트림 관리 설계에 영향.
2. **얼굴 인식 수행 위치** — 아키텍처상 Server에 `Face Detection & Recognition` 존재하나, 슬라이드 이슈에는 "Client 수행 여부" 질문 있음. **이 환경은 Server 전용이므로 기본 가정은 "Server에서 수행"**, 단 최종 확정 필요.
3. **최대 해상도 목표** (1080p vs 4K) → 인코딩/대역폭/성능 설계에 영향.

---

## 7. 참조 문서

- [docs/requirements.md](docs/requirements.md) — 전체 요구사항 정의서 (FR/NFR 추적표 포함)
