package com.om.AnomalyDetectionApplication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    /** Header carrying the shared secret the ML service expects on /train and /predict. */
    public static final String ML_API_KEY_HEADER = "X-API-Key";

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * RestTemplate for the FastAPI ML service, with timeouts and the X-API-Key header on every call.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${ml.service.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${ml.service.read-timeout-ms:15000}") long readTimeoutMs,
            @Value("${ml.service.api-key:}") String mlApiKey) {
        if (mlApiKey.isBlank()) {
            log.warn("ML_API_KEY is not set: the ML service will reject /train and /predict "
                    + "unless it runs with ALLOW_INSECURE_DEV=true");
        }
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .additionalInterceptors((request, body, execution) -> {
                    if (!mlApiKey.isBlank()) {
                        request.getHeaders().set(ML_API_KEY_HEADER, mlApiKey);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
