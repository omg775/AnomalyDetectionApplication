package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.config.AppConfig;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MlServiceClientTest {

    private static final String BASE_URL = "http://ml.test";
    private static final MlPredictionRequest REQUEST =
            new MlPredictionRequest(List.of(new MLDataDTO(7L, 10, 2, 3, 1, 42)));

    private RestTemplate restTemplate(String apiKey) {
        return new AppConfig().restTemplate(new RestTemplateBuilder(), 1000, 1000, apiKey);
    }

    @Test
    void predictAndTrainAndHealth_sendTheApiKeyHeader() {
        RestTemplate restTemplate = restTemplate("shared-secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MlServiceClient client = new MlServiceClient(restTemplate, BASE_URL);

        server.expect(requestTo(BASE_URL + "/predict")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "shared-secret"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/train")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "shared-secret"))
                .andRespond(withSuccess("{\"message\":\"ok\",\"records_processed\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/health"))
                .andExpect(header("X-API-Key", "shared-secret"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getPredictions(REQUEST).getResults()).isEmpty();
        assertThat(client.trainModel(REQUEST).getRecordsProcessed()).isEqualTo(1);
        assertThat(client.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void blankApiKey_sendsNoHeader() {
        RestTemplate restTemplate = restTemplate("");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MlServiceClient client = new MlServiceClient(restTemplate, BASE_URL);

        server.expect(requestTo(BASE_URL + "/predict"))
                .andExpect(headerDoesNotExist("X-API-Key"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        client.getPredictions(REQUEST);
        server.verify();
    }

    @Test
    void rejectedKeyOnPredict_becomesAClearMlServiceException() {
        RestTemplate restTemplate = restTemplate("wrong");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MlServiceClient client = new MlServiceClient(restTemplate, BASE_URL);
        server.expect(requestTo(BASE_URL + "/predict")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getPredictions(REQUEST))
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("rejected the API key");
    }

    @Test
    void rejectedKeyOnTrain_becomesAClearMlServiceException() {
        RestTemplate restTemplate = restTemplate("wrong");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MlServiceClient client = new MlServiceClient(restTemplate, BASE_URL);
        server.expect(requestTo(BASE_URL + "/train")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.trainModel(REQUEST))
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("rejected the API key");
    }
}
