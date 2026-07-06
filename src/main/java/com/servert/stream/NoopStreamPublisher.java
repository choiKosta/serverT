package com.servert.stream;

import com.servert.session.VideoParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 카메라 비활성(camera.enabled=false) 환경용 no-op 게시자. 테스트/무카메라 개발에서 사용.
 * 실제 프로세스를 띄우지 않고 상태 플래그만 유지한다.
 */
@Component
@ConditionalOnProperty(name = "camera.enabled", havingValue = "false", matchIfMissing = false)
public class NoopStreamPublisher implements StreamPublisher {
    private static final Logger log = LoggerFactory.getLogger(NoopStreamPublisher.class);

    private volatile boolean running;
    private volatile String device = "";

    @Override
    public void start(VideoParams params) {
        running = true;
        log.info("[STREAM] (noop) start device=\"{}\" params={}@{}fps", device, params.resolution(), params.frameRate());
    }

    @Override
    public void stop() {
        running = false;
        log.info("[STREAM] (noop) stop");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String lastError() {
        return null;
    }

    @Override
    public void selectDevice(String device) {
        this.device = device;
        log.info("[STREAM] (noop) camera selected: device=\"{}\"", device);
    }

    @Override
    public String device() {
        return device;
    }
}
