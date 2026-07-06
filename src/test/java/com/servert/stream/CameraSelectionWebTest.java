package com.servert.stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클라이언트 카메라 선택 웹 계층 검증. 테스트 프로파일에서 DeviceProbe 는 비어있어 임의 장치를 허용.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CameraSelectionWebTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void devices_endpoint_returns_list() throws Exception {
        mockMvc.perform(get("/api/v1/stream/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoDevices").isArray());
    }

    @Test
    void select_camera_applies_device_to_status() throws Exception {
        mockMvc.perform(put("/api/v1/stream/camera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device\":\"Camera Sensor OV02C10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device").value("Camera Sensor OV02C10"));

        mockMvc.perform(get("/api/v1/stream/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device").value("Camera Sensor OV02C10"));
    }

    @Test
    void select_blank_camera_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/stream/camera")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }
}
