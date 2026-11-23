package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.dto.ScoreResponse;
import com.om.AnomalyDetectionApplication.dto.TrainModelResponse;
import com.om.AnomalyDetectionApplication.service.PredictionService;
import com.om.AnomalyDetectionApplication.service.ScoringService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Validated
public class PredictionController {

    private static final String ALGORITHM_PATTERN = "isolation_forest|local_outlier_factor|one_class_svm";

    private final PredictionService predictionService;
    private final ScoringService scoringService;

    public PredictionController(PredictionService predictionService, ScoringService scoringService) {
        this.predictionService = predictionService;
        this.scoringService = scoringService;
    }

    @GetMapping("/api/anomalies/predict")
    public MlPredictionResponse predict(
            @RequestParam(required = false) @Pattern(regexp = ALGORITHM_PATTERN) String algorithm
    ) {
        return predictionService.predict(algorithm);
    }

    @PostMapping("/api/anomalies/train")
    public TrainModelResponse train(
            @RequestParam(required = false) @Pattern(regexp = ALGORITHM_PATTERN) String algorithm,
            @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("0.5") Double contamination
    ) {
        return predictionService.trainModel(algorithm, contamination);
    }

    /** Scores alerts that have no stored ML score yet and saves the results on the alerts. */
    @PostMapping("/api/anomalies/score")
    public ScoreResponse score(
            @RequestParam(required = false) @Pattern(regexp = ALGORITHM_PATTERN) String algorithm
    ) {
        return scoringService.scoreUnscoredAlerts(algorithm);
    }

    @GetMapping("/anomalies-only")
    public List<MlPredictionResultDTO> getAnomaliesOnly() {
        return predictionService.predict(null)
                .getResults()
                .stream()
                .filter(p -> p.getPrediction() == -1)
                .toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<MlPredictionResultDTO> data = predictionService.predict(null).getResults();

        if (data.isEmpty()) {
            return Map.of(
                    "total", 0,
                    "anomalies", 0,
                    "percentage", 0.0,
                    "ratio", "0/0"
            );
        }

        long anomalies = data.stream()
                .filter(p -> p.getPrediction() == -1)
                .count();

        return Map.of(
                "total", data.size(),
                "anomalies", anomalies,
                "percentage", Math.round((anomalies * 10000.0) / data.size()) / 100.0,
                "ratio", anomalies + "/" + data.size()
        );
    }
}
