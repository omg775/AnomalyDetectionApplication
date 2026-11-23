package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScoringIntegrationTest {

    @MockitoBean
    private MlServiceClient mlServiceClient;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private AnomalyAlertRepository anomalyAlertRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.security.admin.username}")
    private String adminUsername;

    private final List<MlPredictionRequest> requests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        anomalyAlertRepository.deleteAllInBatch();
        requests.clear();
        // Stub model: flags alerts whose source hash is even, score = -hash/1e10.
        when(mlServiceClient.getPredictions(any())).thenAnswer(invocation -> {
            MlPredictionRequest request = invocation.getArgument(0);
            requests.add(request);
            List<MlPredictionResultDTO> results = new ArrayList<>();
            for (MLDataDTO record : request.getRecords()) {
                MlPredictionResultDTO result = new MlPredictionResultDTO();
                result.setId(record.getId());
                result.setPrediction(record.getSourceIpHash() % 2 == 0 ? -1 : 1);
                result.setScore(-record.getSourceIpHash() / 1e10);
                results.add(result);
            }
            return new MlPredictionResponse(results);
        });
    }

    @AfterEach
    void cleanUp() {
        anomalyAlertRepository.deleteAllInBatch();
        ReflectionTestUtils.setField(scoringService, "batchSize", 5000);
    }

    private AnomalyAlert save(String ip) {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setSourceIp(ip);
        alert.setAnomalyType("HIGH_TRAFFIC_VOLUME");
        alert.setSeverity("HIGH");
        alert.setTimestamp(LocalDateTime.of(2026, 2, 1, 3, 0));
        return anomalyAlertRepository.save(alert);
    }

    private String token() {
        return "Bearer " + jwtUtil.generateToken(adminUsername);
    }

    @Test
    void scoreEndpoint_storesScoreFlagAlgorithmAndTime() throws Exception {
        save("10.0.0.2");
        save("10.0.0.3");

        mockMvc.perform(post("/api/anomalies/score").param("algorithm", "one_class_svm").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoredCount").value(2))
                .andExpect(jsonPath("$.anomalyCount").value(1))
                .andExpect(jsonPath("$.algorithm").value("one_class_svm"));

        assertThat(anomalyAlertRepository.findAll()).allSatisfy(alert -> {
            assertThat(alert.getMlScore()).isNotNull();
            assertThat(alert.getMlFlag()).isNotNull();
            assertThat(alert.getMlAlgorithm()).isEqualTo("one_class_svm");
            assertThat(alert.getMlScoredAt()).isNotNull();
        });
        assertThat(anomalyAlertRepository.findAll()).filteredOn(AnomalyAlert::getMlFlag).hasSize(1);
    }

    @Test
    void withoutAlgorithm_theConfiguredDefaultIsSentAndStored() {
        save("10.0.0.2");

        assertThat(scoringService.scoreUnscoredAlerts(null).algorithm()).isEqualTo("isolation_forest");
        assertThat(requests.get(0).getAlgorithm()).isEqualTo("isolation_forest");
    }

    @Test
    void secondRun_scoresOnlyNewAlerts() {
        save("10.0.0.2");
        scoringService.scoreUnscoredAlerts(null);
        save("10.0.0.4");

        assertThat(scoringService.scoreUnscoredAlerts(null).scoredCount()).isEqualTo(1);
        assertThat(scoringService.scoreUnscoredAlerts(null).scoredCount()).isZero();
        assertThat(requests).hasSize(2);
    }

    @Test
    void manyAlerts_areScoredBatchByBatch() {
        ReflectionTestUtils.setField(scoringService, "batchSize", 4);
        for (int i = 1; i <= 10; i++) {
            save("10.0.1." + i);
        }

        assertThat(scoringService.scoreUnscoredAlerts(null).scoredCount()).isEqualTo(10);
        assertThat(requests).extracting(r -> r.getRecords().size()).containsExactly(4, 4, 2);
    }

    @Test
    void failureInALaterBatch_keepsEarlierBatches_andTheNextRunResumes() {
        ReflectionTestUtils.setField(scoringService, "batchSize", 2);
        for (int i = 1; i <= 4; i++) {
            save("10.0.2." + i);
        }
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new MlServiceException("ML service unreachable", null);
            }
            MlPredictionRequest request = invocation.getArgument(0);
            List<MlPredictionResultDTO> results = new ArrayList<>();
            for (MLDataDTO record : request.getRecords()) {
                MlPredictionResultDTO result = new MlPredictionResultDTO();
                result.setId(record.getId());
                result.setPrediction(1);
                results.add(result);
            }
            return new MlPredictionResponse(results);
        }).when(mlServiceClient).getPredictions(any());

        assertThatThrownBy(() -> scoringService.scoreUnscoredAlerts(null)).isInstanceOf(MlServiceException.class);
        assertThat(anomalyAlertRepository.findAll()).filteredOn(a -> a.getMlScoredAt() != null).hasSize(2);

        assertThat(scoringService.scoreUnscoredAlerts(null).scoredCount()).isEqualTo(2);
    }

    @Test
    void missingPrediction_savesNothingForThatBatch() {
        save("10.0.0.2");
        doReturn(new MlPredictionResponse(List.of())).when(mlServiceClient).getPredictions(any());

        assertThatThrownBy(() -> scoringService.scoreUnscoredAlerts(null))
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("no prediction for 1 of 1");
        assertThat(anomalyAlertRepository.findAll()).allSatisfy(a -> assertThat(a.getMlScoredAt()).isNull());
    }

    @Test
    void noUnscoredAlerts_doesNotCallTheMlService() {
        assertThat(scoringService.scoreUnscoredAlerts(null).scoredCount()).isZero();
        verify(mlServiceClient, never()).getPredictions(any());
    }

    @Test
    void invalidAlgorithm_isRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/anomalies/score").param("algorithm", "random_forest").header("Authorization", token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertsResponse_showsMlFieldsOnlyForScoredAlerts() throws Exception {
        save("10.0.0.2");
        scoringService.scoreUnscoredAlerts(null);
        save("10.0.0.9");

        mockMvc.perform(get("/api/anomalies").param("sort", "id").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sourceIp == '10.0.0.2')].mlFlag").value(true))
                .andExpect(jsonPath("$.content[?(@.sourceIp == '10.0.0.2')].mlAlgorithm").value("isolation_forest"))
                .andExpect(jsonPath("$.content[?(@.sourceIp == '10.0.0.9')].mlScore").isEmpty())
                .andExpect(jsonPath("$.content[?(@.sourceIp == '10.0.0.9')].mlFlag").isEmpty());
    }
}
