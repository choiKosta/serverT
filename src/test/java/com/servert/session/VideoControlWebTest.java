package com.servert.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화면 제어(PATCH video) 및 다중 클라이언트 세션-스트림 연동 웹 계층 검증.
 * 테스트 프로파일: camera.enabled=false (NoopStreamPublisher) — 하드웨어/ffmpeg 불필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VideoControlWebTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String connect() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"c\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("sessionId").asText();
    }

    private int activeClients() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/stream/status"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        return body.get("activeClients").asInt();
    }

    @Test
    void patch_video_valid_applies_params() throws Exception {
        String id = connect();
        try {
            mockMvc.perform(patch("/api/v1/session/{id}/video", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resolution\":\"1280x720\",\"frameRate\":60}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolution").value("1280x720"))
                    .andExpect(jsonPath("$.frameRate").value(60))
                    .andExpect(jsonPath("$.codec").value("H.264"));
        } finally {
            mockMvc.perform(delete("/api/v1/session/{id}", id)).andExpect(status().isNoContent());
        }
    }

    @Test
    void patch_video_invalid_resolution_returns_400() throws Exception {
        String id = connect();
        try {
            mockMvc.perform(patch("/api/v1/session/{id}/video", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resolution\":\"640x480\",\"frameRate\":30}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
        } finally {
            mockMvc.perform(delete("/api/v1/session/{id}", id)).andExpect(status().isNoContent());
        }
    }

    @Test
    void multi_client_active_count_tracks_sessions() throws Exception {
        int baseline = activeClients();
        String a = connect();
        String b = connect();
        assertThat(activeClients()).isEqualTo(baseline + 2);

        mockMvc.perform(delete("/api/v1/session/{id}", a)).andExpect(status().isNoContent());
        assertThat(activeClients()).isEqualTo(baseline + 1);

        mockMvc.perform(delete("/api/v1/session/{id}", b)).andExpect(status().isNoContent());
        assertThat(activeClients()).isEqualTo(baseline);
    }
}
