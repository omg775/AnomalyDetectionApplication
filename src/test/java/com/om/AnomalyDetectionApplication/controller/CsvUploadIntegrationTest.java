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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

    private static final String HEADER = "timestamp,source_ip,destination_ip,protocol,destination_port,bytes_transferred\n";

    private String bearerToken() {
        return "Bearer " + jwtUtil.generateToken(adminUsername);
    }

    private ResultActions upload(String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "traffic.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/traffic/upload")
                .file(file)
                .header("Authorization", bearerToken()));
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

    @Test
    void uploadCsv_validatesIpPortBytesAndProtocol_andReportsEveryReason() throws Exception {
        String csv = HEADER
                + "2026-01-01T10:00:00,999.1.1.1,10.0.0.1,TCP,443,5000\n"
                + "2026-01-01T10:00:01,192.168.1.2,not-an-ip,TCP,443,5000\n"
                + "2026-01-01T10:00:02,192.168.1.3,10.0.0.1,TCP,70000,5000\n"
                + "2026-01-01T10:00:03,192.168.1.4,10.0.0.1,TCP,-1,5000\n"
                + "2026-01-01T10:00:04,192.168.1.5,10.0.0.1,TCP,443,-5\n"
                + "2026-01-01T10:00:05,192.168.1.6,10.0.0.1,GOPHER,443,5000\n"
                + "2026-01-01T10:00:06,bad-ip,10.0.0.1,GOPHER,99999,5000\n";

        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(7))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.errors.length()").value(7))
                .andExpect(jsonPath("$.skippedRows[0].row").value(1))
                .andExpect(jsonPath("$.skippedRows[0].reason", containsString("source_ip '999.1.1.1'")))
                .andExpect(jsonPath("$.skippedRows[1].reason", containsString("destination_ip 'not-an-ip'")))
                .andExpect(jsonPath("$.skippedRows[2].reason", containsString("destination_port '70000'")))
                .andExpect(jsonPath("$.skippedRows[3].reason", containsString("destination_port '-1'")))
                .andExpect(jsonPath("$.skippedRows[4].reason", containsString("bytes_transferred '-5'")))
                .andExpect(jsonPath("$.skippedRows[5].reason", containsString("protocol 'GOPHER'")))
                .andExpect(jsonPath("$.skippedRows[6].reason", containsString("source_ip")))
                .andExpect(jsonPath("$.skippedRows[6].reason", containsString("protocol")))
                .andExpect(jsonPath("$.skippedRows[6].reason", containsString("destination_port")))
                .andExpect(jsonPath("$.errors[0]", containsString("Row 1: source_ip")));
    }

    @Test
    void uploadCsv_acceptsIpv6_boundaryPorts_zeroBytes_lowercaseProtocol_andBlankDestination() throws Exception {
        String csv = HEADER
                + "2026-01-01T10:00:00,2001:db8::1,::ffff:192.0.2.1,udp,0,0\n"
                + "2026-01-01T10:00:01,10.0.0.7,,ICMP,65535,1\n";

        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0));

        assertThat(trafficRecordRepository.findAll())
                .anySatisfy(r -> assertThat(r.getDestinationIp()).isNull())
                .allSatisfy(r -> assertThat(r.getRowHash()).hasSize(64));
    }

    @Test
    void uploadCsv_skipsExactDuplicatesWithinTheFile() throws Exception {
        String row = "2026-01-01T10:00:00,192.168.1.1,10.0.0.1,TCP,443,5000\n";
        String csv = HEADER + row + "2026-01-01T10:00:00,192.168.1.1,10.0.0.1,TCP,443,5001\n" + row + row;

        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.duplicateCount").value(2))
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(jsonPath("$.skippedRows[0].row").value(3))
                .andExpect(jsonPath("$.skippedRows[0].reason").value("duplicate of row 1"));
    }

    @Test
    void uploadingTheSameFileTwice_importsNothingTheSecondTime() throws Exception {
        String csv = HEADER
                + "2026-01-01T10:00:00,192.168.1.1,10.0.0.1,TCP,443,5000\n"
                + "2026-01-01T10:00:01,192.168.1.2,10.0.0.1,TCP,443,5000\n";
        long before = trafficRecordRepository.count();

        upload(csv).andExpect(jsonPath("$.importedCount").value(2));
        upload(csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.duplicateCount").value(2))
                .andExpect(jsonPath("$.skippedRows[0].reason").value("duplicate of an existing record"));

        assertThat(trafficRecordRepository.count()).isEqualTo(before + 2);
    }

    @Test
    void uploadCsv_largerThanOneBatch_importsEveryRow() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 1234; i++) {
            csv.append("2026-01-01T10:00:00,10.1.").append(i / 256).append('.').append(i % 256)
                    .append(",10.0.0.1,TCP,443,").append(i).append('\n');
        }

        upload(csv.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1234));
    }

    @Test
    void uploadCsv_missingRequiredColumn_returns400AndImportsNothing() throws Exception {
        long before = trafficRecordRepository.count();

        upload("timestamp,source_ip,destination_ip,protocol,destination_port\n"
                + "2026-01-01T10:00:00,192.168.1.1,10.0.0.1,TCP,443\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("missing required columns: bytes_transferred")));

        assertThat(trafficRecordRepository.count()).isEqualTo(before);
    }
}
