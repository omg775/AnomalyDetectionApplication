package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.AgreementResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceTest {

    @Test
    void agreementIsComputedAcrossBatches_matchingPredictionsById() {
        List<MlPredictionRequest> requests = new ArrayList<>();
        EvaluationService service = new EvaluationService(
                new MlFeatureService(PredictionServiceTest.repositoryWith(10), 4),
                PredictionServiceTest.echoingClient(requests));

        AgreementResponse response = service.computeAgreement();

        // Even ids are HIGH and flagged by the stub, odd ids are MEDIUM and not flagged.
        assertThat(requests).hasSize(3);
        assertThat(response.getTotalCompared()).isEqualTo(10);
        assertThat(response.getBothFlagged()).isEqualTo(5);
        assertThat(response.getNeitherFlagged()).isEqualTo(5);
        assertThat(response.getAgreementRate()).isEqualTo(1.0);
    }

    @Test
    void reversedResultOrder_doesNotMatter() {
        MlServiceClient client = mock(MlServiceClient.class);
        when(client.getPredictions(any())).thenAnswer(invocation -> {
            MlPredictionRequest request = invocation.getArgument(0);
            List<MlPredictionResultDTO> results = new ArrayList<>();
            request.getRecords().forEach(r -> {
                MlPredictionResultDTO result = new MlPredictionResultDTO();
                result.setId(r.getId());
                result.setPrediction(r.getId() == 2 ? -1 : 1);
                results.add(0, result);
            });
            return new MlPredictionResponse(results);
        });
        EvaluationService service = new EvaluationService(
                new MlFeatureService(PredictionServiceTest.repositoryWith(3), 10), client);

        AgreementResponse response = service.computeAgreement();

        assertThat(response.getBothFlagged()).isEqualTo(1);
        assertThat(response.getNeitherFlagged()).isEqualTo(2);
    }

    @Test
    void missingPrediction_isReportedAsAnMlServiceError() {
        MlServiceClient client = mock(MlServiceClient.class);
        when(client.getPredictions(any())).thenReturn(new MlPredictionResponse(List.of()));
        EvaluationService service = new EvaluationService(
                new MlFeatureService(PredictionServiceTest.repositoryWith(2), 10), client);

        assertThatThrownBy(service::computeAgreement)
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("no prediction for 2 of 2 alerts");
    }

    @Test
    void noAlerts_returnsZeros() {
        EvaluationService service = new EvaluationService(
                new MlFeatureService(PredictionServiceTest.repositoryWith(0), 10), mock(MlServiceClient.class));

        assertThat(service.computeAgreement().getTotalCompared()).isZero();
    }
}
