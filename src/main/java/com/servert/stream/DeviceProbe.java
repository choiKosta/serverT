package com.servert.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ffmpeg dshow 로 로컬 비디오 캡처 장치(웹캠) 목록을 조회한다.
 * 사용자가 camera.device 를 정확히 설정하도록 돕는다.
 */
@Component
public class DeviceProbe {
    private static final Logger log = LoggerFactory.getLogger(DeviceProbe.class);

    // ffmpeg -list_devices 출력에서 "장치명" (video) 라인 파싱
    private static final Pattern NAME = Pattern.compile("\"([^\"]+)\"\\s*\\(video\\)");

    private final String ffmpeg;

    public DeviceProbe(@Value("${camera.ffmpeg:ffmpeg}") String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public List<String> listVideoDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(
                    ffmpeg, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = NAME.matcher(line);
                    if (m.find()) {
                        devices.add(m.group(1));
                    }
                }
            }
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[STREAM] device probe failed: {}", e.getMessage());
        }
        return devices;
    }
}
