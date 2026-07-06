package com.servert.stream;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * MediaMTX(RTSP 서버) 자식 프로세스 관리. mediamtx.autostart=true 일 때만 활성화.
 * 앱 기동 시 mediamtx.exe 를 실행하고, 종료 시 정리한다. (FR-S-04 인프라)
 */
@Component
@ConditionalOnProperty(name = "mediamtx.autostart", havingValue = "true")
public class MediaMtxProcess {
    private static final Logger log = LoggerFactory.getLogger(MediaMtxProcess.class);

    private final String exePath;
    private Process process;

    public MediaMtxProcess(@Value("${mediamtx.exe:tools/mediamtx/mediamtx.exe}") String exePath) {
        this.exePath = exePath;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startOnReady() {
        File exe = new File(exePath);
        if (!exe.exists()) {
            log.warn("[MEDIAMTX] executable not found: {} (RTSP 서버를 수동 실행하세요)", exe.getAbsolutePath());
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath())
                    .directory(exe.getParentFile())
                    .redirectErrorStream(true);
            this.process = pb.start();
            log.info("[MEDIAMTX] started: {}", exe.getAbsolutePath());
        } catch (IOException e) {
            log.error("[MEDIAMTX] start failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (process != null && process.isAlive()) {
            log.info("[MEDIAMTX] stopping");
            process.destroy();
        }
    }
}
