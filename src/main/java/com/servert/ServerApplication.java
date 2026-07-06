package com.servert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@EnableScheduling
public class ServerApplication {
    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        log.info("Starting Server application...");
        SpringApplication.run(ServerApplication.class, args);
    }

    /**
     * 애플리케이션 기동 완료 시 서버 상태/접속 정보를 콘솔에 표시한다.
     * 실행 버튼을 눌렀을 때 서버가 정상적으로 떴는지 한눈에 확인하기 위한 시작 배너.
     */
    @Component
    static class StartupBanner {
        private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

        private final Environment env;

        StartupBanner(Environment env) {
            this.env = env;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            String port = env.getProperty("server.port", "8080");
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                host = "localhost";
            }

            log.info("========================================================");
            log.info(" Server is UP and READY");
            log.info(" - Web/Control (REST) : http://localhost:{}", port);
            log.info(" - Web/Control (REST) : http://{}:{}", host, port);
            log.info(" - Video (RTSP contract) : rtsp://{}:8554/camera", host);
            log.info(" Press Ctrl+C in the console to stop.");
            log.info("========================================================");
        }
    }
}
