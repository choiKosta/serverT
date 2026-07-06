package com.servert.stream;

import com.servert.session.VideoParams;

/**
 * 카메라 영상을 RTSP 로 게시(publish)하는 미디어 게시자 추상화.
 * 실제 구현(ffmpeg)과 테스트용 no-op 구현을 분리해, 하드웨어/ffmpeg 없이도 로직 테스트가 가능하게 한다.
 */
public interface StreamPublisher {

    /** 주어진 비디오 파라미터로 게시 시작. 이미 실행 중이면 무시. */
    void start(VideoParams params);

    /** 게시 중지. */
    void stop();

    /** 실행 여부(게시 프로세스 살아있는지). */
    boolean isRunning();

    /** 마지막 오류 메시지(없으면 null). */
    String lastError();

    /** 활성 카메라(dshow 장치명) 변경. 실제 반영은 다음 start 시점(또는 StreamService 의 재시작). */
    void selectDevice(String device);

    /** 현재 선택된 카메라 장치명. */
    String device();
}
