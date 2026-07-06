package com.servert.stream;

/**
 * 스트림 상태 스냅샷. GET /api/v1/stream/status 응답 및 진단용.
 */
public record StreamStatus(
        String state,        // RUNNING | STOPPED
        String device,       // 현재 선택된 카메라(dshow 장치명)
        String resolution,
        int frameRate,
        String codec,
        int activeClients,
        boolean mediamtxUp,
        String lastError) {
}
