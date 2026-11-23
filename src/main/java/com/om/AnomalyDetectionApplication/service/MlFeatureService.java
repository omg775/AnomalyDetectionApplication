package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads anomaly alerts for the ML layer in id order, {@code ml.feature.batch-size} at a time, so no
 * code path has to load the whole table at once.
 */
@Service
public class MlFeatureService {

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final int batchSize;

    public MlFeatureService(AnomalyAlertRepository anomalyAlertRepository,
                            @Value("${ml.feature.batch-size:5000}") int batchSize) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.batchSize = batchSize;
    }

    /**
     * Passes every alert to {@code consumer}, one id-ordered batch at a time.
     *
     * @param consumer receives each non-empty batch
     */
    public void forEachAlertBatch(Consumer<List<AnomalyAlert>> consumer) {
        long lastId = 0;
        List<AnomalyAlert> batch;
        do {
            batch = anomalyAlertRepository.findByIdGreaterThanOrderByIdAsc(lastId, Limit.of(batchSize));
            if (!batch.isEmpty()) {
                consumer.accept(batch);
                lastId = batch.get(batch.size() - 1).getId();
            }
        } while (batch.size() == batchSize);
    }

    /**
     * Builds the feature vectors of all alerts in id order.
     *
     * @return one entry per alert, carrying the alert id
     */
    public List<MLDataDTO> loadAllFeatures() {
        List<MLDataDTO> features = new ArrayList<>();
        forEachAlertBatch(batch -> batch.forEach(alert -> features.add(MLFeatureMapper.toFeatures(alert))));
        return features;
    }
}
