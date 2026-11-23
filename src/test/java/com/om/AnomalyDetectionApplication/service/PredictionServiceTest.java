package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PredictionServiceTest {

    static AnomalyAlert alert(long id) {
        AnomalyAlert alert = new AnomalyAlert();
        ReflectionTestUtils.setField(alert, "id", id);
        alert.setSourceIp("10.0.0." + id);
        alert.setAnomalyType("HIGH_TRAFFIC_VOLUME");
        alert.setSeverity(id % 2 == 0 ? "HIGH" : "MEDIUM");
        alert.setTimestamp(LocalDateTime.of(2026, 1, 5, (int) (id % 24), 0));
        return alert;
    }

    /** Repository stub that serves the given ids through keyset paging. */
    static AnomalyAlertRepository repositoryWith(long count) {
        List<AnomalyAlert> all = LongStream.rangeClosed(1, count).mapToObj(PredictionServiceTest::alert).toList();
        AnomalyAlertRepository repository = mock(AnomalyAlertRepository.class);
        when(repository.findByIdGreaterThanOrderByIdAsc(anyLong(), any(Limit.class))).thenAnswer(invocation -> {
            long after = invocation.getArgument(0);
            int max = ((Limit) invocation.getArgument(1)).max();
            return all.stream().filter(a -> a.getId() > after).limit(max).toList();
        });
        return repository;
    }

    /** ML client stub that flags every alert with an even id and records each request. */
    static MlServiceClient echoingClient(List<MlPredictionRequest> requests) {
        MlServiceClient client = mock(MlServiceClient.class);
        when(client.getPredictions(any())).thenAnswer(invocation -> {
            MlPredictionRequest request = invocation.getArgument(0);
            requests.add(request);
            List<MlPredictionResultDTO> results = new ArrayList<>();
            for (MLDataDTO record : request.getRecords()) {
                MlPredictionResultDTO result = new MlPredictionResultDTO();
                result.setId(record.getId());
                result.setPrediction(record.getId() % 2 == 0 ? -1 : 1);
                result.setScore(-0.1);
                results.add(result);
            }
            return new MlPredictionResponse(results);
        });
        return client;
    }

    @Test
    void predict_sendsOneRequestPerBatch_andKeepsIdsInOrder() {
        List<MlPredictionRequest> requests = new ArrayList<>();
        PredictionService service = new PredictionService(
                new MlFeatureService(repositoryWith(12), 5), echoingClient(requests));

        MlPredictionResponse response = service.predict("one_class_svm");

        assertThat(requests).hasSize(3);
        assertThat(requests).extracting(r -> r.getRecords().size()).containsExactly(5, 5, 2);
        assertThat(requests).allSatisfy(r -> assertThat(r.getAlgorithm()).isEqualTo("one_class_svm"));
        assertThat(response.getResults()).extracting(MlPredictionResultDTO::getId)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 12).boxed().toList());
    }

    @Test
    void predict_withNoAlerts_callsNothingAndReturnsAnEmptyList() {
        List<MlPredictionRequest> requests = new ArrayList<>();
        PredictionService service = new PredictionService(
                new MlFeatureService(repositoryWith(0), 5), echoingClient(requests));

        assertThat(service.predict(null).getResults()).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void featuresCarryTheAlertId() {
        List<MLDataDTO> features = new MlFeatureService(repositoryWith(7), 3).loadAllFeatures();

        assertThat(features).extracting(MLDataDTO::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(features.get(1).getSeverity()).isEqualTo(3);
        assertThat(features.get(1).getHourOfDay()).isEqualTo(2);
    }
}
