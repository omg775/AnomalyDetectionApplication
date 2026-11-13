package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import com.om.AnomalyDetectionApplication.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CsvUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TrafficRecordRepository trafficRecordRepository;

    @Value("${app.security.admin.username}")
    private String adminUsername;

    private String bearerToken() {
        return "Bearer " + jwtUtil.generateToken(adminUsername);
    }

    @Test
    void uploadCsv_withValidAndMalformedRows_importsValidAndSkipsMalformed() throws Exception {
        String csv = "timestamp,source_ip,destination_ip,protocol,destination_port,bytes_transferred\n"
                + "2026-01-01T10:00:00,192.168.1.1,10.0.0.1,TCP,443,5000\n"
                + "not-a-timestamp,192.168.1.2,10.0.0.1,TCP,443,5000\n"
                + "2026-01-01T11:00:00,192.168.1.3,10.0.0.1,TCP,not-a-port,5000\n"
                + "2026-01-01T12:00:00,192.168.1.4,10.0.0.1,TCP,22,not-a-number\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "traffic.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        long before = trafficRecordRepository.count();

        mockMvc.perform(multipart("/api/traffic/upload")
                        .file(file)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(3))
                .andExpect(jsonPath("$.errors.length()").value(3));

        assertThat(trafficRecordRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void uploadCsv_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/traffic/upload")
                        .file(file)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCsv_withoutAuth_returns401() throws Exception {
        String csv = "timestamp,source_ip,destination_ip,protocol,destination_port,bytes_transferred\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "traffic.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/traffic/upload").file(file))
                .andExpect(status().isUnauthorized());
    }
}
