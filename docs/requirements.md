# 카메라 관제 시스템 요구사항 정의서

> 출처: *Agentic Coding for Embedded SW Systems* 강의 슬라이드 (© Sungwoon Choi 2026, p.21~25)
> 작성일: 2026-07-06

---

## 1. 개요 (System Overview)

- **시스템 목적**: 카메라 관제(감시) 시스템
- **구조**: Client–Server 구조
- **핵심 흐름**: Camera → Server(영상 수신/처리/스트리밍) → Client/Web Client(영상 조회 및 제어)

---

## 2. 시스템 아키텍처 (Initial Architecture)

```
                          ┌─────────────────┐
                          │   Web Client    │
                          │   - Windows     │
                          └────────┬────────┘
                                   │ (REST / Web UI)
┌──────────────────┐      ┌────────┴────────┐      ┌──────────┐
│      Client       │◄────►│     Server      │◄────►│  Camera  │
│  - Ubuntu/WSL2    │      │   - Windows     │      └──────────┘
└────────┬─────────┘      └────────┬────────┘
         │                          │
   ┌─────┴─────┐          ┌─────┬───┴───┬─────────┬──────────┐
   │ QT View   │          │Spring│MediaMTX│  Face  │ Camera   │
   │ - 화면표시 │          │Server│ RTSP   │Detection│Interface│
   ├───────────┤          │-REST │ Server │   &     │-프레임획득│
   │ Server    │          │ API  │Client접속│Recognit.│         │
   │ Interface │          │      │ 관리    │         │          │
   │-RTSP 수신 │          │      │RTP/RTCP │         │          │
   └───────────┘          └──────┴─────────┴────────┴──────────┘
```

### 2.1 구성 요소
| 노드 | 환경 | 세부 컴포넌트 |
|------|------|----------------|
| **Client** | Ubuntu/WSL2 | QT View(화면 표시), Server Interface(RTSP:// 수신) |
| **Server** | Windows | Spring Server(REST API), MediaMTX RTSP Server(Client 접속 관리, RTP/RTCP 처리), Face Detection & Recognition, Camera Interface(프레임 획득) |
| **Web Client** | Windows | Server와 통신하는 웹 UI |
| **Camera** | - | 영상 소스 |

---

## 3. Client 요구사항

### 3.1 기능
- **Server에 제어 명령 송출**
  - 접속 / 접속 해제
  - 화면 제어
    - Frame Rate / Resolution 조정
- **서버 접속 시**
  - 서버로부터 송출되는 비디오 스트리밍 보기
- **문제 발생 시 화면에 에러 메시지 표출**

### 3.2 실행 환경 (Runtime)
| 항목 | 내용 |
|------|------|
| OS | Ubuntu |
| Package/Deployment Tool | vcpkg / Docker |
| Server Interface | RTSP / H.264 |
| UI | QT UI |

### 3.3 개발 환경 (Development)
| 항목 | 내용 |
|------|------|
| OS | Ubuntu / WSL2 |
| IDE | VS Code / CoPilot, Codex or Claude |
| Programming Language | C++ |
| Build Tool | CMake |
| Package/Deployment Tool | vcpkg / Docker |
| Compiler | g++ |
| Debugging Tool | gdb |
| Vision Library | OpenCV |
| UI | QT |
| Test | Google Test |

---

## 4. Server 요구사항

### 4.1 기능
- **클라이언트로부터 제어 명령 수신 및 수행**
  - 클라이언트와 접속 및 접속 해제
  - 화면 제어
    - Frame Rate / Resolution
- **클라이언트 접속 시**
  - 카메라로부터 비디오 스트리밍을 수신
  - 클라이언트로 비디오 스트리밍
- **문제 발생 시 관리자 UI 혹은 Client에 메시지 표출**

### 4.2 실행 환경 (Runtime)
| 항목 | 내용 |
|------|------|
| OS | Windows |
| Package/Deployment Tool | Gradle |
| Server Interface | RTSP / H.264 |
| UI | Web UI |
| Framework | Spring Boot, MediaMTX |

### 4.3 개발 환경 (Development)
| 항목 | 내용 |
|------|------|
| OS | Windows |
| IDE | IntelliJ / JetBrains, Codex or Claude |
| Programming Language | Java |
| Build Tool | Gradle |
| Package/Deployment Tool | (미정) |
| Vision Library | OpenCV |
| UI | Web |
| Test | JUnit |

---

## 5. 비디오 요구사항 (Video)

### 5.1 프로토콜 / 코덱
- **Protocol**: RTSP
- **Codec**: H.264 / MJPEG

### 5.2 포맷 (Format)
- **Default**: 1920 x 1080, 30 FPS, H.264
- **확장 지원 해상도**: 1280 x 720, 1920 x 1080, 3840 x 2160 (4K)

### 5.3 지연 요구사항 (Latency Requirements)
| 등급 | 기준 |
|------|------|
| 최소 | 500 ms 이하 |
| 권장 | 200 ms 이하 |
| 실시간 제어 | 100 ms 이하 |

### 5.4 제어 인터페이스 (Control Interface)
- **비디오와 제어 채널 분리**
  - Video: RTSP
  - Control: TCP JSON 또는 REST

---

## 6. 인터페이스 URL (Interface URL)

### 6.1 고정 규칙
- **Port**: 8554
- **Path**: /camera

### 6.2 예시
```
rtsp://<server-ip>:8554/camera
```

---

## 7. 오류 처리 (Error Handling)

### 7.1 Client가 감지해야 할 이벤트
- RTSP 연결 실패
- 스트림 끊김
- Frame Timeout
- Codec 오류
- 재접속

### 7.2 재접속 정책
- **3초 간격 자동 재시도**

---

## 8. 미결정 이슈 (Open Issues)

> 아래 항목은 요구사항 확정 전 결정이 필요한 사항이다.

1. **Client 대수**: Client는 1대인가, 다중 Client 접속을 고려하는가?
2. **얼굴 인식 위치**: 얼굴 인식은 Client에서 수행하는가? (아키텍처상 Server에 Face Detection & Recognition 컴포넌트 존재 → 확정 필요)
3. **최대 해상도 목표**: 최대 해상도 목표는 1080p인가, 4K까지 고려하는가?

---

## 부록 A. 요구사항 요약 (Traceability)

| ID | 구분 | 요구사항 | 우선순위 |
|----|------|----------|:--------:|
| FR-C-01 | Client/기능 | Server 접속/접속 해제 제어 명령 송출 | High |
| FR-C-02 | Client/기능 | Frame Rate / Resolution 화면 제어 | High |
| FR-C-03 | Client/기능 | 서버로부터의 비디오 스트리밍 조회 | High |
| FR-C-04 | Client/기능 | 문제 발생 시 화면에 에러 메시지 표출 | Medium |
| FR-S-01 | Server/기능 | 클라이언트 제어 명령 수신 및 수행 | High |
| FR-S-02 | Server/기능 | 클라이언트 접속/해제 관리 | High |
| FR-S-03 | Server/기능 | 카메라로부터 비디오 스트리밍 수신 | High |
| FR-S-04 | Server/기능 | 클라이언트로 비디오 스트리밍 송출 | High |
| FR-S-05 | Server/기능 | 얼굴 인식(Face Detection & Recognition) | (이슈) |
| FR-S-06 | Server/기능 | 문제 발생 시 관리자 UI/Client에 메시지 표출 | Medium |
| NFR-V-01 | Video | RTSP + H.264/MJPEG 스트리밍 | High |
| NFR-V-02 | Video | Default 1920x1080/30FPS, 720p~4K 확장 | High |
| NFR-V-03 | Video/성능 | 지연 최소 500ms / 권장 200ms / 실시간 100ms 이하 | High |
| NFR-V-04 | Video | Video(RTSP)와 Control(TCP JSON/REST) 채널 분리 | High |
| NFR-I-01 | Interface | RTSP URL 고정 규칙 (Port 8554, Path /camera) | High |
| NFR-E-01 | 오류처리 | RTSP 실패/스트림 끊김/Frame Timeout/Codec 오류 감지 | High |
| NFR-E-02 | 오류처리 | 3초 간격 자동 재접속 | High |
