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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 접속 -> 데이터 전송(Hello->Hello World) -> 접속 해제 시나리오 검증. (User Story #1)
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void connect_then_echo_then_disconnect() throws Exception {
        // (1) 접속: 세션 생성
        MvcResult connectResult = mockMvc.perform(post("/api/v1/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"test-client\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.rtspUrl").value(org.hamcrest.Matchers.containsString(":8554/camera")))
                .andExpect(jsonPath("$.video.resolution").value("1920x1080"))
                .andReturn();

        JsonNode connectBody = objectMapper.readTree(connectResult.getResponse().getContentAsString());
        String sessionId = connectBody.get("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        // (2) 데이터 전송: "Hello" -> "Hello World"
        mockMvc.perform(post("/api/v1/session/{id}/echo", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello World"));

        // (3) 접속 해제
        mockMvc.perform(delete("/api/v1/session/{id}", sessionId))
                .andExpect(status().isNoContent());

        // (4) 해제된 세션 재사용 -> 404 SESSION_NOT_FOUND
        mockMvc.perform(post("/api/v1/session/{id}/echo", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void disconnect_unknown_session_returns_404() throws Exception {
        mockMvc.perform(delete("/api/v1/session/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }
}
