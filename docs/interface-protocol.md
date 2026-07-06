# 인터페이스 프로토콜 정의 — 클라이언트 접속 (Server)

> 범위: 클라이언트가 서버에 **접속(연결)** 하기 위한 인터페이스 프로토콜 정의.
> 근거: docs/requirements.md — FR-S-01(제어 명령 수신/수행), FR-S-02(접속/해제 관리), NFR-V-04(Video/Control 채널 분리), NFR-I-01(RTSP URL 고정), §5.4(Control = TCP JSON 또는 REST).
> 관련: [sprint/user-story-1.md](../sprint/user-story-1.md)(REST 연결 + "Hello World" 핸드셰이크).
> 이 환경은 **Server 전용**(CLAUDE.md §0) — 아래는 서버가 제공/준수해야 하는 계약이며, 클라이언트 구현은 이 계약을 따른다.

---

## 1. 채널 구성 (2-채널 분리)

요구사항 NFR-V-04에 따라 **제어(Control)** 와 **영상(Video)** 은 별도 채널로 분리한다.

| 채널 | 프로토콜 | 엔드포인트 | 용도 |
|------|----------|------------|------|
| **Control** | HTTP/REST (JSON) | `http://<server-ip>:8080/api/v1/...` | 접속/해제, 화면 제어(FrameRate/Resolution), 상태 조회 |
| **Video** | RTSP (H.264/MJPEG) | `rtsp://<server-ip>:8554/camera` | 비디오 스트리밍 (계약 고정: NFR-I-01) |

- 클라이언트는 **먼저 Control 채널로 접속(세션 생성)** 한 뒤, 응답으로 받은 RTSP URL로 **Video 채널에 별도 접속**한다.
- Control 채널로 REST를 선택한다(§5.4의 두 옵션 중). TCP JSON 대안은 §7 참조.
- 서버 실행 시 두 채널 주소가 콘솔 배너로 표시된다(`ServerApplication` 시작 로그 참조).

---

## 2. 접속 시퀀스 (Connection Sequence)

```
Client                                Server
  |                                     |
  |  (1) POST /api/v1/session           |   접속 요청 (FR-S-02)
  |------------------------------------>|   세션 생성, sessionId 발급
  |  201 { sessionId, rtspUrl, video }  |
  |<------------------------------------|
  |                                     |
  |  (2) RTSP SETUP/PLAY (rtspUrl)      |   Video 채널 별도 접속 (NFR-I-01)
  |====================================>|   MediaMTX 스트림 전송 시작
  |<==== H.264/MJPEG stream ============|
  |                                     |
  |  (3) PATCH /api/v1/session/{id}/video   화면 제어 (FR-S-01, FR-C-02)
  |------------------------------------>|   FrameRate/Resolution 변경
  |  200 { video }                      |
  |<------------------------------------|
  |                                     |
  |  (4) DELETE /api/v1/session/{id}    |   접속 해제 요청 (FR-S-02)
  |------------------------------------>|   세션 제거, 스트림 정리
  |  204 No Content                     |
  |<------------------------------------|
```

### 세션 상태 (State Machine)
```
[NONE] --POST /session--> [CONNECTED] --PATCH video--> [CONNECTED]
                              |
                              +--DELETE /session--> [CLOSED]
                              +--(timeout/오류)----> [CLOSED]
```

---

## 3. 공통 규약 (Common Conventions)

- **Base URL**: `http://<server-ip>:8080`
- **API 버전 접두사**: `/api/v1`
- **Content-Type**: `application/json; charset=UTF-8` (요청/응답 공통)
- **세션 식별**: 접속 후 발급된 `sessionId`를 경로 파라미터(`/session/{sessionId}`)로 전달. (헤더 방식 `X-Session-Id`는 §8 미결정)
- **시간/타임스탬프**: ISO-8601 UTC (`2026-07-06T12:00:00Z`)

### 3.1 성공 응답 상태 코드
| 동작 | 코드 |
|------|------|
| 세션 생성 | `201 Created` |
| 조회/제어 성공 | `200 OK` |
| 해제 성공 | `204 No Content` |

### 3.2 오류 응답 (Error Response) — FR-S-06 연계
모든 오류는 아래 JSON 형식으로 반환한다.
```json
{
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "해당 sessionId 를 찾을 수 없습니다.",
    "timestamp": "2026-07-06T12:00:00Z"
  }
}
```
| HTTP | code (예) | 상황 |
|------|-----------|------|
| `400 Bad Request` | `INVALID_PARAMETER` | 잘못된 요청 본문/파라미터 (예: 미지원 해상도) |
| `404 Not Found` | `SESSION_NOT_FOUND` | 없거나 이미 해제된 세션 |
| `409 Conflict` | `SESSION_LIMIT` | 동시 접속 제한 초과 (다중 접속 정책 확정 시) |
| `500 Internal Server Error` | `INTERNAL_ERROR` | 서버 내부 오류 (RTSP 연동 실패 등) |

---

## 4. 엔드포인트 정의 (Endpoints)

### 4.1 접속 — 세션 생성  `POST /api/v1/session`
클라이언트가 서버에 접속을 요청하고, 서버가 해당 클라이언트와 연결(세션)을 생성한다. (FR-S-02)

**Request** (본문 선택 — 초기 희망 비디오 파라미터)
```json
{
  "clientName": "qt-viewer-01",
  "video": { "resolution": "1920x1080", "frameRate": 30 }
}
```
**Response `201 Created`**
```json
{
  "sessionId": "b1e2c3d4-...",
  "rtspUrl": "rtsp://192.168.45.33:8554/camera",
  "video": { "resolution": "1920x1080", "frameRate": 30, "codec": "H.264" },
  "createdAt": "2026-07-06T12:00:00Z"
}
```
- 기본 비디오 포맷: **1920x1080 / 30FPS / H.264** (요구사항 §5.2).

### 4.2 상태 조회  `GET /api/v1/session/{sessionId}`
**Response `200 OK`**
```json
{ "sessionId": "b1e2c3d4-...", "state": "CONNECTED",
  "video": { "resolution": "1920x1080", "frameRate": 30, "codec": "H.264" } }
```

### 4.3 화면 제어  `PATCH /api/v1/session/{sessionId}/video`
Frame Rate / Resolution 조정. (FR-S-01, FR-C-02)

> **주의(구현):** 카메라 1대 → 게시 스트림 1개를 모든 클라이언트가 공유하므로, 화면 제어는 **스트림 전역**으로 적용된다(모든 클라이언트에 동일). 클라이언트별 개별 해상도는 per-client 트랜스코딩이 필요하여 범위 밖.
> 생략(null/미포함) 필드는 현재값 유지(PATCH 의미). codec 은 H.264 고정.

**Request** (변경할 필드만)
```json
{ "resolution": "1280x720", "frameRate": 60 }
```
**Response `200 OK`** — 적용된 최종 파라미터 반환
```json
{ "video": { "resolution": "1280x720", "frameRate": 60, "codec": "H.264" } }
```
- 허용 해상도: `1280x720`, `1920x1080`, `3840x2160` (§5.2). 그 외 값은 `400 INVALID_PARAMETER`.

### 4.4 접속 해제  `DELETE /api/v1/session/{sessionId}`
클라이언트가 접속 해제를 요청하면 서버가 해당 세션을 제거하고 스트림을 정리한다. (FR-S-02)

**Response `204 No Content`** (본문 없음)
- 이후 같은 `sessionId` 사용 시 `404 SESSION_NOT_FOUND`.

### 4.5 핸드셰이크 검증(에코)  `POST /api/v1/session/{sessionId}/echo`
User Story #1의 연결 검증용. 수신 문자열 뒤에 `" World"`를 붙여 반환한다.

**Request** `{ "message": "Hello" }` → **Response `200` OK** `{ "message": "Hello World" }`

### 4.6 스트림 상태/관리 (구현됨)
| 동작 | Method | Path | 응답 |
|------|--------|------|------|
| 상태 조회 | `GET` | `/api/v1/stream/status` | `{ state, resolution, frameRate, codec, activeClients, mediamtxUp, lastError }` |
| 수동 시작 | `POST` | `/api/v1/stream/start` | 상태 |
| 수동 중지 | `POST` | `/api/v1/stream/stop` | 상태 |
| 웹캠 목록 | `GET` | `/api/v1/stream/devices` | `{ "videoDevices": [...] }` |
| 카메라 선택 | `PUT` | `/api/v1/stream/camera` | body `{ "device": "..." }` → 상태 |

- **카메라 선택**: 클라이언트가 `GET /devices` 목록에서 고른 장치명을 `PUT /camera` 로 전달. 스트림 전역 적용(모든 클라이언트 공유), 실행 중이면 새 카메라로 재시작. 목록에 없는 장치는 `400 INVALID_PARAMETER`.
- 스트림은 **첫 클라이언트 접속 시 자동 시작**, **마지막 해제 시 자동 중지**된다.
- 게시 프로세스 이상 종료 시 감시 스케줄러가 **3초 간격 자동 재시작**한다(NFR-E-02).

---

## 5. 오류 처리 / 재접속 (Error & Reconnect)

- 서버는 RTSP 연결 실패·스트림 끊김·Frame Timeout·Codec 오류를 감지하고(NFR-E-01), 오류 메시지를 관리자 UI/Client에 전파한다(FR-S-06, §3.2 형식).
- **재접속 정책: 3초 간격 자동 재시도**(NFR-E-02). 클라이언트는 Control(REST) 또는 Video(RTSP) 채널 단절 시 3초 간격으로 재시도한다.
- 세션 유휴 타임아웃(무통신 시 자동 CLOSED) 값은 §8 미결정.

---

## 6. 지연 목표 (Latency) — NFR-V-03

| 등급 | 기준 | 적용 |
|------|------|------|
| 최소 | 500ms 이하 | 스트리밍 전반 |
| 권장 | 200ms 이하 | 일반 조회 |
| 실시간 제어 | 100ms 이하 | Control 명령(접속/제어) 응답 |

---

## 7. 대안: Control 채널을 TCP JSON 으로 (참고)

§5.4는 Control 채널로 REST **또는** TCP JSON을 허용한다. 본 문서는 REST를 채택하되, TCP JSON 채택 시 동일 의미를 다음과 같이 매핑한다(향후 확정 시 별도 정의).
```json
// 요청 예: 접속
{ "type": "CONNECT", "clientName": "qt-viewer-01",
  "video": { "resolution": "1920x1080", "frameRate": 30 } }
// 응답 예
{ "type": "CONNECT_OK", "sessionId": "...", "rtspUrl": "rtsp://.../camera" }
```
`type`: `CONNECT` / `DISCONNECT` / `SET_VIDEO` / `ERROR`.

---

## 8. 미결정 이슈 (확정 필요)

1. **다중 클라이언트 접속** (§8-1): 1대 vs 다중 → 세션 저장소/동시성/`409 SESSION_LIMIT` 정책 결정 필요.
2. **인증/인가**: 세션 발급 시 토큰/헤더(`Authorization`, `X-Session-Id`) 필요 여부. 현재 초안은 무인증.
3. **세션 유휴 타임아웃** 값(예: 60s) 및 keep-alive(ping) 방식.
4. **세션 식별 전달 방식**: 경로 파라미터 vs 헤더.
5. Control 채널 최종 선택(REST 확정 vs TCP JSON) — 본 문서는 REST 가정.

---

## 9. 추적성 (Traceability)

| 프로토콜 요소 | 요구사항 ID |
|---------------|-------------|
| 접속/해제 세션 관리 | FR-S-02, FR-C-01 |
| 화면 제어(FrameRate/Resolution) | FR-S-01, FR-C-02 |
| Video/Control 채널 분리 | NFR-V-04 |
| RTSP URL 고정(8554/camera) | NFR-I-01 |
| 오류 감지/메시지 전파 | NFR-E-01, FR-S-06 |
| 3초 재접속 | NFR-E-02 |
| 지연 목표 | NFR-V-03 |
