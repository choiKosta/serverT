package com.servert.stream;

import com.servert.session.InvalidParameterException;
import com.servert.session.VideoParams;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StreamService 로직 단위 테스트(스프링 컨텍스트 없이). 세션-스트림 연동, 화면 제어, 재접속 감시.
 */
class StreamServiceTest {

    /** start 호출 횟수와 살아있음 여부를 제어 가능한 fake publisher. */
    static class FakePublisher implements StreamPublisher {
        final AtomicInteger startCount = new AtomicInteger();
        volatile boolean running;
        volatile String device = "";
        VideoParams lastParams;

        @Override public void start(VideoParams params) { startCount.incrementAndGet(); lastParams = params; running = true; }
        @Override public void stop() { running = false; }
        @Override public boolean isRunning() { return running; }
        @Override public String lastError() { return null; }
        @Override public void selectDevice(String device) { this.device = device; }
        @Override public String device() { return device; }
    }

    private StreamService newService(FakePublisher pub) {
        // DeviceProbe 에 없는 ffmpeg 경로 -> 장치 목록 비어있음 -> selectCamera 검증 통과(임의 장치 허용)
        return new StreamService(pub, new DeviceProbe("no-such-ffmpeg"), "1920x1080", 30, "http://localhost:9997");
    }

    @Test
    void first_connect_starts_and_last_disconnect_stops() {
        FakePublisher pub = new FakePublisher();
        StreamService svc = newService(pub);

        svc.onClientConnected();               // 1st
        assertThat(pub.isRunning()).isTrue();
        assertThat(svc.activeClients()).isEqualTo(1);

        svc.onClientConnected();               // 2nd (다중 클라이언트)
        assertThat(svc.activeClients()).isEqualTo(2);
        assertThat(pub.startCount.get()).isEqualTo(1); // 스트림은 하나로 공유(중복 시작 없음)

        svc.onClientDisconnected();            // -> 1
        assertThat(pub.isRunning()).isTrue();
        svc.onClientDisconnected();            // -> 0, stop
        assertThat(pub.isRunning()).isFalse();
        assertThat(svc.activeClients()).isEqualTo(0);
    }

    @Test
    void applyVideo_validates_and_restarts_when_running() {
        FakePublisher pub = new FakePublisher();
        StreamService svc = newService(pub);
        svc.onClientConnected(); // running

        VideoParams applied = svc.applyVideo(new VideoParams("1280x720", 60, null));
        assertThat(applied.resolution()).isEqualTo("1280x720");
        assertThat(applied.frameRate()).isEqualTo(60);
        assertThat(applied.codec()).isEqualTo("H.264");
        assertThat(pub.lastParams.resolution()).isEqualTo("1280x720"); // 재시작 시 새 파라미터 반영
    }

    @Test
    void applyVideo_rejects_bad_resolution() {
        StreamService svc = newService(new FakePublisher());
        assertThatThrownBy(() -> svc.applyVideo(new VideoParams("800x600", 30, null)))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void watchdog_reconnects_when_dead_but_should_run() {
        FakePublisher pub = new FakePublisher();
        StreamService svc = newService(pub);
        svc.onClientConnected();               // shouldRun=true, running=true, startCount=1
        pub.running = false;                   // 프로세스가 죽은 상황 시뮬레이션 (NFR-E-01)

        svc.watchdog();                        // 재접속 시도 (NFR-E-02)
        assertThat(pub.isRunning()).isTrue();
        assertThat(pub.startCount.get()).isEqualTo(2);
    }

    @Test
    void selectCamera_sets_device_and_restarts_when_running() {
        FakePublisher pub = new FakePublisher();
        StreamService svc = newService(pub);
        svc.onClientConnected();                 // running, startCount=1

        StreamStatus st = svc.selectCamera("Built-in Cam");
        assertThat(pub.device()).isEqualTo("Built-in Cam");
        assertThat(st.device()).isEqualTo("Built-in Cam");
        assertThat(pub.startCount.get()).isEqualTo(2); // 재시작됨
    }

    @Test
    void selectCamera_rejects_blank() {
        StreamService svc = newService(new FakePublisher());
        assertThatThrownBy(() -> svc.selectCamera(" "))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void watchdog_does_nothing_when_stopped() {
        FakePublisher pub = new FakePublisher();
        StreamService svc = newService(pub);
        // shouldRun=false (아무도 연결 안 함)
        svc.watchdog();
        assertThat(pub.startCount.get()).isEqualTo(0);
    }
}
