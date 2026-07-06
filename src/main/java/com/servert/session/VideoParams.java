package com.servert.session;

import java.util.Set;

/**
 * 비디오 파라미터. 요구사항 §5.2 기본 포맷: 1920x1080 / 30FPS / H.264.
 * 허용 해상도: 1280x720, 1920x1080, 3840x2160(4K). 프레임레이트: 1..60.
 */
public record VideoParams(String resolution, int frameRate, String codec) {

    /** 허용 해상도 집합 (요구사항 §5.2). */
    public static final Set<String> ALLOWED_RESOLUTIONS = Set.of("1280x720", "1920x1080", "3840x2160");
    public static final int MIN_FRAME_RATE = 1;
    public static final int MAX_FRAME_RATE = 60;

    public static VideoParams defaults() {
        return new VideoParams("1920x1080", 30, "H.264");
    }

    /**
     * 파라미터 검증. 위반 시 InvalidParameterException(400).
     */
    public void validate() {
        if (resolution == null || !ALLOWED_RESOLUTIONS.contains(resolution)) {
            throw new InvalidParameterException(
                    "unsupported resolution: " + resolution + " (allowed: " + ALLOWED_RESOLUTIONS + ")");
        }
        if (frameRate < MIN_FRAME_RATE || frameRate > MAX_FRAME_RATE) {
            throw new InvalidParameterException(
                    "frameRate out of range: " + frameRate + " (allowed: " + MIN_FRAME_RATE + ".." + MAX_FRAME_RATE + ")");
        }
    }

    /** WxH 문자열에서 너비 추출 (ffmpeg video_size 등에 사용). */
    public int width() {
        return Integer.parseInt(resolution.split("x")[0]);
    }

    public int height() {
        return Integer.parseInt(resolution.split("x")[1]);
    }
}
